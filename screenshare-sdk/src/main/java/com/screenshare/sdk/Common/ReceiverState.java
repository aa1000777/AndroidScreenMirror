package com.screenshare.sdk.Common;

/**
 * 接收端状态机
 */
public enum ReceiverState {
    /** 监听中（等待连接） */
    LISTENING,

    /** 已接受连接 */
    ACCEPTED,

    /** 正在接收流 */
    STREAMING
}