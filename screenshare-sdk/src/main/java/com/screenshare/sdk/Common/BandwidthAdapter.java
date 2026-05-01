package com.screenshare.sdk.Common;

import android.util.Log;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 带宽自适应（BandwidthAdapter）
 *
 * 负责：
 * - 监测网络带宽和延迟
 * - 动态调整编码参数（码率、帧率、分辨率）
 * - 避免网络拥塞
 *
 * 使用方式：
 * 1. 定期调用 updateStats() 更新网络统计
 * 2. 监听 onQualityChanged() 回调获取参数变化
 * 3. 应用获取到的配置到编码器
 */
public class BandwidthAdapter {

    private static final String TAG = "BandwidthAdapter";

    // 统计窗口大小（采样次数）
    private static final int STATS_WINDOW_SIZE = 10;

    // 带宽阈值（bps）
    private static final long HIGH_BANDWIDTH_THRESHOLD = 10_000_000;  // 10Mbps
    private static final long MEDIUM_BANDWIDTH_THRESHOLD = 4_000_000;  // 4Mbps
    private static final long LOW_BANDWIDTH_THRESHOLD = 2_000_000;     // 2Mbps

    // 丢包率阈值
    private static final double HIGH_LOSS_RATE = 0.05;   // 5%
    private static final double CRITICAL_LOSS_RATE = 0.10; // 10%

    /**
     * 质量级别
     */
    public enum QualityLevel {
        HIGH,      // 1080p 60fps 8Mbps
        MEDIUM,    // 720p 30fps 4Mbps
        LOW,       // 480p 30fps 2Mbps
        MINIMAL    // 360p 15fps 1Mbps
    }

    /**
     * 编码配置
     */
    public static class EncoderConfig {
        public int width;
        public int height;
        public int fps;
        public int bitrate;

        public EncoderConfig(int width, int height, int fps, int bitrate) {
            this.width = width;
            this.height = height;
            this.fps = fps;
            this.bitrate = bitrate;
        }

        @Override
        public String toString() {
            return String.format("EncoderConfig{%dx%d@%dfps %dbps}",
                width, height, fps, bitrate);
        }
    }

    /**
     * 回调接口
     */
    public interface QualityChangeListener {
        void onQualityChanged(QualityLevel level, EncoderConfig config);
        void onBandwidthWarning(String message);
    }

    private QualityChangeListener listener;

    // 当前质量级别
    private AtomicInteger currentLevel = new AtomicInteger(QualityLevel.HIGH.ordinal());

    // 网络统计
    private AtomicLong estimatedBandwidth = new AtomicLong(HIGH_BANDWIDTH_THRESHOLD);
    private AtomicInteger currentRtt = new AtomicInteger(0);
    private AtomicLong totalBytesSent = new AtomicLong(0);
    private AtomicLong totalBytesReceived = new AtomicLong(0);

    // 最近采样（用于滑动平均）
    private long[] recentBandwidthSamples = new long[STATS_WINDOW_SIZE];
    private int sampleIndex = 0;
    private int sampleCount = 0;

    // 是否启用自适应
    private boolean isEnabled = true;

    // 最小/最大配置
    private EncoderConfig maxConfig = new EncoderConfig(1920, 1080, 60, 8_000_000);
    private EncoderConfig minConfig = new EncoderConfig(640, 360, 15, 1_000_000);

    public BandwidthAdapter() {
    }

    public void setListener(QualityChangeListener listener) {
        this.listener = listener;
    }

    /**
     * 设置配置范围
     */
    public void setConfigRange(EncoderConfig max, EncoderConfig min) {
        this.maxConfig = max;
        this.minConfig = min;
    }

    /**
     * 启用/禁用自适应
     */
    public void setEnabled(boolean enabled) {
        this.isEnabled = enabled;
        Log.i(TAG, "BandwidthAdapter enabled=" + enabled);
    }

    /**
     * 更新网络统计（每次发送/接收后调用）
     *
     * @param bytesSent 本次发送字节数
     * @param bytesReceived 本次接收字节数（用于计算往返时间）
     * @param rttMs 往返延迟（毫秒）
     */
    public synchronized void updateStats(long bytesSent, long bytesReceived, int rttMs) {
        if (!isEnabled) {
            return;
        }

        // 更新计数
        totalBytesSent.addAndGet(bytesSent);
        totalBytesReceived.addAndGet(bytesReceived);
        currentRtt.set(rttMs);

        // 估算带宽（基于发送量和延迟）
        if (rttMs > 0 && bytesSent > 0) {
            long estimatedBps = (bytesSent * 1000L * 8) / rttMs;
            addBandwidthSample(estimatedBps);
        }
    }

    private void addBandwidthSample(long bandwidth) {
        recentBandwidthSamples[sampleIndex] = bandwidth;
        sampleIndex = (sampleIndex + 1) % STATS_WINDOW_SIZE;
        if (sampleCount < STATS_WINDOW_SIZE) {
            sampleCount++;
        }

        // 计算滑动平均
        long sum = 0;
        for (int i = 0; i < sampleCount; i++) {
            sum += recentBandwidthSamples[i];
        }
        estimatedBandwidth.set(sum / sampleCount);
    }

    /**
     * 获取丢包率
     */
    public double getLossRate() {
        // 需要外部提供，这里返回 0 作为默认值
        return 0.0;
    }

    /**
     * 检查是否需要降级
     */
    public synchronized boolean shouldDegrade() {
        // 检查丢包率
        double lossRate = getLossRate();
        if (lossRate > HIGH_LOSS_RATE) {
            Log.w(TAG, "High loss rate: " + (lossRate * 100) + "%");
            return true;
        }

        // 检查 RTT
        if (currentRtt.get() > 100) {
            Log.w(TAG, "High RTT: " + currentRtt.get() + "ms");
            return true;
        }

        // 检查带宽
        if (estimatedBandwidth.get() < LOW_BANDWIDTH_THRESHOLD) {
            return true;
        }

        return false;
    }

    /**
     * 检查是否可以升级
     */
    public synchronized boolean canUpgrade() {
        // 检查带宽是否充裕
        if (estimatedBandwidth.get() > HIGH_BANDWIDTH_THRESHOLD) {
            // 检查丢包率和延迟是否良好
            if (currentRtt.get() < 50 && getLossRate() < 0.01) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取当前质量级别
     */
    public QualityLevel getCurrentLevel() {
        return QualityLevel.values()[currentLevel.get()];
    }

    /**
     * 获取当前编码配置
     */
    public EncoderConfig getCurrentConfig() {
        return getConfigForLevel(getCurrentLevel());
    }

    /**
     * 获取指定质量级别的配置
     */
    private EncoderConfig getConfigForLevel(QualityLevel level) {
        switch (level) {
            case HIGH:
                return new EncoderConfig(1920, 1080, 60, 8_000_000);
            case MEDIUM:
                return new EncoderConfig(1280, 720, 30, 4_000_000);
            case LOW:
                return new EncoderConfig(854, 480, 30, 2_000_000);
            case MINIMAL:
                return new EncoderConfig(640, 360, 15, 1_000_000);
            default:
                return new EncoderConfig(1920, 1080, 60, 8_000_000);
        }
    }

    /**
     * 降级质量
     */
    public synchronized void degrade() {
        int current = currentLevel.get();
        if (current < QualityLevel.values().length - 1) {
            int newLevel = current + 1;
            currentLevel.set(newLevel);

            QualityLevel level = QualityLevel.values()[newLevel];
            EncoderConfig config = getConfigForLevel(level);

            Log.i(TAG, "Quality degraded to " + level + ": " + config);

            if (listener != null) {
                listener.onQualityChanged(level, config);
            }
        } else {
            if (listener != null) {
                listener.onBandwidthWarning("Already at minimum quality");
            }
        }
    }

    /**
     * 升级质量
     */
    public synchronized void upgrade() {
        int current = currentLevel.get();
        if (current > 0) {
            int newLevel = current - 1;
            currentLevel.set(newLevel);

            QualityLevel level = QualityLevel.values()[newLevel];
            EncoderConfig config = getConfigForLevel(level);

            Log.i(TAG, "Quality upgraded to " + level + ": " + config);

            if (listener != null) {
                listener.onQualityChanged(level, config);
            }
        }
    }

    /**
     * 重置到高质量
     */
    public synchronized void reset() {
        currentLevel.set(QualityLevel.HIGH.ordinal());

        QualityLevel level = QualityLevel.HIGH;
        EncoderConfig config = getConfigForLevel(level);

        Log.i(TAG, "Quality reset to HIGH: " + config);

        if (listener != null) {
            listener.onQualityChanged(level, config);
        }
    }

    /**
     * 获取估算带宽（bps）
     */
    public long getEstimatedBandwidth() {
        return estimatedBandwidth.get();
    }

    /**
     * 获取当前 RTT（毫秒）
     */
    public int getCurrentRtt() {
        return currentRtt.get();
    }

    @Override
    public String toString() {
        return "BandwidthAdapter{" +
            "level=" + getCurrentLevel() +
            ", bandwidth=" + (estimatedBandwidth.get() / 1_000_000) + "Mbps" +
            ", rtt=" + currentRtt.get() + "ms" +
            ", loss=" + String.format("%.1f%%", getLossRate() * 100) +
            '}';
    }
}