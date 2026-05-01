package com.screenshare.sdk.touch;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.graphics.Path;
import android.os.Build;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.WindowManager;

import com.screenshare.sdk.Common.ErrorCode;

/**
 * 触摸注入服务（AccessibilityService 实现）
 *
 * 默认触摸注入方式
 * 通过 AccessibilityService 注入触摸事件到系统
 */
public class TouchInjectorService extends AccessibilityService {

    private static final String TAG = "TouchInjectorService";
    private static TouchInjectorService instance;

    private EventListener eventListener;
    private int displayWidth = 1080;
    private int displayHeight = 1920;
    private boolean isInjecting = false;

    public interface EventListener {
        void onTouchInjected(long timestamp, int action, float x, float y, boolean success);
        void onError(int errorCode, String message);
    }

    public static TouchInjectorService getInstance() {
        return instance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;

        // 获取屏幕尺寸
        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (wm != null) {
            Display display = wm.getDefaultDisplay();
            displayWidth = display.getWidth();
            displayHeight = display.getHeight();
        }

        Log.d(TAG, "TouchInjectorService created, display: " + displayWidth + "x" + displayHeight);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        instance = null;
        Log.d(TAG, "TouchInjectorService destroyed");
    }

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        Log.d(TAG, "TouchInjectorService connected");
    }

    public void setEventListener(EventListener listener) {
        this.eventListener = listener;
    }

    /**
     * 设置屏幕尺寸（用于坐标转换）
     */
    public void setScreenSize(int width, int height) {
        this.displayWidth = width;
        this.displayHeight = height;
    }

    /**
     * 注入触摸事件
     */
    public boolean injectTouchEvent(long timestamp, int action, float x, float y, float scaleX, float scaleY) {
        if (instance == null) {
            return false;
        }

        // 缩放坐标
        float scaledX = x * scaleX;
        float scaledY = y * scaleY;

        // 确保坐标在屏幕范围内
        final float finalScaledX = Math.max(0, Math.min(scaledX, displayWidth - 1));
        final float finalScaledY = Math.max(0, Math.min(scaledY, displayHeight - 1));

        // 构建 GestureDescription
        GestureDescription.Builder builder = new GestureDescription.Builder();
        Path path = new Path();
        path.moveTo(finalScaledX, finalScaledY);

        builder.addStroke(new GestureDescription.StrokeDescription(
            path, 0, getGestureDuration(action), false
        ));

        GestureDescription gesture = builder.build();

        boolean result = dispatchGesture(gesture, new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                Log.d(TAG, "Touch injected: (" + finalScaledX + ", " + finalScaledY + ")");
                if (eventListener != null) {
                    eventListener.onTouchInjected(timestamp, action, finalScaledX, finalScaledY, true);
                }
            }

            @Override
            public void onCancelled(GestureDescription gestureDescription) {
                Log.w(TAG, "Touch injection cancelled");
                if (eventListener != null) {
                    eventListener.onTouchInjected(timestamp, action, finalScaledX, finalScaledY, false);
                }
            }
        }, null);

        if (!result) {
            Log.e(TAG, "Failed to dispatch touch gesture");
            if (eventListener != null) {
                eventListener.onTouchInjected(timestamp, action, finalScaledX, finalScaledY, false);
            }
        }

        return result;
    }

    /**
     * 静态方法：从外部调用触摸注入
     */
    public static boolean injectTouch(long timestamp, int action, float x, float y, float scaleX, float scaleY) {
        if (instance == null) {
            Log.w(TAG, "TouchInjectorService not running");
            return false;
        }
        return instance.injectTouchEvent(timestamp, action, x, y, scaleX, scaleY);
    }

    /**
     * 注入点击
     */
    public boolean injectClick(float x, float y, float scaleX, float scaleY) {
        boolean down = injectTouchEvent(System.currentTimeMillis(), MotionEvent.ACTION_DOWN, x, y, scaleX, scaleY);
        if (down) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        return injectTouchEvent(System.currentTimeMillis(), MotionEvent.ACTION_UP, x, y, scaleX, scaleY);
    }

    /**
     * 注入滑动
     */
    public boolean injectSwipe(float x1, float y1, float x2, float y2, float scaleX, float scaleY) {
        boolean down = injectTouchEvent(System.currentTimeMillis(), MotionEvent.ACTION_DOWN, x1, y1, scaleX, scaleY);
        if (!down) return false;

        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        return injectTouchEvent(System.currentTimeMillis(), MotionEvent.ACTION_UP, x2, y2, scaleX, scaleY);
    }

    private long getGestureDuration(int action) {
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                return 50;
            case MotionEvent.ACTION_MOVE:
                return 16;
            case MotionEvent.ACTION_UP:
                return 100;
            default:
                return 50;
        }
    }

    @Override
    public void onAccessibilityEvent(android.view.accessibility.AccessibilityEvent event) {
        // 不需要处理
    }

    @Override
    public void onInterrupt() {
        Log.w(TAG, "AccessibilityService interrupted");
        if (eventListener != null) {
            eventListener.onError(0, "AccessibilityService interrupted");
        }
    }
}
