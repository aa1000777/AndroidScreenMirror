package com.screenshare.sdk;

import android.content.Context;
import com.screenshare.sdk.capture.ScreenCapturer;
import com.screenshare.sdk.codec.VideoEncoder;
import com.screenshare.sdk.network.RtpSession;
import com.screenshare.sdk.network.UdpChannel;
import com.screenshare.sdk.touch.TouchEncoder;
import com.screenshare.sdk.Common.AtomicBuffer;
import com.screenshare.sdk.Common.ErrorCode;
import com.screenshare.sdk.Common.SenderState;

/**
 * 发送端（Sender）
 *
 * 负责：屏幕采集 → 编码 → RTP 打包 → UDP 发送
 *       触摸事件 → 编码 → 独立 UDP 通道发送
 */
public class Sender {

    private static final String TAG = "ScreenshareSDK.Sender";

    private final Context context;
    private final SenderConfig config;

    private ScreenCapturer screenCapturer;
    private VideoEncoder videoEncoder;
    private RtpSession rtpSession;
    private UdpChannel videoChannel;
    private UdpChannel touchChannel;
    private TouchEncoder touchEncoder;

    private SenderState state = SenderState.IDLE;
    private EventListener eventListener;

    public interface EventListener {
        void onStateChanged(SenderState state);
        void onError(int errorCode, String message);
        void onConnected(String peerAddress);
        void onDisconnected();
        void onFrameSent(long timestamp, int size);
    }

    public Sender(Context context, SenderConfig config) {
        this.context = context;
        this.config = config;
    }

    public void setEventListener(EventListener listener) {
        this.eventListener = listener;
    }

    public Context getContext() {
        return context;
    }

    public SenderConfig getConfig() {
        return config;
    }

    public SenderState getState() {
        return state;
    }

    private void setState(SenderState newState) {
        if (state != newState) {
            state = newState;
            if (eventListener != null) {
                eventListener.onStateChanged(newState);
            }
        }
    }

    /**
     * 开始屏幕采集和编码
     */
    public synchronized void startCapture() {
        if (state != SenderState.IDLE) {
            notifyError(ErrorCode.ERR_INVALID_STATE, "Cannot start capture in state: " + state);
            return;
        }

        try {
            setState(SenderState.CAPTURING);

            // 1. 分配原子化缓冲区（减少 GC）
            AtomicBuffer videoBuffer = new AtomicBuffer(config.sendBufferSize);
            AtomicBuffer touchBuffer = new AtomicBuffer(4096);

            // 2. 初始化视频编码器
            videoEncoder = new VideoEncoder(context, config);
            videoEncoder.start();

            // 3. 初始化视频传输通道
            videoChannel = new UdpChannel(config.videoPort);
            videoChannel.open();
            rtpSession = new RtpSession(videoEncoder, videoChannel, videoBuffer);
            rtpSession.start();

            // 4. 初始化触摸通道（独立，低延迟）
            touchChannel = new UdpChannel(config.touchPort);
            touchChannel.open();
            touchEncoder = new TouchEncoder(touchChannel, touchBuffer);
            touchEncoder.start();

            // 5. 初始化屏幕采集
            screenCapturer = new ScreenCapturer(context, config, videoEncoder);
            screenCapturer.setEventListener(new ScreenCapturer.EventListener() {
                @Override
                public void onFrameAvailable(long timestamp, int size) {
                    if (eventListener != null) {
                        eventListener.onFrameSent(timestamp, size);
                    }
                }

                @Override
                public void onError(int errorCode, String message) {
                    notifyError(errorCode, message);
                }
            });
            screenCapturer.start();

            setState(SenderState.DISCOVERING);

        } catch (Exception e) {
            notifyError(ErrorCode.ERR_CAPTURE_START_FAILED, "Failed to start capture: " + e.getMessage());
            stopCapture();
        }
    }

    /**
     * 连接到指定的对等设备
     */
    public synchronized void connect(String peerAddress) {
        if (state != SenderState.DISCOVERING && state != SenderState.CONNECTION_LOST) {
            notifyError(ErrorCode.ERR_INVALID_STATE, "Cannot connect in state: " + state);
            return;
        }

        try {
            setState(SenderState.CONNECTING);

            // 更新视频通道目标地址
            videoChannel.setPeerAddress(peerAddress);
            touchChannel.setPeerAddress(peerAddress);

            // 等待连接建立
            int timeout = config.connectTimeout;
            long startTime = System.currentTimeMillis();
            while (!videoChannel.isConnected() && System.currentTimeMillis() - startTime < timeout) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            if (videoChannel.isConnected()) {
                setState(SenderState.STREAMING);
                if (eventListener != null) {
                    eventListener.onConnected(peerAddress);
                }
            } else {
                notifyError(ErrorCode.ERR_CONNECTION_FAILED, "Connection timeout");
                setState(SenderState.DISCOVERING);
            }

        } catch (Exception e) {
            notifyError(ErrorCode.ERR_CONNECTION_FAILED, "Connect failed: " + e.getMessage());
            setState(SenderState.DISCOVERING);
        }
    }

    /**
     * 发送触摸事件
     */
    public void sendTouchEvent(long timestamp, int action, float x, float y) {
        if (state != SenderState.STREAMING) {
            return;
        }
        if (touchEncoder != null) {
            touchEncoder.encodeAndSend(timestamp, action, x, y);
        }
    }

    /**
     * 断开连接
     */
    public synchronized void disconnect() {
        if (state == SenderState.IDLE) {
            return;
        }

        videoChannel.disconnect();
        touchChannel.disconnect();
        setState(SenderState.DISCOVERING);

        if (eventListener != null) {
            eventListener.onDisconnected();
        }
    }

    /**
     * 停止屏幕采集
     */
    public synchronized void stopCapture() {
        setState(SenderState.IDLE);

        if (screenCapturer != null) {
            screenCapturer.stop();
            screenCapturer = null;
        }
        if (videoEncoder != null) {
            videoEncoder.stop();
            videoEncoder = null;
        }
        if (rtpSession != null) {
            rtpSession.stop();
            rtpSession = null;
        }
        if (videoChannel != null) {
            videoChannel.close();
            videoChannel = null;
        }
        if (touchChannel != null) {
            touchChannel.close();
            touchChannel = null;
        }
        if (touchEncoder != null) {
            touchEncoder.stop();
            touchEncoder = null;
        }
    }

    /**
     * 释放所有资源
     */
    public synchronized void release() {
        stopCapture();
    }

    private void notifyError(int errorCode, String message) {
        if (eventListener != null) {
            eventListener.onError(errorCode, message);
        }
    }
}