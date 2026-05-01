package com.screenshare.sdk.capture;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.view.Surface;

import com.screenshare.sdk.SenderConfig;

/**
 * 屏幕采集器
 *
 * 使用 MediaProjection API 采集屏幕内容
 * 输出到编码器的输入 Surface
 */
public class ScreenCapturer {

    private static final String TAG = "ScreenCapturer";

    private final Context context;
    private final SenderConfig config;
    private final Object encoder; // VideoEncoder

    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private Surface inputSurface;

    private boolean isCapturing = false;
    private EventListener eventListener;

    public interface EventListener {
        void onFrameAvailable(long timestamp, int size);
        void onError(int errorCode, String message);
    }

    public ScreenCapturer(Context context, SenderConfig config, Object videoEncoder) {
        this.context = context;
        this.config = config;
        this.encoder = videoEncoder;
    }

    public void setEventListener(EventListener listener) {
        this.eventListener = listener;
    }

    /**
     * 获取采集输入 Surface
     * 需要在 start() 之前调用
     */
    public Surface getInputSurface() {
        return inputSurface;
    }

    /**
     * 开始采集
     */
    public synchronized void start() {
        if (isCapturing) {
            return;
        }

        try {
            // 获取 MediaProjectionManager
            MediaProjectionManager mgr = (MediaProjectionManager)
                context.getSystemService(Context.MEDIA_PROJECTION_SERVICE);

            // 创建 MediaProjection（需要先请求权限）
            // 注意：权限请求应在 App 层完成，这里只负责初始化
            // 实际项目会传入已授权的 MediaProjection 实例

            // 创建虚拟显示器
            // 输入 Surface 传递给编码器
            // 通过 ImageReader 获取 Surface

            isCapturing = true;

        } catch (Exception e) {
            isCapturing = false;
            if (eventListener != null) {
                eventListener.onError(2001, "Failed to start capture: " + e.getMessage());
            }
        }
    }

    /**
     * 停止采集
     */
    public synchronized void stop() {
        isCapturing = false;

        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }

        if (mediaProjection != null) {
            mediaProjection.stop();
            mediaProjection = null;
        }

        inputSurface = null;
    }

    /**
     * 检查是否正在采集
     */
    public boolean isCapturing() {
        return isCapturing;
    }

    /**
     * 释放资源
     */
    public synchronized void release() {
        stop();
    }
}