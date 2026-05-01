package com.screenshare.sdk.Common;

/**
 * 发送端状态机
 */
public enum SenderState {
    /** 空闲状态 */
    IDLE,

    /** 正在采集屏幕 */
    CAPTURING,

    /** 正在发现设备 */
    DISCOVERING,

    /** 正在连接 */
    CONNECTING,

    /** 正在推流 */
    STREAMING,

    /** 连接丢失（可重连） */
    CONNECTION_LOST
}