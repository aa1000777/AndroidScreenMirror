package com.screenshare.sdk.codec;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Build;
import android.view.Surface;

import com.screenshare.sdk.SenderConfig;
import com.screenshare.sdk.Common.ErrorCode;

import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 视频编码器（发送端用）
 *
 * 使用 MediaCodec 硬件编码 H.264/H.265
 * 输入：Surface（来自屏幕采集）
 * 输出：编码后的 NAL 单元 → RTP 打包
 */
public class VideoEncoder {

    private static final String TAG = "VideoEncoder";

    private final Context context;
    private final SenderConfig config;

    private MediaCodec encoderCodec;
    private Surface inputSurface;
    private Thread encoderThread;
    private AtomicBoolean isRunning = new AtomicBoolean(false);

    private BlockingQueue<byte[]> outputQueue = new LinkedBlockingQueue<>(30);
    private EventListener eventListener;

    // 编码参数
    private int width;
    private int height;
    private int fps;
    private int bitrate;
    private int keyFrameInterval;

    public interface EventListener {
        void onEncodedFrame(byte[] data, int offset, int length, long timestamp, boolean isKeyFrame);
        void onError(int errorCode, String message);
    }

    public VideoEncoder(Context context, SenderConfig config) {
        this.context = context;
        this.config = config;

        this.width = config.width;
        this.height = config.height;
        this.fps = config.fps;
        this.bitrate = config.videoBitrate > 0 ? config.videoBitrate : 8000000;
        this.keyFrameInterval = config.keyFrameInterval;
    }

    public void setEventListener(EventListener listener) {
        this.eventListener = listener;
    }

    /**
     * 获取输入 Surface（用于屏幕采集）
     */
    public Surface getInputSurface() {
        return inputSurface;
    }

    /**
     * 获取输出队列（编码后的帧）
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
            MediaFormat format = MediaFormat.createVideoFormat(
                config.videoCodec == SenderConfig.VideoCodecType.H265_HARDWARE
                    ? MediaFormat.MIMETYPE_VIDEO_HEVC
                    : MediaFormat.MIMETYPE_VIDEO_AVC,
                width,
                height
            );

            // 码率
            format.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);

            // 帧率
            format.setInteger(MediaFormat.KEY_FRAME_RATE, fps);

            // 关键帧间隔
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, keyFrameInterval);

            // 编码延迟模式（低延迟，API 29+）
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                format.setInteger("latency", 0);
            }

            // Profile（Baseline for broad compatibility）
            if (config.videoCodec == SenderConfig.VideoCodecType.H264_HARDWARE) {
                format.setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline);
            }

            // 创建编码器
            String mimeType = config.videoCodec == SenderConfig.VideoCodecType.H265_HARDWARE
                ? MediaFormat.MIMETYPE_VIDEO_HEVC
                : MediaFormat.MIMETYPE_VIDEO_AVC;

            encoderCodec = MediaCodec.createEncoderByType(mimeType);
            encoderCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

            // 获取输入 Surface 并启动编码器
            inputSurface = encoderCodec.createInputSurface();
            encoderCodec.start();

            isRunning.set(true);

            // 启动编码线程
            encoderThread = new Thread(this::encodeLoop, "VideoEncoderThread");
            encoderThread.start();

        } catch (Exception e) {
            if (eventListener != null) {
                eventListener.onError(ErrorCode.ERR_ENCODER_INIT_FAILED, "Failed to init encoder: " + e.getMessage());
            }
        }
    }

    private void encodeLoop() {
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        ByteBuffer[] outputBuffers = encoderCodec.getOutputBuffers();

        while (isRunning.get()) {
            try {
                int bufferIndex = encoderCodec.dequeueOutputBuffer(info, 10000);

                if (bufferIndex >= 0) {
                    // 有输出
                    ByteBuffer outputBuffer = encoderCodec.getOutputBuffer(bufferIndex);

                    if (outputBuffer != null && info.size > 0) {
                        byte[] data = new byte[info.size];
                        outputBuffer.get(data, 0, info.size);

                        // 加入输出队列
                        if (!outputQueue.offer(data)) {
                            // 队列满，丢弃最旧的
                            outputQueue.poll();
                            outputQueue.offer(data);
                        }

                        if (eventListener != null) {
                            boolean isKeyFrame = (info.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0;
                            eventListener.onEncodedFrame(data, 0, data.length, info.presentationTimeUs, isKeyFrame);
                        }
                    }

                    encoderCodec.releaseOutputBuffer(bufferIndex, false);
                } else if (bufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    // 无输出，稍等
                    Thread.sleep(1);
                } else if (bufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    outputBuffers = encoderCodec.getOutputBuffers();
                }

            } catch (Exception e) {
                if (!isRunning.get()) {
                    break;
                }
                if (eventListener != null) {
                    eventListener.onError(ErrorCode.ERR_ENCODER_TIMEOUT, "Encoder error: " + e.getMessage());
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

        inputSurface = null;
        outputQueue.clear();
    }

    /**
     * 释放资源
     */
    public synchronized void release() {
        stop();
    }
}