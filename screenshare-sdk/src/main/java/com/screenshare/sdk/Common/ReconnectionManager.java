package com.screenshare.sdk.Common;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 断线重连管理器（ReconnectionManager）
 *
 * 负责：
 * - 监听连接断开事件
 * - 自动触发重连（指数退避策略）
 * - 限制最大重试次数
 * - 状态机集成
 */
public class ReconnectionManager {

    private static final String TAG = "ReconnectionManager";

    // 最大重试次数
    private static final int MAX_RETRY_COUNT = 3;

    // 初始重试延迟（毫秒）
    private static final long INITIAL_RETRY_DELAY_MS = 1000;

    // 最大重试延迟（毫秒）
    private static final long MAX_RETRY_DELAY_MS = 10000;

    // 重连状态
    public enum ReconnectState {
        IDLE,           // 空闲，未在重连
        SCHEDULED,      // 已安排重连
        IN_PROGRESS,    // 正在重连
        SUCCESS,        // 重连成功
        FAILED          // 重连失败（已达最大次数）
    }

    /**
     * 重连事件回调
     */
    public interface ReconnectListener {
        void onReconnectStart(int attempt);
        void onReconnectAttempt(int attempt, long delayMs);
        void onReconnectSuccess();
        void onReconnectFailed(int attempt, int maxAttempts);
        void onStateChanged(ReconnectState state);
    }

    private ReconnectListener listener;

    // 关联的状态机
    private WeakReference<ConnectionStateMachine> stateMachineRef;

    // 重连状态
    private AtomicInteger currentState = new AtomicInteger(ReconnectState.IDLE.ordinal());

    // 重试计数
    private AtomicInteger retryCount = new AtomicInteger(0);

    // 当前延迟（用于指数退避）
    private long currentDelayMs = INITIAL_RETRY_DELAY_MS;

    // Handler 用于安排重连任务
    private Handler mainHandler;

    // 待执行的重连任务
    private Runnable pendingReconnectTask;

    public ReconnectionManager(ConnectionStateMachine stateMachine) {
        this.stateMachineRef = new WeakReference<>(stateMachine);
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public void setListener(ReconnectListener listener) {
        this.listener = listener;
    }

    /**
     * 获取当前重连状态
     */
    public ReconnectState getState() {
        return ReconnectState.values()[currentState.get()];
    }

    /**
     * 获取当前重试次数
     */
    public int getRetryCount() {
        return retryCount.get();
    }

    /**
     * 检查是否可以重连
     */
    public boolean canReconnect() {
        return retryCount.get() < MAX_RETRY_COUNT &&
               getState() != ReconnectState.IN_PROGRESS;
    }

    /**
     * 触发重连（从连接丢失状态）
     *
     * @param reason 重连原因
     */
    public synchronized void triggerReconnect(String reason) {
        if (!canReconnect()) {
            Log.w(TAG, "Cannot reconnect: max retries exceeded or already in progress");
            notifyState(ReconnectState.FAILED);
            return;
        }

        int attempt = retryCount.get() + 1;
        Log.i(TAG, "Triggering reconnect attempt " + attempt + "/" + MAX_RETRY_COUNT + ": " + reason);

        // 更新状态机到 CONNECTION_LOST
        ConnectionStateMachine sm = stateMachineRef.get();
        if (sm != null) {
            sm.transitionTo(ConnectionStateMachine.State.CONNECTION_LOST, reason);
        }

        // 通知开始重连
        if (listener != null) {
            listener.onReconnectStart(attempt);
        }

        // 安排重连任务
        scheduleReconnect(attempt);
    }

    /**
     * 安排重连任务
     */
    private void scheduleReconnect(int attempt) {
        // 取消之前的待执行任务
        if (pendingReconnectTask != null) {
            mainHandler.removeCallbacks(pendingReconnectTask);
        }

        // 计算延迟（指数退避）
        currentDelayMs = Math.min(INITIAL_RETRY_DELAY_MS * (1 << (attempt - 1)), MAX_RETRY_DELAY_MS);

        notifyState(ReconnectState.SCHEDULED);

        if (listener != null) {
            listener.onReconnectAttempt(attempt, currentDelayMs);
        }

        pendingReconnectTask = new Runnable() {
            @Override
            public void run() {
                executeReconnect(attempt);
            }
        };

        mainHandler.postDelayed(pendingReconnectTask, currentDelayMs);
    }

    /**
     * 执行重连
     */
    private void executeReconnect(int attempt) {
        notifyState(ReconnectState.IN_PROGRESS);

        ConnectionStateMachine sm = stateMachineRef.get();
        if (sm == null) {
            Log.e(TAG, "State machine reference lost");
            notifyState(ReconnectState.FAILED);
            return;
        }

        // 更新状态机到 CONNECTING
        sm.transitionTo(ConnectionStateMachine.State.CONNECTING, "Reconnect attempt " + attempt);

        Log.i(TAG, "Executing reconnect attempt " + attempt);
    }

    /**
     * 通知重连成功
     *
     * 调用此方法表示应用层的重连操作成功完成
     */
    public synchronized void onReconnectSuccess() {
        Log.i(TAG, "Reconnect succeeded after " + retryCount.get() + " attempts");

        // 重置重试计数
        retryCount.set(0);
        currentDelayMs = INITIAL_RETRY_DELAY_MS;

        notifyState(ReconnectState.SUCCESS);

        if (listener != null) {
            listener.onReconnectSuccess();
        }

        // 更新状态机到 STREAMING
        ConnectionStateMachine sm = stateMachineRef.get();
        if (sm != null) {
            sm.transitionTo(ConnectionStateMachine.State.STREAMING, "Reconnect success");
        }
    }

    /**
     * 通知重连失败
     *
     * 调用此方法表示应用层的重连操作失败
     * 会自动判断是否需要再次重试
     */
    public synchronized void onReconnectFailed(String reason) {
        retryCount.incrementAndGet();

        if (retryCount.get() >= MAX_RETRY_COUNT) {
            Log.w(TAG, "Reconnect failed: max retries exceeded");
            notifyState(ReconnectState.FAILED);

            if (listener != null) {
                listener.onReconnectFailed(retryCount.get(), MAX_RETRY_COUNT);
            }

            // 重置状态机到 IDLE
            ConnectionStateMachine sm = stateMachineRef.get();
            if (sm != null) {
                sm.transitionTo(ConnectionStateMachine.State.IDLE, "Max retries exceeded");
            }

            reset();
        } else {
            Log.w(TAG, "Reconnect attempt failed, will retry: " + reason);
            // 继续安排下一次重连
            triggerReconnect("Retry after failure: " + reason);
        }
    }

    /**
     * 重置管理器
     */
    public synchronized void reset() {
        if (pendingReconnectTask != null) {
            mainHandler.removeCallbacks(pendingReconnectTask);
            pendingReconnectTask = null;
        }

        retryCount.set(0);
        currentDelayMs = INITIAL_RETRY_DELAY_MS;
        notifyState(ReconnectState.IDLE);
    }

    /**
     * 取消重连（用户主动断开）
     */
    public synchronized void cancel() {
        Log.i(TAG, "Reconnection cancelled by user");

        if (pendingReconnectTask != null) {
            mainHandler.removeCallbacks(pendingReconnectTask);
            pendingReconnectTask = null;
        }

        reset();
    }

    private void notifyState(ReconnectState state) {
        currentState.set(state.ordinal());
        if (listener != null) {
            listener.onStateChanged(state);
        }
    }

    @Override
    public String toString() {
        return "ReconnectionManager{" +
            "state=" + getState() +
            ", retryCount=" + retryCount.get() +
            "/" + MAX_RETRY_COUNT +
            ", delayMs=" + currentDelayMs +
            '}';
    }
}