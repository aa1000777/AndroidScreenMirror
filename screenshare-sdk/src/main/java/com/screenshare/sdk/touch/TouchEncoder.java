package com.screenshare.sdk.touch;

import com.screenshare.sdk.network.UdpChannel;
import com.screenshare.sdk.Common.AtomicBuffer;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 触摸编码器（发送端用）
 *
 * 负责将触摸事件序列化为二进制并通过 UDP 发送
 * 触摸事件走独立通道，不走视频帧队列
 */
public class TouchEncoder {

    private static final String TAG = "TouchEncoder";

    // 触摸事件格式（自定义二进制协议）：
    // | timestamp(8) | action(2) | x(4) | y(4) | = 18 bytes
    private static final int TOUCH_PACKET_SIZE = 18;

    private final UdpChannel channel;
    private final AtomicBuffer buffer;
    private Thread sendThread;
    private AtomicBoolean isRunning = new AtomicBoolean(false);

    // 待发送的触摸事件队列（绕过 PriorityQueue）
    private java.util.concurrent.BlockingQueue<TouchEvent> eventQueue =
        new java.util.concurrent.LinkedBlockingQueue<>();

    public TouchEncoder(UdpChannel channel, AtomicBuffer buffer) {
        this.channel = channel;
        this.buffer = buffer;
    }

    /**
     * 编码并发送触摸事件
     */
    public void encodeAndSend(long timestamp, int action, float x, float y) {
        TouchEvent event = new TouchEvent(timestamp, action, x, y);
        if (!eventQueue.offer(event)) {
            // 队列满，丢弃旧事件（保持低延迟）
            eventQueue.poll();
            eventQueue.offer(event);
        }
    }

    /**
     * 启动发送线程
     */
    public void start() {
        if (isRunning.get()) {
            return;
        }

        isRunning.set(true);
        sendThread = new Thread(this::sendLoop, "TouchEncoder");
        sendThread.start();
    }

    private void sendLoop() {
        ByteBuffer packet = ByteBuffer.allocate(TOUCH_PACKET_SIZE);

        while (isRunning.get()) {
            try {
                TouchEvent event = eventQueue.poll(10, java.util.concurrent.TimeUnit.MILLISECONDS);
                if (event == null) {
                    continue;
                }

                // 序列化为二进制
                packet.clear();
                packet.putLong(event.timestamp);
                packet.putShort((short) event.action);
                packet.putFloat(event.x);
                packet.putFloat(event.y);

                byte[] data = packet.array();
                channel.send(data, 0, TOUCH_PACKET_SIZE);

            } catch (Exception e) {
                if (!isRunning.get()) {
                    break;
                }
            }
        }
    }

    /**
     * 停止编码器
     */
    public void stop() {
        isRunning.set(false);

        if (sendThread != null) {
            try {
                sendThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            sendThread = null;
        }

        eventQueue.clear();
    }

    /**
     * 释放资源
     */
    public void release() {
        stop();
    }

    private static class TouchEvent {
        final long timestamp;
        final int action;
        final float x;
        final float y;

        TouchEvent(long timestamp, int action, float x, float y) {
            this.timestamp = timestamp;
            this.action = action;
            this.x = x;
            this.y = y;
        }
    }
}