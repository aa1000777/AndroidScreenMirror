package com.screenshare.sdk.touch;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.graphics.Path;
import android.os.Build;
import android.util.Log;
import android.view.Display;
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
     *
     * @param timestamp 事件时间戳
     * @param action MotionEvent.ACTION_*
     * @param x 触摸 X 坐标（相对于原视频）
     * @param y 触摸 Y 坐标（相对于原视频）
     * @param scaleX X 方向缩放比例
     * @param scaleY Y 方向缩放比例
     * @return 是否注入成功
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
                eventListener.onTouchInjected(timestamp, action, scaledX, scaledY, false);
            }
        }

        return result;
    }

    private long getGestureDuration(int action) {
        switch (action) {
            case MotionEventAction.ACTION_DOWN:
                return 50;
            case MotionEventAction.ACTION_MOVE:
                return 16;
            case MotionEventAction.ACTION_UP:
                return 100;
            default:
                return 50;
        }
    }

    /**
     * 注入点击
     */
    public boolean injectClick(float x, float y, float scaleX, float scaleY) {
        boolean down = injectTouchEvent(System.currentTimeMillis(), MotionEventAction.ACTION_DOWN, x, y, scaleX, scaleY);
        if (down) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        return injectTouchEvent(System.currentTimeMillis(), MotionEventAction.ACTION_UP, x, y, scaleX, scaleY);
    }

    /**
     * 注入滑动
     */
    public boolean injectSwipe(float x1, float y1, float x2, float y2, float scaleX, float scaleY) {
        // 简化：注入两个点
        boolean down = injectTouchEvent(System.currentTimeMillis(), MotionEventAction.ACTION_DOWN, x1, y1, scaleX, scaleY);
        if (!down) return false;

        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        return injectTouchEvent(System.currentTimeMillis(), MotionEventAction.ACTION_UP, x2, y2, scaleX, scaleY);
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

    // MotionEvent action 常量（避免引用完整的 MotionEvent）
    public static class MotionEventAction {
        public static final int ACTION_DOWN = 0;
        public static final int ACTION_UP = 1;
        public static final int ACTION_MOVE = 2;
        public static final int ACTION_CANCEL = 3;
    }
}
