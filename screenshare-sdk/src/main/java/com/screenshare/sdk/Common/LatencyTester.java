package com.screenshare.sdk.Common;

import android.os.SystemClock;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 延迟测试器（LatencyTester）
 *
 * 负责：
 * - 测量端到端延迟（ping-pong 往返时间）
 * - 记录统计数据（平均值、最小值、最大值、p95）
 * - 定期输出测试报告
 *
 * 使用方式：
 * 1. 在 Sender 端调用 recordSend()
 * 2. 在 Receiver 端收到后调用 recordReceive()
 * 3. 查看 getStats() 获取统计数据
 */
public class LatencyTester {

    private static final String TAG = "LatencyTester";

    // 最大记录数
    private static final int MAX_RECORDS = 1000;

    // 默认测试间隔（毫秒）
    private static final long DEFAULT_TEST_INTERVAL_MS = 1000;

    /**
     * 单次延迟记录
     */
    public static class LatencyRecord {
        public final long timestamp;
        public final long roundTripTimeMs;
        public final boolean isValid;

        public LatencyRecord(long timestamp, long rttMs, boolean valid) {
            this.timestamp = timestamp;
            this.roundTripTimeMs = rttMs;
            this.isValid = valid;
        }
    }

    /**
     * 延迟统计
     */
    public static class LatencyStats {
        public final long count;
        public final long minMs;
        public final long maxMs;
        public final long avgMs;
        public final long p95Ms;
        public final long p99Ms;
        public final float lossRate;
        public final long totalRecords;

        public LatencyStats(List<LatencyRecord> records) {
            if (records.isEmpty()) {
                count = 0;
                minMs = 0;
                maxMs = 0;
                avgMs = 0;
                p95Ms = 0;
                p99Ms = 0;
                lossRate = 0f;
                totalRecords = 0;
                return;
            }

            List<Long> sortedTimes = new ArrayList<>();
            long validCount = 0;
            long sum = 0;
            long min = Long.MAX_VALUE;
            long max = Long.MIN_VALUE;

            for (LatencyRecord record : records) {
                if (record.isValid) {
                    sortedTimes.add(record.roundTripTimeMs);
                    sum += record.roundTripTimeMs;
                    validCount++;
                    if (record.roundTripTimeMs < min) min = record.roundTripTimeMs;
                    if (record.roundTripTimeMs > max) max = record.roundTripTimeMs;
                }
            }

            this.totalRecords = records.size();
            this.count = validCount;

            if (validCount == 0) {
                minMs = 0;
                maxMs = 0;
                avgMs = 0;
                p95Ms = 0;
                p99Ms = 0;
                lossRate = 1f;
            } else {
                this.minMs = min;
                this.maxMs = max;
                this.avgMs = sum / validCount;
                this.lossRate = (float) (totalRecords - validCount) / totalRecords;

                // 计算百分位数
                java.util.Collections.sort(sortedTimes);
                int p95Index = (int) (validCount * 0.95);
                int p99Index = (int) (validCount * 0.99);
                this.p95Ms = sortedTimes.get(Math.min(p95Index, sortedTimes.size() - 1));
                this.p99Ms = sortedTimes.get(Math.min(p99Index, sortedTimes.size() - 1));
            }
        }

        @Override
        public String toString() {
            return String.format(
                "LatencyStats{count=%d, min=%dms, max=%dms, avg=%dms, p95=%dms, p99=%dms, loss=%.1f%%}",
                count, minMs, maxMs, avgMs, p95Ms, p99Ms, lossRate * 100
            );
        }

        /**
         * 获取人类可读的统计报告
         */
        public String getReport() {
            StringBuilder sb = new StringBuilder();
            sb.append("=== Latency Test Report ===\n");
            sb.append("Total records: ").append(totalRecords).append("\n");
            sb.append("Valid records: ").append(count).append("\n");
            sb.append("Min RTT: ").append(minMs).append("ms\n");
            sb.append("Max RTT: ").append(maxMs).append("ms\n");
            sb.append("Avg RTT: ").append(avgMs).append("ms\n");
            sb.append("P95 RTT: ").append(p95Ms).append("ms\n");
            sb.append("P99 RTT: ").append(p99Ms).append("ms\n");
            sb.append("Loss rate: ").append(String.format("%.1f%%", lossRate * 100)).append("\n");
            sb.append("=========================");
            return sb.toString();
        }
    }

    /**
     * 测试回调
     */
    public interface TestCallback {
        void onTestResult(long roundTripTimeMs);
        void onTestTimeout();
    }

    private AtomicBoolean isRunning = new AtomicBoolean(false);

    // 记录列表
    private CopyOnWriteArrayList<LatencyRecord> records = new CopyOnWriteArrayList<>();

    // 当前测试的序列号
    private AtomicLong sequenceNumber = new AtomicLong(0);

    // 挂起的测试（用于计算 RTT）
    private volatile long pendingSendTimestamp = 0;

    // 测试回调
    private TestCallback callback;

    /**
     * 开始自动测试
     */
    public void startAutoTest(long intervalMs) {
        if (isRunning.getAndSet(true)) {
            return;
        }

        new Thread(() -> {
            while (isRunning.get()) {
                // 发送测试
                long seq = sendTestPing();

                // 等待接收（最多 5 秒超时）
                long startTime = SystemClock.elapsedRealtime();
                while (isRunning.get() && pendingSendTimestamp != 0) {
                    if (SystemClock.elapsedRealtime() - startTime > 5000) {
                        // 超时
                        onTestTimeout(seq);
                        break;
                    }
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }

                // 等待间隔
                try {
                    Thread.sleep(intervalMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "LatencyTester-AutoTest").start();

        Log.i(TAG, "LatencyTester auto-test started (interval=" + intervalMs + "ms)");
    }

    /**
     * 停止自动测试
     */
    public void stopAutoTest() {
        isRunning.set(false);
    }

    /**
     * 发送测试 ping（Sender 端调用）
     *
     * @return 序列号（用于关联 pong）
     */
    public long sendTestPing() {
        long seq = sequenceNumber.incrementAndGet();
        pendingSendTimestamp = SystemClock.elapsedRealtime();

        // TODO: 通过 UDP 发送测试包到对面
        // 这里只是记录本地时间，实际需要配合网络传输

        Log.d(TAG, "Test ping sent: seq=" + seq);
        return seq;
    }

    /**
     * 收到测试 pong（Receiver 端调用，然后 Sender 端收到响应）
     *
     * @param timestamp 对应 ping 发送时的时间戳
     */
    public void recordPong(long timestamp) {
        long now = SystemClock.elapsedRealtime();
        long rtt = now - timestamp;

        LatencyRecord record = new LatencyRecord(now, rtt, true);
        records.add(record);

        // 清理超出范围的记录
        while (records.size() > MAX_RECORDS) {
            records.remove(0);
        }

        if (callback != null) {
            callback.onTestResult(rtt);
        }

        Log.d(TAG, "Pong received: rtt=" + rtt + "ms");
    }

    /**
     * 测试超时
     */
    private void onTestTimeout(long sequence) {
        long now = SystemClock.elapsedRealtime();

        LatencyRecord record = new LatencyRecord(now, 0, false);
        records.add(record);

        // 清理超出范围的记录
        while (records.size() > MAX_RECORDS) {
            records.remove(0);
        }

        if (callback != null) {
            callback.onTestTimeout();
        }

        Log.w(TAG, "Test timeout: seq=" + sequence);
    }

    /**
     * 手动记录发送时间
     */
    public long recordSend() {
        return SystemClock.elapsedRealtime();
    }

    /**
     * 手动记录接收时间（配合 recordSend 使用）
     *
     * @param sendTimestamp recordSend() 返回的时间戳
     */
    public void recordReceive(long sendTimestamp) {
        long now = SystemClock.elapsedRealtime();
        long rtt = now - sendTimestamp;

        LatencyRecord record = new LatencyRecord(now, rtt, true);
        records.add(record);

        // 清理超出范围的记录
        while (records.size() > MAX_RECORDS) {
            records.remove(0);
        }

        if (callback != null) {
            callback.onTestResult(rtt);
        }
    }

    /**
     * 获取统计数据
     */
    public LatencyStats getStats() {
        return new LatencyStats(new ArrayList<>(records));
    }

    /**
     * 获取所有记录
     */
    public List<LatencyRecord> getRecords() {
        return new ArrayList<>(records);
    }

    /**
     * 清空记录
     */
    public void clearRecords() {
        records.clear();
    }

    /**
     * 设置测试回调
     */
    public void setCallback(TestCallback callback) {
        this.callback = callback;
    }

    /**
     * 是否正在运行
     */
    public boolean isRunning() {
        return isRunning.get();
    }

    /**
     * 获取记录数量
     */
    public int getRecordCount() {
        return records.size();
    }

    @Override
    public String toString() {
        LatencyStats stats = getStats();
        return "LatencyTester{" +
            "records=" + records.size() +
            ", running=" + isRunning.get() +
            ", stats=" + stats +
            '}';
    }
}