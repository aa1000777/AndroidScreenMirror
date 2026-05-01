package com.screenshare.sdk.codec;

import android.content.Context;

/**
 * 音频解码器（接收端用，可选功能）
 *
 * 当前为占位符，后续实现
 */
public class AudioDecoder {

    private static final String TAG = "AudioDecoder";

    private final Context context;
    private boolean isRunning = false;
    private EventListener eventListener;

    public interface EventListener {
        void onAudioDecoded(byte[] data, int size, long timestamp);
        void onError(int errorCode, String message);
    }

    public AudioDecoder(Context context) {
        this.context = context;
    }

    public void setEventListener(EventListener listener) {
        this.eventListener = listener;
    }

    public synchronized void start() {
        if (isRunning) {
            return;
        }
        isRunning = true;
    }

    public synchronized void stop() {
        isRunning = false;
    }

    public synchronized void release() {
        stop();
    }
}