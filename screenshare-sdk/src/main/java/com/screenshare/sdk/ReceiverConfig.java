package com.screenshare.sdk;

/**
 * 接收端（Receiver）配置
 */
public class ReceiverConfig {
    /** 期望接收的视频分辨率 */
    public int width = 1920;

    /** 期望接收的视频高度 */
    public int height = 1080;

    /** 视频解码器类型（应与发送端匹配） */
    public VideoCodecType videoCodec = VideoCodecType.H264_HARDWARE;

    /** 是否接收音频 */
    public boolean withAudio = false;

    /** 监听端口（UDP） */
    public int listenVideoPort = 8888;

    /** 触摸事件监听端口（UDP） */
    public int listenTouchPort = 8889;

    /** 最大重连次数 */
    public int maxReconnect = 3;

    /** 连接超时（毫秒） */
    public int connectTimeout = 5000;

    /** 接收缓冲区大小（字节） */
    public int receiveBufferSize = 512 * 1024;

    /** 是否启用低延迟模式 */
    public boolean lowLatencyMode = true;

    /** 帧队列最大长度 */
    public int maxFrameQueue = 2;

    public enum VideoCodecType {
        H264_HARDWARE,
        H265_HARDWARE,
        VP8,
        VP9
    }
}