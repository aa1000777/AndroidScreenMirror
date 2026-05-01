package com.screenshare.sdk.touch;

import com.screenshare.sdk.network.UdpChannel;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 触摸解码器（接收端用）
 *
 * 负责接收 UDP 触摸数据包并反序列化
 * 注入到系统实现触摸反控
 */
public class TouchDecoder {

    private static final String TAG = "TouchDecoder";

    private static final int TOUCH_PACKET_SIZE = 18;

    private final UdpChannel channel;
    private Thread decodeThread;
    private AtomicBoolean isRunning = new AtomicBoolean(false);
    private EventListener eventListener;

    public interface EventListener {
        void onTouchEvent(long timestamp, int action, float x, float y);
        void onError(int errorCode, String message);
    }

    public TouchDecoder(UdpChannel channel) {
        this.channel = channel;
    }

    public void setEventListener(EventListener listener) {
        this.eventListener = listener;
    }

    /**
     * 启动解码
     */
    public void start() {
        if (isRunning.get()) {
            return;
        }

        isRunning.set(true);

        // 设置通道监听
        channel.setEventListener(new UdpChannel.EventListener() {
            @Override
            public void onReceived(byte[] data, int length, java.net.InetAddress from, int fromPort) {
                if (length >= TOUCH_PACKET_SIZE) {
                    parseAndDispatch(data);
                }
            }

            @Override
            public void onError(String message) {
                if (eventListener != null) {
                    eventListener.onError(0, message);
                }
            }
        });
    }

    private void parseAndDispatch(byte[] data) {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(data);

            long timestamp = buffer.getLong();
            int action = buffer.getShort();
            float x = buffer.getFloat();
            float y = buffer.getFloat();

            if (eventListener != null) {
                eventListener.onTouchEvent(timestamp, action, x, y);
            }

        } catch (Exception e) {
            if (eventListener != null) {
                eventListener.onError(0, "Failed to parse touch event: " + e.getMessage());
            }
        }
    }

    /**
     * 停止解码
     */
    public void stop() {
        isRunning.set(false);

        if (decodeThread != null) {
            try {
                decodeThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            decodeThread = null;
        }
    }

    /**
     * 释放资源
     */
    public void release() {
        stop();
    }
}