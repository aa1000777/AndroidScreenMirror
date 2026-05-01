package com.screenshare.sdk.codec;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Process;
import android.util.Log;

import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 音频播放器（AudioPlayer）
 *
 * 使用 AudioTrack 播放解码后的 PCM 数据
 * 输入：AAC 数据 → AudioDecoder 解码 → PCM → AudioTrack 播放
 */
public class AudioPlayer {

    private static final String TAG = "AudioPlayer";

    // 采样率
    public static final int SAMPLE_RATE = 48000;

    // 通道配置（单声道）
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_MONO;

    // 音频格式（16bit PCM）
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;

    // 缓冲区大小（字节）
    private static final int BUFFER_SIZE_BYTES = 4096;

    private final Context context;

    private AudioTrack audioTrack;
    private Thread playerThread;
    private AtomicBoolean isRunning = new AtomicBoolean(false);

    private BlockingQueue<byte[]> inputQueue = new LinkedBlockingQueue<>(50);
    private EventListener eventListener;

    public interface EventListener {
        void onError(int errorCode, String message);
    }

    public AudioPlayer(Context context) {
        this.context = context;
    }

    public void setEventListener(EventListener listener) {
        this.eventListener = listener;
    }

    /**
     * 获取输入队列（用于喂入 PCM 数据）
     */
    public BlockingQueue<byte[]> getInputQueue() {
        return inputQueue;
    }

    /**
     * 启动播放器
     */
    public synchronized boolean start() {
        if (isRunning.getAndSet(true)) {
            Log.w(TAG, "AudioPlayer already running");
            return false;
        }

        try {
            // 计算最小缓冲区大小
            int minBufferSize = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT
            );

            if (minBufferSize == AudioTrack.ERROR || minBufferSize == AudioTrack.ERROR_BAD_VALUE) {
                Log.e(TAG, "Invalid buffer size: " + minBufferSize);
                isRunning.set(false);
                return false;
            }

            int bufferSize = Math.max(minBufferSize, BUFFER_SIZE_BYTES * 4);

            // 创建 AudioTrack
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                .build();

            AudioFormat audioFormat = new AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(CHANNEL_CONFIG)
                .setEncoding(AUDIO_FORMAT)
                .build();

            audioTrack = new AudioTrack(
                audioAttributes,
                audioFormat,
                bufferSize,
                AudioTrack.MODE_STREAM,
                android.media.AudioManager.AUDIO_SESSION_ID_GENERATE
            );

            if (audioTrack.getState() != AudioTrack.STATE_INITIALIZED) {
                Log.e(TAG, "AudioTrack not initialized");
                release();
                return false;
            }

            // 设置线程优先级
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);

            // 开始播放
            audioTrack.play();

            // 启动播放线程
            playerThread = new Thread(this::playLoop, "AudioPlayerThread");
            playerThread.start();

            Log.i(TAG, "AudioPlayer started");
            return true;

        } catch (Exception e) {
            Log.e(TAG, "Failed to start AudioPlayer", e);
            release();
            return false;
        }
    }

    private void playLoop() {
        byte[] buffer = new byte[BUFFER_SIZE_BYTES];

        while (isRunning.get()) {
            try {
                byte[] data = inputQueue.poll(100, java.util.concurrent.TimeUnit.MILLISECONDS);
                if (data == null) {
                    continue;
                }

                // 写入 AudioTrack
                int written = audioTrack.write(data, 0, data.length);
                if (written < 0) {
                    Log.w(TAG, "AudioTrack write returned: " + written);
                }

            } catch (Exception e) {
                if (!isRunning.get()) {
                    break;
                }
                Log.e(TAG, "Play error", e);
            }
        }
    }

    /**
     * 停止播放器
     */
    public synchronized void stop() {
        if (!isRunning.getAndSet(false)) {
            return;
        }

        if (audioTrack != null) {
            try {
                audioTrack.stop();
                audioTrack.release();
            } catch (Exception e) {
                Log.e(TAG, "Error stopping AudioTrack", e);
            }
            audioTrack = null;
        }

        if (playerThread != null) {
            try {
                playerThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            playerThread = null;
        }

        inputQueue.clear();

        Log.i(TAG, "AudioPlayer stopped");
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
}