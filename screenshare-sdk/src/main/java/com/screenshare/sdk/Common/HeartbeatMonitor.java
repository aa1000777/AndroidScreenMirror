package com.screenshare.sdk.Common;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 心跳监控器（HeartbeatMonitor）
 *
 * 负责：
 * - 定期发送心跳 ping
 * - 检测心跳 pong 响应
 * - 连接健康检查
 * - 超时触发断线重连
 */
public class HeartbeatMonitor {

    private static final String TAG = "HeartbeatMonitor";

    // 心跳间隔（毫秒）
    private static final long DEFAULT_HEARTBEAT_INTERVAL_MS = 2000;

    // 心跳超时（毫秒），超过此时间未收到 pong 认为连接有问题
    private static final long DEFAULT_HEARTBEAT_TIMEOUT_MS = 5000;

    // 最大连续超时次数，超过此值认为连接断开
    private static final int MAX_CONSECUTIVE_TIMEOUTS = 3;

    /**
     * 心跳事件回调
     */
    public interface HeartbeatListener {
        void onPing(long timestamp);                      // 发送 ping 时回调
        void onPong(long roundTripTimeMs);               // 收到 pong 时回调
        void onTimeout(int missedCount, int maxAllowed); // 超时时回调
        void onConnectionHealth(boolean isHealthy);      // 连接健康状态变化时回调
        void onHeartbeatStopped();
    }

    private HeartbeatListener listener;

    // 关联的 ReconnectionManager
    private WeakReference<ReconnectionManager> reconnectManagerRef;

    // 心跳间隔和超时
    private long heartbeatIntervalMs = DEFAULT_HEARTBEAT_INTERVAL_MS;
    private long heartbeatTimeoutMs = DEFAULT_HEARTBEAT_TIMEOUT_MS;

    // 运行状态
    private AtomicBoolean isRunning = new AtomicBoolean(false);

    // 心跳计数器
    private AtomicLong pingCount = new AtomicLong(0);
    private AtomicLong pongCount = new AtomicLong(0);
    private AtomicLong consecutiveTimeouts = new AtomicLong(0);

    // 最后一次收到 pong 的时间
    private volatile long lastPongTimeMs = 0;

    // 最后一次 ping 的时间戳
    private volatile long lastPingTimestamp = 0;

    // 计算的往返延迟
    private volatile long currentRoundTripTimeMs = 0;

    // Handler 和任务
    private Handler handler;
    private Runnable heartbeatTask;
    private Runnable timeoutTask;

    // 是否已通知不健康状态
    private boolean hasNotifiedUnhealthy = false;

    public HeartbeatMonitor() {
        this.handler = new Handler(Looper.getMainLooper());
        this.heartbeatTask = new Runnable() {
            @Override
            public void run() {
                HeartbeatMonitor.this.sendPing();
            }
        };
        this.timeoutTask = new Runnable() {
            @Override
            public void run() {
                HeartbeatMonitor.this.checkTimeout();
            }
        };
    }

    public HeartbeatMonitor(ReconnectionManager reconnectManager) {
        this();
        this.reconnectManagerRef = new WeakReference<>(reconnectManager);
    }

    public void setListener(HeartbeatListener listener) {
        this.listener = listener;
    }

    /**
     * 设置心跳间隔
     */
    public void setHeartbeatInterval(long intervalMs) {
        this.heartbeatIntervalMs = intervalMs;
    }

    /**
     * 设置心跳超时
     */
    public void setHeartbeatTimeout(long timeoutMs) {
        this.heartbeatTimeoutMs = timeoutMs;
    }

    /**
     * 启动心跳监控
     */
    public void start() {
        if (isRunning.getAndSet(true)) {
            Log.w(TAG, "HeartbeatMonitor already running");
            return;
        }

        resetStats();
        scheduleNextPing();

        Log.i(TAG, "HeartbeatMonitor started (interval=" + heartbeatIntervalMs + "ms, timeout=" + heartbeatTimeoutMs + "ms)");
    }

    /**
     * 停止心跳监控
     */
    public synchronized void stop() {
        if (!isRunning.getAndSet(false)) {
            return;
        }

        handler.removeCallbacks(heartbeatTask);
        handler.removeCallbacks(timeoutTask);

        if (listener != null) {
            listener.onHeartbeatStopped();
        }

        Log.i(TAG, "HeartbeatMonitor stopped");
    }

    /**
     * 重置统计数据
     */
    private void resetStats() {
        pingCount.set(0);
        pongCount.set(0);
        consecutiveTimeouts.set(0);
        lastPongTimeMs = 0;
        lastPingTimestamp = 0;
        currentRoundTripTimeMs = 0;
        hasNotifiedUnhealthy = false;
    }

    /**
     * 安排下一次 ping
     */
    private void scheduleNextPing() {
        if (!isRunning.get()) {
            return;
        }

        handler.removeCallbacks(heartbeatTask);
        handler.postDelayed(heartbeatTask, heartbeatIntervalMs);
    }

    /**
     * 发送 ping
     */
    private void sendPing() {
        if (!isRunning.get()) {
            return;
        }

        long timestamp = SystemClock.elapsedRealtime();
        lastPingTimestamp = timestamp;

        pingCount.incrementAndGet();

        if (listener != null) {
            listener.onPing(timestamp);
        }

        // 安排超时检测
        handler.removeCallbacks(timeoutTask);
        handler.postDelayed(timeoutTask, heartbeatTimeoutMs);

        // 安排下一次 ping
        scheduleNextPing();
    }

    /**
     * 收到 pong 响应
     *
     * @param timestamp 对应 ping 的时间戳（用于计算 RTT）
     */
    public synchronized void onPong(long timestamp) {
        if (!isRunning.get()) {
            return;
        }

        long now = SystemClock.elapsedRealtime();
        long rtt = now - timestamp;

        // 更新 RTT
        currentRoundTripTimeMs = rtt;
        lastPongTimeMs = now;
        pongCount.incrementAndGet();
        consecutiveTimeouts.set(0);

        if (listener != null) {
            listener.onPong(rtt);
        }

        // 如果之前通知过不健康，现在恢复健康
        if (hasNotifiedUnhealthy) {
            hasNotifiedUnhealthy = false;
            if (listener != null) {
                listener.onConnectionHealth(true);
            }
        }
    }

    /**
     * 检查超时
     */
    private void checkTimeout() {
        if (!isRunning.get()) {
            return;
        }

        long timeSinceLastPong = SystemClock.elapsedRealtime() - lastPongTimeMs;

        // 如果还在等待 pong（lastPongTimeMs 为 0 表示还没收到过）
        if (lastPongTimeMs == 0 || timeSinceLastPong > heartbeatTimeoutMs) {
            int timeouts = (int) consecutiveTimeouts.incrementAndGet();

            Log.w(TAG, "Heartbeat timeout #" + timeouts + " (sinceLastPong=" + timeSinceLastPong + "ms)");

            if (listener != null) {
                listener.onTimeout(timeouts, MAX_CONSECUTIVE_TIMEOUTS);
            }

            // 检查是否超过最大超时次数
            if (timeouts >= MAX_CONSECUTIVE_TIMEOUTS) {
                handleConnectionLost();
            } else {
                // 还没超过阈值，通知不健康但继续监控
                if (!hasNotifiedUnhealthy) {
                    hasNotifiedUnhealthy = true;
                    if (listener != null) {
                        listener.onConnectionHealth(false);
                    }
                }
            }
        }
    }

    /**
     * 处理连接丢失
     */
    private void handleConnectionLost() {
        Log.e(TAG, "Connection lost: max consecutive timeouts exceeded");

        stop();

        // 触发重连
        ReconnectionManager rm = reconnectManagerRef != null ? reconnectManagerRef.get() : null;
        if (rm != null) {
            rm.onReconnectFailed("Heartbeat timeout");
        }
    }

    /**
     * 获取当前 RTT
     */
    public long getCurrentRoundTripTimeMs() {
        return currentRoundTripTimeMs;
    }

    /**
     * 获取 ping 计数
     */
    public long getPingCount() {
        return pingCount.get();
    }

    /**
     * 获取 pong 计数
     */
    public long getPongCount() {
        return pongCount.get();
    }

    /**
     * 获取丢包率（估算）
     */
    public float getLossRate() {
        long pings = pingCount.get();
        long pongs = pongCount.get();
        if (pings == 0) {
            return 0f;
        }
        return (float) (pings - pongs) / pings;
    }

    /**
     * 是否正在运行
     */
    public boolean isRunning() {
        return isRunning.get();
    }

    @Override
    public String toString() {
        return "HeartbeatMonitor{" +
            "running=" + isRunning.get() +
            ", pingCount=" + pingCount.get() +
            ", pongCount=" + pongCount.get() +
            ", rttMs=" + currentRoundTripTimeMs +
            ", lossRate=" + String.format("%.2f", getLossRate() * 100) + "%" +
            '}';
    }
}