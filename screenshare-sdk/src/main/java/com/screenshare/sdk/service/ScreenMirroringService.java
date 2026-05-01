package com.screenshare.sdk.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

/**
 * 投屏前台服务（ScreenMirroringService）
 *
 * 负责：
 * - 作为前台服务运行，保持投屏在后台存活
 * - 显示持续通知（投屏进行中）
 * - 管理 Sender/Receiver 生命周期
 */
public class ScreenMirroringService extends Service {

    private static final String TAG = "ScreenMirroringService";

    private static final String CHANNEL_ID = "screenshare_mirroring";
    private static final int NOTIFICATION_ID = 1001;

    public static final String ACTION_START_SENDER = "com.screenshare.action.START_SENDER";
    public static final String ACTION_START_RECEIVER = "com.screenshare.action.START_RECEIVER";
    public static final String ACTION_STOP = "com.screenshare.action.STOP";

    // Extras for Intent
    public static final String EXTRA_VIDEO_PORT = "video_port";
    public static final String EXTRA_TOUCH_PORT = "touch_port";

    private IBinder binder;
    private boolean isRunning = false;

    private ServiceListener serviceListener;

    public interface ServiceListener {
        void onServiceStarted();
        void onServiceStopped();
        void onError(int errorCode, String message);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();

        Log.i(TAG, "ScreenMirroringService created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            return START_NOT_STICKY;
        }

        String action = intent.getAction();
        if (action == null) {
            return START_NOT_STICKY;
        }

        switch (action) {
            case ACTION_START_SENDER:
                startForegroundWithNotification("投屏中", "正在发送屏幕...");
                isRunning = true;
                if (serviceListener != null) {
                    serviceListener.onServiceStarted();
                }
                break;

            case ACTION_START_RECEIVER:
                startForegroundWithNotification("接收中", "正在接收屏幕...");
                isRunning = true;
                if (serviceListener != null) {
                    serviceListener.onServiceStarted();
                }
                break;

            case ACTION_STOP:
                stopSelf();
                break;

            default:
                break;
        }

        return START_STICKY;
    }

    private void startForegroundWithNotification(String title, String content) {
        Intent notificationIntent = new Intent(this, getMainActivityClass());
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "投屏服务",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("用于保持投屏服务在后台运行");
            channel.setShowBadge(false);

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Class<?> getMainActivityClass() {
        try {
            return Class.forName("com.screenshare.app.ui.MainActivity");
        } catch (ClassNotFoundException e) {
            return android.app.Activity.class;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        if (binder == null) {
            binder = new LocalBinder();
        }
        return binder;
    }

    @Override
    public void onDestroy() {
        isRunning = false;
        if (serviceListener != null) {
            serviceListener.onServiceStopped();
        }
        super.onDestroy();
        Log.i(TAG, "ScreenMirroringService destroyed");
    }

    public boolean isRunning() {
        return isRunning;
    }

    public void setServiceListener(ServiceListener listener) {
        this.serviceListener = listener;
    }

    /**
     * 更新通知内容
     */
    public void updateNotification(String title, String content) {
        Intent notificationIntent = new Intent(this, getMainActivityClass());
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build();

        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, notification);
        }
    }

    /**
     * 停止服务
     */
    public void stopService() {
        stopSelf();
    }

    /**
     * 本地 Binder
     */
    public class LocalBinder extends Binder {
        public ScreenMirroringService getService() {
            return ScreenMirroringService.this;
        }
    }

    /**
     * 启动服务的静态方法
     */
    public static Intent createSenderIntent(Context context, int videoPort, int touchPort) {
        Intent intent = new Intent(context, ScreenMirroringService.class);
        intent.setAction(ACTION_START_SENDER);
        intent.putExtra(EXTRA_VIDEO_PORT, videoPort);
        intent.putExtra(EXTRA_TOUCH_PORT, touchPort);
        return intent;
    }

    public static Intent createReceiverIntent(Context context, int videoPort, int touchPort) {
        Intent intent = new Intent(context, ScreenMirroringService.class);
        intent.setAction(ACTION_START_RECEIVER);
        intent.putExtra(EXTRA_VIDEO_PORT, videoPort);
        intent.putExtra(EXTRA_TOUCH_PORT, touchPort);
        return intent;
    }

    public static Intent createStopIntent(Context context) {
        Intent intent = new Intent(context, ScreenMirroringService.class);
        intent.setAction(ACTION_STOP);
        return intent;
    }
}