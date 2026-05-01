package com.screenshare.sdk.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Surface;
import android.view.WindowManager;

import com.screenshare.sdk.Sender;
import com.screenshare.sdk.SenderConfig;
import com.screenshare.sdk.codec.VideoEncoder;
import com.screenshare.sdk.Common.ErrorCode;

/**
 * 屏幕采集前台服务
 *
 * 运行在前台，保证采集不会被系统杀死
 * 管理 MediaProjection 生命周期
 */
public class ScreenCaptureService extends Service {

    private static final String TAG = "ScreenCaptureService";
    private static final String CHANNEL_ID = "screenshare_capture_channel";
    private static final int NOTIFICATION_ID = 1001;

    private static ScreenCaptureService instance;

    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private Surface captureSurface;

    private VideoEncoder encoder;
    private SenderConfig config;

    private boolean isRunning = false;
    private EventListener eventListener;
    private Handler mainHandler;

    public interface EventListener {
        void onCaptureStarted();
        void onCaptureStopped();
        void onError(int errorCode, String message);
    }

    public static ScreenCaptureService getInstance() {
        return instance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        mainHandler = new Handler(Looper.getMainLooper());
        createNotificationChannel();
        Log.d(TAG, "ScreenCaptureService created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (isRunning) {
            return START_STICKY;
        }

        // 从 Intent 获取 MediaProjection
        int resultCode = intent != null ? intent.getIntExtra("resultCode", -1) : -1;
        if (resultCode == -1) {
            stopSelf();
            return START_NOT_STICKY;
        }

        android.os.Parcelable resultDataParcel = intent != null ?
            intent.getParcelableExtra("resultData") : null;
        Intent resultData = null;
        if (resultDataParcel instanceof Intent) {
            resultData = (Intent) resultDataParcel;
        }

        if (resultData == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        startForeground(NOTIFICATION_ID, createNotification());

        // 初始化 MediaProjection
        MediaProjectionManager mgr = (MediaProjectionManager)
            getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        if (mgr != null) {
            mediaProjection = mgr.getMediaProjection(resultCode, resultData);
            if (mediaProjection != null) {
                startCapture();
            }
        }

        isRunning = true;
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopCapture();
        isRunning = false;
        instance = null;
        Log.d(TAG, "ScreenCaptureService destroyed");
    }

    public void setEventListener(EventListener listener) {
        this.eventListener = listener;
    }

    /**
     * 初始化采集
     *
     * @param resultCode MediaProjection 权限结果码
     * @param resultData MediaProjection 数据
     * @param config 采集配置
     */
    public void initialize(int resultCode, Intent resultData, SenderConfig config) {
        this.config = config;

        MediaProjectionManager mgr = (MediaProjectionManager)
            getSystemService(Context.MEDIA_PROJECTION_SERVICE);

        if (mgr == null) {
            notifyError(ErrorCode.ERR_MEDIA_PROJECTION_DENIED, "MediaProjection not available");
            return;
        }

        mediaProjection = mgr.getMediaProjection(resultCode, resultData);

        if (mediaProjection == null) {
            notifyError(ErrorCode.ERR_MEDIA_PROJECTION_DENIED, "MediaProjection denied");
            return;
        }

        // 注册生命周期回调
        mediaProjection.registerCallback(new MediaProjection.Callback() {
            @Override
            public void onStop() {
                Log.d(TAG, "MediaProjection stopped");
                stopCapture();
            }
        }, mainHandler);
    }

    /**
     * 启动采集
     */
    public synchronized void startCapture() {
        if (mediaProjection == null) {
            notifyError(ErrorCode.ERR_CAPTURE_NOT_INIT, "MediaProjection not initialized");
            return;
        }

        if (isCapturing()) {
            return;
        }

        try {
            // 获取屏幕尺寸
            WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
            DisplayMetrics metrics = new DisplayMetrics();
            wm.getDefaultDisplay().getRealMetrics(metrics);

            int width = config != null ? config.width : metrics.widthPixels;
            int height = config != null ? config.height : metrics.heightPixels;

            // 创建 ImageReader 作为采集目标
            ImageReader.OnImageAvailableListener listener = reader -> {
                android.media.Image image = reader.acquireLatestImage();
                if (image != null) {
                    // 图像数据会直接传到 encoder 的 Surface
                    image.close();
                }
            };

            imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2);
            imageReader.setOnImageAvailableListener(listener, mainHandler);
            captureSurface = imageReader.getSurface();

            // 创建 VirtualDisplay
            virtualDisplay = mediaProjection.createVirtualDisplay(
                "ScreenshareCapture",
                width, height,
                metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                captureSurface,
                null,
                mainHandler
            );

            if (virtualDisplay == null) {
                notifyError(ErrorCode.ERR_VIRTUAL_DISPLAY_FAILED, "Failed to create VirtualDisplay");
                return;
            }

            Log.d(TAG, "Capture started: " + width + "x" + height);

            if (eventListener != null) {
                mainHandler.post(() -> eventListener.onCaptureStarted());
            }

        } catch (Exception e) {
            notifyError(ErrorCode.ERR_CAPTURE_START_FAILED, "Failed to start capture: " + e.getMessage());
        }
    }

    /**
     * 停止采集
     */
    public synchronized void stopCapture() {
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }

        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }

        captureSurface = null;

        Log.d(TAG, "Capture stopped");

        if (eventListener != null) {
            mainHandler.post(() -> eventListener.onCaptureStopped());
        }
    }

    /**
     * 获取采集 Surface（传递给 VideoEncoder）
     */
    public Surface getCaptureSurface() {
        return captureSurface;
    }

    /**
     * 检查是否正在采集
     */
    public boolean isCapturing() {
        return virtualDisplay != null;
    }

    /**
     * 释放 MediaProjection
     */
    public void releaseProjection() {
        stopCapture();

        if (mediaProjection != null) {
            mediaProjection.unregisterCallback(null);
            mediaProjection.stop();
            mediaProjection = null;
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "屏幕采集服务",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("用于屏幕录制和投屏的持续采集");
            channel.setShowBadge(false);

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification createNotification() {
        Intent notificationIntent = new Intent(this, getClass());
        PendingIntent pendingIntent = PendingIntent.getService(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        );

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder builder = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("屏幕投屏中")
                .setContentText("正在采集屏幕内容")
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setContentIntent(pendingIntent)
                .setOngoing(true);

            return builder.build();
        } else {
            Notification.Builder builder = new Notification.Builder(this)
                .setContentTitle("屏幕投屏中")
                .setContentText("正在采集屏幕内容")
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setContentIntent(pendingIntent)
                .setOngoing(true);

            return builder.build();
        }
    }

    private void notifyError(int code, String message) {
        Log.e(TAG, message);
        if (eventListener != null) {
            mainHandler.post(() -> eventListener.onError(code, message));
        }
    }
}
