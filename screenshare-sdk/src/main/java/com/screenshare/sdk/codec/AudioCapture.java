package com.screenshare.sdk.codec;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Process;
import android.util.Log;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 音频采集器（AudioCapture）
 *
 * 使用 AudioRecord 采集麦克风音频
 * 输出：PCM 数据 → AudioEncoder 编码
 */
public class AudioCapture {

    private static final String TAG = "AudioCapture";

    // 采样率
    public static final int SAMPLE_RATE = 48000;

    // 通道配置（单声道）
    public static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;

    // 音频格式（16bit PCM）
    public static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;

    // 缓冲区大小（帧）
    private static final int BUFFER_SIZE_FRAMES = 1024;

    private final Context context;

    private AudioRecord audioRecord;
    private int bufferSizeInBytes;

    private Thread captureThread;
    private AtomicBoolean isRunning = new AtomicBoolean(false);

    private EventListener eventListener;

    // 音频数据回调
    public interface EventListener {
        void onAudioDataAvailable(byte[] pcmData, int size, long timestamp);
        void onError(int errorCode, String message);
    }

    public AudioCapture(Context context) {
        this.context = context;
    }

    public void setEventListener(EventListener listener) {
        this.eventListener = listener;
    }

    /**
     * 启动音频采集
     */
    public synchronized boolean start() {
        if (isRunning.getAndSet(true)) {
            Log.w(TAG, "AudioCapture already running");
            return false;
        }

        try {
            // 计算最小缓冲区大小
            int minBufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT
            );

            if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
                Log.e(TAG, "Invalid buffer size: " + minBufferSize);
                isRunning.set(false);
                notifyError(-1, "Invalid buffer size");
                return false;
            }

            bufferSizeInBytes = Math.max(minBufferSize, BUFFER_SIZE_FRAMES * 2); // 16bit mono

            // 创建 AudioRecord
            audioRecord = new AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSizeInBytes
            );

            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord not initialized");
                release();
                notifyError(-1, "AudioRecord initialization failed");
                return false;
            }

            // 设置线程优先级
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);

            // 开始采集
            audioRecord.startRecording();

            // 启动采集线程
            captureThread = new Thread(this::captureLoop, "AudioCaptureThread");
            captureThread.start();

            Log.i(TAG, "AudioCapture started");
            return true;

        } catch (Exception e) {
            Log.e(TAG, "Failed to start AudioCapture", e);
            release();
            notifyError(-1, "Failed to start: " + e.getMessage());
            return false;
        }
    }

    private void captureLoop() {
        byte[] buffer = new byte[bufferSizeInBytes];
        long timestamp = System.nanoTime() / 1000; // 微秒

        while (isRunning.get()) {
            try {
                int bytesRead = audioRecord.read(buffer, 0, buffer.length);

                if (bytesRead > 0) {
                    // 复制数据（避免直接返回内部缓冲区）
                    byte[] data = new byte[bytesRead];
                    System.arraycopy(buffer, 0, data, 0, bytesRead);

                    if (eventListener != null) {
                        eventListener.onAudioDataAvailable(data, bytesRead, timestamp);
                    }

                    timestamp += bytesRead * 1000000L / (SAMPLE_RATE * 2); // 16bit mono
                }

            } catch (Exception e) {
                if (!isRunning.get()) {
                    break;
                }
                Log.e(TAG, "Capture error", e);
            }
        }
    }

    /**
     * 停止音频采集
     */
    public synchronized void stop() {
        if (!isRunning.getAndSet(false)) {
            return;
        }

        if (audioRecord != null) {
            try {
                if (audioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
                    audioRecord.stop();
                }
                audioRecord.release();
            } catch (Exception e) {
                Log.e(TAG, "Error stopping AudioRecord", e);
            }
            audioRecord = null;
        }

        if (captureThread != null) {
            try {
                captureThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            captureThread = null;
        }

        Log.i(TAG, "AudioCapture stopped");
    }

    /**
     * 释放资源
     */
    public synchronized void release() {
        stop();
    }

    /**
     * 是否正在运行
     */
    public boolean isRunning() {
        return isRunning.get();
    }

    private void notifyError(int code, String message) {
        if (eventListener != null) {
            eventListener.onError(code, message);
        }
    }
}