package com.example.screenmirror;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

/**
 * 屏幕录制服务
 * 作为前台服务运行，确保屏幕录制在后台持续进行
 * 注：此服务为简化实现，实际功能在SenderActivity中完成
 */
public class ScreenCaptureService extends Service {

    // 服务ID和通知渠道ID
    private static final int NOTIFICATION_ID = 1001;
    private static final String CHANNEL_ID = "screen_capture_service_channel";
    
    // 服务状态
    private boolean isRunning = false;

    @Override
    public void onCreate() {
        super.onCreate();
        LogUtil.d("ScreenCaptureService", "服务已创建");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        LogUtil.d("ScreenCaptureService", "服务已启动");
        
        // 创建前台通知
        createForegroundNotification();
        
        // 标记服务为前台服务
        startForeground(NOTIFICATION_ID, createNotification());
        
        isRunning = true;
        
        // 如果服务被杀死，重新启动
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        // 此服务不支持绑定
        return null;
    }

    /**
     * 创建前台通知
     */
    private void createForegroundNotification() {
        // 创建通知渠道（Android 8.0+需要）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "屏幕录制服务",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("屏幕录制服务正在运行");
            
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    /**
     * 创建通知
     */
    private Notification createNotification() {
        // 创建点击通知后打开的Intent
        Intent notificationIntent = new Intent(this, SenderActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 
                PendingIntent.FLAG_IMMUTABLE);

        // 构建通知
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("屏幕录制中")
                .setContentText("正在录制屏幕并发送视频流")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true) // 设置为持续显示
                .build();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        LogUtil.d("ScreenCaptureService", "服务已销毁");
        isRunning = false;
        
        // 停止前台服务
        stopForeground(true);
    }

    /**
     * 检查服务是否正在运行
     */
    public boolean isRunning() {
        return isRunning;
    }
}

/**
 * 日志工具类
 */
class LogUtil {
    public static void d(String tag, String message) {
        android.util.Log.d(tag, message);
    }
    
    public static void e(String tag, String message) {
        android.util.Log.e(tag, message);
    }
}