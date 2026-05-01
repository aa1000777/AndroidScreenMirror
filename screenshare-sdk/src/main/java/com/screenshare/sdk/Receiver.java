package com.screenshare.sdk;

import android.content.Context;
import com.screenshare.sdk.codec.VideoDecoder;
import com.screenshare.sdk.codec.AudioDecoder;
import com.screenshare.sdk.network.RtpSession;
import com.screenshare.sdk.network.UdpChannel;
import com.screenshare.sdk.touch.TouchDecoder;
import com.screenshare.sdk.Common.AtomicBuffer;
import com.screenshare.sdk.Common.ErrorCode;
import com.screenshare.sdk.Common.ReceiverState;

/**
 * 接收端（Receiver）
 *
 * 负责：UDP 接收 → RTP 解包 → 解码 → 渲染
 *       UDP 接收 → 触摸事件解码 → 注入到系统
 */
public class Receiver {

    private static final String TAG = "ScreenshareSDK.Receiver";

    private final Context context;
    private final ReceiverConfig config;

    private VideoDecoder videoDecoder;
    private AudioDecoder audioDecoder;
    private RtpSession rtpSession;
    private UdpChannel videoChannel;
    private UdpChannel touchChannel;
    private TouchDecoder touchDecoder;

    private ReceiverState state = ReceiverState.LISTENING;
    private EventListener eventListener;

    public interface EventListener {
        void onStateChanged(ReceiverState state);
        void onError(int errorCode, String message);
        void onFrameReceived(long timestamp, int size);
        void onTouchEventReceived(long timestamp, int action, float x, float y);
        void onConnected(String senderAddress);
        void onDisconnected();
    }

    public Receiver(Context context, ReceiverConfig config) {
        this.context = context;
        this.config = config;
    }

    public void setEventListener(EventListener listener) {
        this.eventListener = listener;
    }

    public Context getContext() {
        return context;
    }

    public ReceiverConfig getConfig() {
        return config;
    }

    public ReceiverState getState() {
        return state;
    }

    private void setState(ReceiverState newState) {
        if (state != newState) {
            state = newState;
            if (eventListener != null) {
                eventListener.onStateChanged(newState);
            }
        }
    }

    /**
     * 开始监听（等待发送端连接）
     */
    public synchronized void startListening() {
        if (state != ReceiverState.LISTENING) {
            notifyError(ErrorCode.ERR_INVALID_STATE, "Cannot start listening in state: " + state);
            return;
        }

        try {
            setState(ReceiverState.LISTENING);

            // 1. 分配原子化缓冲区
            AtomicBuffer videoBuffer = new AtomicBuffer(config.receiveBufferSize);

            // 2. 初始化视频解码器
            videoDecoder = new VideoDecoder(context, config);
            videoDecoder.setEventListener(new VideoDecoder.EventListener() {
                @Override
                public void onFrameDecoded(long timestamp, int size) {
                    if (eventListener != null) {
                        eventListener.onFrameReceived(timestamp, size);
                    }
                }

                @Override
                public void onError(int errorCode, String message) {
                    notifyError(errorCode, message);
                }
            });
            videoDecoder.start();

            // 3. 初始化视频接收通道
            videoChannel = new UdpChannel(config.listenVideoPort);
            videoChannel.setMode(true); // 监听模式
            videoChannel.open();
            rtpSession = new RtpSession(videoDecoder, videoChannel, videoBuffer);
            rtpSession.setReceiveMode(true);
            rtpSession.start();

            // 4. 初始化触摸接收通道
            touchChannel = new UdpChannel(config.listenTouchPort);
            touchChannel.setMode(true);
            touchChannel.open();
            touchDecoder = new TouchDecoder(touchChannel);
            touchDecoder.setEventListener(new TouchDecoder.EventListener() {
                @Override
                public void onTouchEvent(long timestamp, int action, float x, float y) {
                    if (eventListener != null) {
                        eventListener.onTouchEventReceived(timestamp, action, x, y);
                    }
                }

                @Override
                public void onError(int errorCode, String message) {
                    notifyError(errorCode, message);
                }
            });
            touchDecoder.start();

        } catch (Exception e) {
            notifyError(ErrorCode.ERR_NETWORK_START_FAILED, "Failed to start listening: " + e.getMessage());
            stopListening();
        }
    }

    /**
     * 停止监听
     */
    public synchronized void stopListening() {
        setState(ReceiverState.LISTENING);

        if (videoDecoder != null) {
            videoDecoder.stop();
            videoDecoder = null;
        }
        if (audioDecoder != null) {
            audioDecoder.stop();
            audioDecoder = null;
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
        if (touchDecoder != null) {
            touchDecoder.stop();
            touchDecoder = null;
        }
    }

    /**
     * 释放所有资源
     */
    public synchronized void release() {
        stopListening();
    }

    private void notifyError(int errorCode, String message) {
        if (eventListener != null) {
            eventListener.onError(errorCode, message);
        }
    }
}