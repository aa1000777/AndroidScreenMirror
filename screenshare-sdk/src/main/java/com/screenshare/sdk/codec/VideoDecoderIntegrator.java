package com.screenshare.sdk.codec;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;

import com.screenshare.sdk.ReceiverConfig;
import com.screenshare.sdk.Common.ErrorCode;

import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 视频解码器集成器（VideoDecoderIntegrator）
 *
 * 负责任务：
 * - 管理 TextureView 的生命周期
 * - 处理 SurfaceTexture 可用性
 * - 将解码后的帧渲染到 TextureView
 * - 处理画面比例适配
 */
public class VideoDecoderIntegrator implements TextureView.SurfaceTextureListener {

    private static final String TAG = "VideoDecoderIntegrator";

    private final Context context;
    private final ReceiverConfig config;

    private TextureView textureView;
    private SurfaceTexture availableSurfaceTexture;
    private Surface decoderSurface;

    private VideoDecoder videoDecoder;
    private boolean isSurfaceReady = false;

    private EventListener eventListener;
    private HandlerThread decoderThread;
    private Handler decoderHandler;

    public interface EventListener {
        void onSurfaceReady(Surface surface);
        void onSurfaceDestroyed();
        void onFrameRendered(long timestamp, int size);
        void onError(int errorCode, String message);
    }

    public VideoDecoderIntegrator(Context context, ReceiverConfig config) {
        this.context = context;
        this.config = config;
    }

    public void setEventListener(EventListener listener) {
        this.eventListener = listener;
    }

    /**
     * 绑定 TextureView
     */
    public void bindTextureView(TextureView view) {
        if (this.textureView != null) {
            this.textureView.setSurfaceTextureListener(null);
        }

        this.textureView = view;
        this.textureView.setSurfaceTextureListener(this);

        // 如果 SurfaceTexture 已经可用
        if (this.textureView.getSurfaceTexture() != null) {
            onSurfaceTextureAvailable(this.textureView.getSurfaceTexture(),
                this.textureView.getWidth(), this.textureView.getHeight());
        }
    }

    /**
     * 解绑 TextureView
     */
    public void unbindTextureView() {
        if (textureView != null) {
            textureView.setSurfaceTextureListener(null);
            textureView = null;
        }
        releaseSurface();
    }

    /**
     * 启动解码器（需要 Surface 准备就绪）
     */
    public synchronized void start() {
        if (!isSurfaceReady) {
            Log.w(TAG, "Surface not ready, cannot start decoder");
            return;
        }

        if (videoDecoder != null && videoDecoder.isRunning()) {
            return;
        }

        // 创建 VideoDecoder
        videoDecoder = new VideoDecoder(context, config);
        videoDecoder.setOutputSurface(decoderSurface);
        videoDecoder.setEventListener(new VideoDecoder.EventListener() {
            @Override
            public void onFrameDecoded(long timestamp, int size) {
                if (eventListener != null) {
                    eventListener.onFrameRendered(timestamp, size);
                }
            }

            @Override
            public void onError(int errorCode, String message) {
                if (eventListener != null) {
                    eventListener.onError(errorCode, message);
                }
            }
        });

        // 创建解码线程
        decoderThread = new HandlerThread("VideoDecoderIntegrator");
        decoderThread.start();
        decoderHandler = new Handler(decoderThread.getLooper());

        videoDecoder.start();

        Log.i(TAG, "VideoDecoderIntegrator started");
    }

    /**
     * 停止解码器
     */
    public synchronized void stop() {
        if (videoDecoder != null) {
            videoDecoder.stop();
            videoDecoder = null;
        }

        if (decoderThread != null) {
            decoderThread.quitSafely();
            try {
                decoderThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            decoderThread = null;
            decoderHandler = null;
        }

        Log.i(TAG, "VideoDecoderIntegrator stopped");
    }

    /**
     * 获取输入队列（用于喂入编码数据）
     */
    public BlockingQueue<byte[]> getInputQueue() {
        return videoDecoder != null ? videoDecoder.getInputQueue() : null;
    }

    /**
     * 是否有可用输入队列
     */
    public boolean isInputQueueAvailable() {
        return videoDecoder != null && videoDecoder.isRunning();
    }

    // ========== TextureView.SurfaceTextureListener ==========

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        Log.d(TAG, "SurfaceTexture available: " + width + "x" + height);

        this.availableSurfaceTexture = surface;
        this.isSurfaceReady = true;

        // 创建 Surface 用于解码器
        this.decoderSurface = new Surface(surface);

        if (eventListener != null) {
            eventListener.onSurfaceReady(decoderSurface);
        }

        // 如果之前的解码器还在运行，先停止
        stop();

        // 可选：立即启动解码器
        // start();
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        Log.d(TAG, "SurfaceTexture size changed: " + width + "x" + height);
        // 可选：调整解码器参数或重新配置画面比例
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        Log.d(TAG, "SurfaceTexture destroyed");

        isSurfaceReady = false;
        releaseSurface();

        if (eventListener != null) {
            eventListener.onSurfaceDestroyed();
        }

        return true; // SurfaceTexture 将被释放
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        // 每帧更新时调用（用于 TextureView 自动渲染）
    }

    private void releaseSurface() {
        if (decoderSurface != null) {
            decoderSurface.release();
            decoderSurface = null;
        }
        availableSurfaceTexture = null;
    }

    /**
     * 获取当前 Surface
     */
    public Surface getSurface() {
        return decoderSurface;
    }

    /**
     * 检查 Surface 是否就绪
     */
    public boolean isSurfaceReady() {
        return isSurfaceReady;
    }

    /**
     * 释放所有资源
     */
    public void release() {
        stop();
        unbindTextureView();
    }

    /**
     * 内部 VideoDecoder 扩展
     */
    public static class VideoDecoder {

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

        public void setOutputSurface(Surface surface) {
            this.outputSurface = surface;
        }

        public BlockingQueue<byte[]> getInputQueue() {
            return inputQueue;
        }

        public synchronized void start() {
            if (isRunning.get()) {
                return;
            }

            try {
                String mimeType = config.videoCodec == ReceiverConfig.VideoCodecType.H265_HARDWARE
                    ? MediaFormat.MIMETYPE_VIDEO_HEVC
                    : MediaFormat.MIMETYPE_VIDEO_AVC;

                MediaFormat format = MediaFormat.createVideoFormat(mimeType, config.width, config.height);

                // 低延迟模式
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    format.setInteger("latency", 0);
                }

                decoderCodec = MediaCodec.createDecoderByType(mimeType);
                decoderCodec.configure(format, outputSurface, null, 0);
                decoderCodec.start();

                isRunning.set(true);

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
                    byte[] data = inputQueue.poll(100, java.util.concurrent.TimeUnit.MILLISECONDS);
                    if (data == null) {
                        continue;
                    }

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

                    int outputIndex = decoderCodec.dequeueOutputBuffer(info, 10000);
                    if (outputIndex >= 0) {
                        decoderCodec.releaseOutputBuffer(outputIndex, info.size > 0);

                        if (info.size > 0 && eventListener != null) {
                            eventListener.onFrameDecoded(info.presentationTimeUs, info.size);
                        }
                    } else if (outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        // 无输出，稍等
                    } else if (outputIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                        inputBuffers = decoderCodec.getInputBuffers();
                    }

                } catch (Exception e) {
                    if (!isRunning.get()) {
                        break;
                    }
                }
            }
        }

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

        public boolean isRunning() {
            return isRunning.get();
        }
    }
}