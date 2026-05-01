package com.screenshare.sdk.Common;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 连接状态机（ConnectionStateMachine）
 *
 * 统一管理 Sender 和 Receiver 的连接状态转换
 * 支持：
 * - 状态转换验证（防止非法状态跃迁）
 * - 事件监听（状态变化通知）
 * - 状态持久化（用于断线重连）
 *
 * 状态定义：
 *   IDLE → DISCOVERING → CONNECTING → CONNECTED → STREAMING
 *                                            ↓
 *                                      CONNECTION_LOST (可重连)
 *                                            ↓
 *                                         IDLE (重连失败)
 */
public class ConnectionStateMachine {

    private static final String TAG = "ConnectionStateMachine";

    /**
     * 连接状态枚举
     */
    public enum State {
        IDLE,                  // 空闲，未开始
        DISCOVERING,           // 正在发现设备
        CONNECTING,            // 正在连接
        CONNECTED,             // 已建立连接（但未开始推流）
        STREAMING,             // 正在推流/接收
        CONNECTION_LOST,       // 连接丢失（可触发重连）
        DISCONNECTING          // 正在断开
    }

    /**
     * 状态转换事件
     */
    public static class TransitionEvent {
        public final State fromState;
        public final State toState;
        public final long timestamp;
        public final String reason;

        public TransitionEvent(State from, State to, long time, String reason) {
            this.fromState = from;
            this.toState = to;
            this.timestamp = time;
            this.reason = reason;
        }
    }

    /**
     * 状态转换监听器
     */
    public interface TransitionListener {
        void onTransition(TransitionEvent event);
    }

    /**
     * 错误回调
     */
    public interface ErrorCallback {
        void onInvalidTransition(State from, State to);
        void onStateMachineError(int errorCode, String message);
    }

    // 当前状态
    private AtomicReference<State> currentState = new AtomicReference<>(State.IDLE);

    // 角色类型
    private final Role role;

    // 监听器列表
    private final CopyOnWriteArrayList<TransitionListener> transitionListeners = new CopyOnWriteArrayList<>();

    // 错误回调
    private ErrorCallback errorCallback;

    // 状态转换历史（用于调试）
    private final List<TransitionEvent> transitionHistory = new ArrayList<>();

    // 角色枚举
    public enum Role {
        SENDER,
        RECEIVER
    }

    public ConnectionStateMachine(Role role) {
        this.role = role;
    }

    /**
     * 获取当前状态
     */
    public State getState() {
        return currentState.get();
    }

    /**
     * 获取角色
     */
    public Role getRole() {
        return role;
    }

    /**
     * 设置错误回调
     */
    public void setErrorCallback(ErrorCallback callback) {
        this.errorCallback = callback;
    }

    /**
     * 添加状态转换监听器
     */
    public void addTransitionListener(TransitionListener listener) {
        if (listener != null) {
            transitionListeners.add(listener);
        }
    }

    /**
     * 移除状态转换监听器
     */
    public void removeTransitionListener(TransitionListener listener) {
        transitionListeners.remove(listener);
    }

    /**
     * 转换到新状态
     *
     * @param newState 目标状态
     * @param reason 转换原因
     * @return 是否成功转换
     */
    public synchronized boolean transitionTo(State newState, String reason) {
        State oldState = currentState.get();

        // 验证转换合法性
        if (!isValidTransition(oldState, newState)) {
            Log.w(TAG, "Invalid transition: " + oldState + " -> " + newState);
            if (errorCallback != null) {
                errorCallback.onInvalidTransition(oldState, newState);
            }
            return false;
        }

        // 执行转换
        currentState.set(newState);

        // 记录历史
        TransitionEvent event = new TransitionEvent(oldState, newState,
            System.currentTimeMillis(), reason);
        transitionHistory.add(event);

        // 最多保留 50 条历史
        if (transitionHistory.size() > 50) {
            transitionHistory.remove(0);
        }

        // 通知监听器
        for (TransitionListener listener : transitionListeners) {
            try {
                listener.onTransition(event);
            } catch (Exception e) {
                Log.e(TAG, "Transition listener error", e);
            }
        }

        Log.i(TAG, "State transition: " + oldState + " -> " + newState + " (" + reason + ")");
        return true;
    }

    /**
     * 验证状态转换是否合法
     */
    private boolean isValidTransition(State from, State to) {
        // 相同状态可以重复转换（但不会实际通知）
        if (from == to) {
            return true;
        }

        switch (from) {
            case IDLE:
                // IDLE 可以转到 DISCOVERING
                return to == State.DISCOVERING || to == State.IDLE;

            case DISCOVERING:
                // DISCOVERING 可以转到 CONNECTING 或 IDLE
                return to == State.CONNECTING || to == State.IDLE || to == State.DISCOVERING;

            case CONNECTING:
                // CONNECTING 可以转到 CONNECTED, IDLE, 或 CONNECTION_LOST
                return to == State.CONNECTED || to == State.IDLE || to == State.CONNECTION_LOST;

            case CONNECTED:
                // CONNECTED 可以转到 STREAMING, CONNECTION_LOST, 或 IDLE
                return to == State.STREAMING || to == State.CONNECTION_LOST || to == State.IDLE;

            case STREAMING:
                // STREAMING 可以转到 CONNECTION_LOST, IDLE
                return to == State.CONNECTION_LOST || to == State.IDLE || to == State.STREAMING;

            case CONNECTION_LOST:
                // CONNECTION_LOST 可以转到 DISCOVERING (重连) 或 IDLE (放弃)
                return to == State.DISCOVERING || to == State.IDLE || to == State.CONNECTING;

            case DISCONNECTING:
                // DISCONNECTING 只能转到 IDLE
                return to == State.IDLE;

            default:
                return false;
        }
    }

    /**
     * 判断是否可以执行某个操作
     */
    public boolean canPerform(String action) {
        State state = currentState.get();
        switch (action) {
            case "startDiscovery":
                return state == State.IDLE;
            case "connect":
                return state == State.DISCOVERING;
            case "startStream":
                return state == State.CONNECTED;
            case "stopStream":
                return state == State.STREAMING;
            case "disconnect":
                return state == State.CONNECTED || state == State.STREAMING || state == State.CONNECTION_LOST;
            case "reconnect":
                return state == State.CONNECTION_LOST;
            default:
                return false;
        }
    }

    /**
     * 获取状态转换历史
     */
    public List<TransitionEvent> getTransitionHistory() {
        return new ArrayList<>(transitionHistory);
    }

    /**
     * 重置状态机到 IDLE
     */
    public synchronized void reset() {
        transitionTo(State.IDLE, "Reset");
    }

    /**
     * 强制设置状态（不验证，用于错误恢复）
     */
    public void forceState(State state) {
        currentState.set(state);
    }

    @Override
    public String toString() {
        return "ConnectionStateMachine{" +
            "role=" + role +
            ", state=" + currentState.get() +
            '}';
    }
}