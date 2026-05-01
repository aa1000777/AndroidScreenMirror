package com.screenshare.sdk.codec;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.view.Surface;

import com.screenshare.sdk.ReceiverConfig;
import com.screenshare.sdk.Common.ErrorCode;

import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 视频解码器（接收端用）
 *
 * 使用 MediaCodec 硬件解码 H.264/H.265
 * 输入：RTP 解包后的 NAL 单元
 * 输出：解码后的图像 → Surface 渲染
 */
public class VideoDecoder {

    private static final String TAG = "VideoDecoder";

    private final Context context;
    private final ReceiverConfig config;

    private MediaCodec decoderCodec;
    private Surface outputSurface;
    private Thread decoderThread;
    private AtomicBoolean isRunning = new AtomicBoolean(false);

    private BlockingQueue<byte[]> inputQueue = new LinkedBlockingQueue<>(30);
    private EventListener eventListener;

    public interface EventListener {
        void onFrameDecoded(long timestamp, int size);
        void onError(int errorCode, String message);
    }

    public VideoDecoder(Context context, ReceiverConfig config) {
        this.context = context;
        this.config = config;
    }

    public void setEventListener(EventListener listener) {
        this.eventListener = listener;
    }

    /**
     * 设置输出 Surface（用于渲染解码后的图像）
     */
    public void setOutputSurface(Surface surface) {
        this.outputSurface = surface;
    }

    /**
     * 获取输入队列（用于 RTP 解包后喂数据）
     */
    public BlockingQueue<byte[]> getInputQueue() {
        return inputQueue;
    }

    /**
     * 启动解码器
     */
    public synchronized void start() {
        if (isRunning.get()) {
            return;
        }

        try {
            // 创建 MediaFormat
            String mimeType = config.videoCodec == ReceiverConfig.VideoCodecType.H265_HARDWARE
                ? MediaFormat.MIMETYPE_VIDEO_HEVC
                : MediaFormat.MIMETYPE_VIDEO_AVC;

            MediaFormat format = MediaFormat.createVideoFormat(mimeType, config.width, config.height);

            // 低延迟模式（API 29+）
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                format.setInteger("latency", 0);
            }

            // 创建解码器
            decoderCodec = MediaCodec.createDecoderByType(mimeType);
            decoderCodec.configure(format, outputSurface, null, 0);
            decoderCodec.start();

            isRunning.set(true);

            // 启动解码线程
            decoderThread = new Thread(this::decodeLoop, "VideoDecoderThread");
            decoderThread.start();

        } catch (Exception e) {
            if (eventListener != null) {
                eventListener.onError(ErrorCode.ERR_DECODER_INIT_FAILED, "Failed to init decoder: " + e.getMessage());
            }
        }
    }

    private void decodeLoop() {
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        ByteBuffer[] inputBuffers = decoderCodec.getInputBuffers();

        while (isRunning.get()) {
            try {
                // 从输入队列取数据
                byte[] data = inputQueue.poll(100, java.util.concurrent.TimeUnit.MILLISECONDS);
                if (data == null) {
                    continue;
                }

                // 入队
                int inputIndex = decoderCodec.dequeueInputBuffer(10000);
                if (inputIndex >= 0) {
                    ByteBuffer inputBuffer = decoderCodec.getInputBuffer(inputIndex);
                    if (inputBuffer != null) {
                        inputBuffer.clear();
                        inputBuffer.put(data);
                        decoderCodec.queueInputBuffer(inputIndex, 0, data.length,
                            System.nanoTime() / 1000, 0);
                    }
                }

                // 出队
                int outputIndex = decoderCodec.dequeueOutputBuffer(info, 10000);
                if (outputIndex >= 0) {
                    decoderCodec.releaseOutputBuffer(outputIndex, info.size > 0);

                    if (info.size > 0 && eventListener != null) {
                        eventListener.onFrameDecoded(info.presentationTimeUs, info.size);
                    }
                }

            } catch (Exception e) {
                if (!isRunning.get()) {
                    break;
                }
            }
        }
    }

    /**
     * 停止解码器
     */
    public synchronized void stop() {
        isRunning.set(false);

        if (decoderThread != null) {
            try {
                decoderThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            decoderThread = null;
        }

        if (decoderCodec != null) {
            try {
                decoderCodec.stop();
                decoderCodec.release();
            } catch (Exception e) {
                // ignore
            }
            decoderCodec = null;
        }

        inputQueue.clear();
    }

    /**
     * 释放资源
     */
    public synchronized void release() {
        stop();
    }
}