package com.screenshare.sdk;

/**
 * 发送端（Sender）配置
 */
public class SenderConfig {
    /** 视频分辨率 - 宽度 */
    public int width = 1920;

    /** 视频分辨率 - 高度 */
    public int height = 1080;

    /** 帧率 FPS */
    public int fps = 60;

    /** 视频码率 bps（0 = 自动） */
    public int videoBitrate = 0;

    /** 关键帧间隔（秒） */
    public int keyFrameInterval = 2;

    /** 视频编码器类型 */
    public VideoCodecType videoCodec = VideoCodecType.H264_HARDWARE;

    /** 是否包含音频 */
    public boolean withAudio = false;

    /** 音频码率 */
    public int audioBitrate = 128000;

    /** 视频端口（UDP） */
    public int videoPort = 8888;

    /** 触摸端口（UDP） */
    public int touchPort = 8889;

    /** 最大重连次数 */
    public int maxReconnect = 3;

    /** 连接超时（毫秒） */
    public int connectTimeout = 5000;

    /** 是否启用性能模式（降低质量换取性能） */
    public boolean performanceMode = false;

    /** 性能预设 */
    public PerformancePreset performancePreset = PerformancePreset.BALANCED;

    /** 发送缓冲区大小（字节） */
    public int sendBufferSize = 512 * 1024;

    public enum VideoCodecType {
        H264_HARDWARE,   // 硬件 H264（MediaCodec）
        H265_HARDWARE,   // 硬件 H265（MediaCodec）
        VP8,             // 软件 VP8
        VP9              // 软件 VP9
    }

    public enum PerformancePreset {
        LOW_LATENCY,     // 超低延迟（延迟 < 50ms）
        BALANCED,        // 平衡（延迟 ~100ms）
        HIGH_QUALITY     // 高质量（延迟 > 200ms）
    }
}