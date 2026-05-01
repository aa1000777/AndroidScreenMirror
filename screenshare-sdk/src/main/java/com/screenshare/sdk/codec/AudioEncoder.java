package com.screenshare.sdk.codec;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.util.Log;

import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 音频编码器（AudioEncoder）
 *
 * 使用 MediaCodec 编码 PCM → AAC
 * 输入：AudioCapture 采集的 PCM 数据
 * 输出：编码后的 AAC 数据 → RTP 打包发送
 */
public class AudioEncoder {

    private static final String TAG = "AudioEncoder";

    // AAC 采样率
    private static final int SAMPLE_RATE = 48000;

    // AAC 通道数
    private static final int CHANNEL_COUNT = 1;

    // AAC 比特率
    private static final int BIT_RATE = 128000;

    // AAC 格式
    private static final String MIME_TYPE = "audio/mp4a-latm";

    private final Context context;

    private MediaCodec encoderCodec;
    private Thread encoderThread;
    private AtomicBoolean isRunning = new AtomicBoolean(false);

    private BlockingQueue<byte[]> inputQueue = new LinkedBlockingQueue<>(50);
    private BlockingQueue<byte[]> outputQueue = new LinkedBlockingQueue<>(50);

    private EventListener eventListener;

    public interface EventListener {
        void onEncodedData(byte[] aacData, int size, long timestamp);
        void onError(int errorCode, String message);
    }

    public AudioEncoder(Context context) {
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
     * 获取输出队列（用于取出编码后的 AAC 数据）
     */
    public BlockingQueue<byte[]> getOutputQueue() {
        return outputQueue;
    }

    /**
     * 启动编码器
     */
    public synchronized void start() {
        if (isRunning.get()) {
            return;
        }

        try {
            // 创建 MediaFormat
            MediaFormat format = MediaFormat.createAudioFormat(MIME_TYPE, SAMPLE_RATE, CHANNEL_COUNT);
            format.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE);
            format.setInteger(MediaFormat.KEY_AAC_PROFILE, 2); // AAC-LC

            // 创建编码器
            encoderCodec = MediaCodec.createEncoderByType(MIME_TYPE);
            encoderCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            encoderCodec.start();

            isRunning.set(true);

            // 启动编码线程
            encoderThread = new Thread(this::encodeLoop, "AudioEncoderThread");
            encoderThread.start();

            Log.i(TAG, "AudioEncoder started");

        } catch (Exception e) {
            Log.e(TAG, "Failed to start AudioEncoder", e);
            if (eventListener != null) {
                eventListener.onError(-1, "Failed to start: " + e.getMessage());
            }
        }
    }

    private void encodeLoop() {
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        ByteBuffer[] inputBuffers = encoderCodec.getInputBuffers();
        ByteBuffer[] outputBuffers = encoderCodec.getOutputBuffers();

        while (isRunning.get()) {
            try {
                // 填充输入
                int inputIndex = encoderCodec.dequeueInputBuffer(10000);
                if (inputIndex >= 0) {
                    ByteBuffer inputBuffer = inputBuffers[inputIndex];
                    inputBuffer.clear();

                    byte[] pcmData = inputQueue.poll(100, java.util.concurrent.TimeUnit.MILLISECONDS);
                    if (pcmData != null) {
                        int size = Math.min(pcmData.length, inputBuffer.remaining());
                        inputBuffer.put(pcmData, 0, size);
                        encoderCodec.queueInputBuffer(inputIndex, 0, size, 0, 0);
                    } else {
                        // 无数据，填充静音
                        encoderCodec.queueInputBuffer(inputIndex, 0, 0, 0, 0);
                    }
                }

                // 消费输出
                int outputIndex = encoderCodec.dequeueOutputBuffer(info, 10000);
                if (outputIndex >= 0) {
                    ByteBuffer outputBuffer = encoderCodec.getOutputBuffer(outputIndex);

                    if (outputBuffer != null && info.size > 0) {
                        byte[] aacData = new byte[info.size];
                        outputBuffer.get(aacData, 0, info.size);

                        // 加入输出队列
                        if (!outputQueue.offer(aacData)) {
                            outputQueue.poll();
                            outputQueue.offer(aacData);
                        }

                        if (eventListener != null) {
                            eventListener.onEncodedData(aacData, info.size, info.presentationTimeUs);
                        }
                    }

                    encoderCodec.releaseOutputBuffer(outputIndex, false);

                } else if (outputIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    outputBuffers = encoderCodec.getOutputBuffers();
                }

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
    public synchronized void stop() {
        isRunning.set(false);

        if (encoderThread != null) {
            try {
                encoderThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            encoderThread = null;
        }

        if (encoderCodec != null) {
            try {
                encoderCodec.stop();
                encoderCodec.release();
            } catch (Exception e) {
                // ignore
            }
            encoderCodec = null;
        }

        inputQueue.clear();
        outputQueue.clear();

        Log.i(TAG, "AudioEncoder stopped");
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