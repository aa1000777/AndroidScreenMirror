package com.screenshare.sdk.Common;

/**
 * SDK 错误码定义
 *
 * 错误码范围：
 * - 1xxx: 权限/配置错误
 * - 2xxx: 屏幕采集错误
 * - 3xxx: 编码/解码错误
 * - 4xxx: 网络传输错误
 * - 5xxx: WiFi P2P 错误
 */
public class ErrorCode {

    // ========== 1xxx: 权限/配置错误 ==========

    /** 缺少必要权限 */
    public static final int ERR_PERMISSION_DENIED = 1001;

    /** Context 为空 */
    public static final int ERR_CONTEXT_NULL = 1002;

    /** 配置无效 */
    public static final int ERR_CONFIG_INVALID = 1003;

    /** 状态无效（无法执行操作） */
    public static final int ERR_INVALID_STATE = 1004;

    // ========== 2xxx: 屏幕采集错误 ==========

    /** 屏幕采集启动失败 */
    public static final int ERR_CAPTURE_START_FAILED = 2001;

    /** MediaProjection 权限被拒绝 */
    public static final int ERR_MEDIA_PROJECTION_DENIED = 2002;

    /** 屏幕采集器未初始化 */
    public static final int ERR_CAPTURE_NOT_INIT = 2003;

    /** VirtualDisplay 创建失败 */
    public static final int ERR_VIRTUAL_DISPLAY_FAILED = 2004;

    // ========== 3xxx: 编码/解码错误 ==========

    /** 编码器初始化失败 */
    public static final int ERR_ENCODER_INIT_FAILED = 3001;

    /** 解码器初始化失败 */
    public static final int ERR_DECODER_INIT_FAILED = 3002;

    /** 编码超时 */
    public static final int ERR_ENCODER_TIMEOUT = 3003;

    /** 解码超时 */
    public static final int ERR_DECODER_TIMEOUT = 3004;

    /** 不支持的编码格式 */
    public static final int ERR_CODEC_NOT_SUPPORTED = 3005;

    // ========== 4xxx: 网络传输错误 ==========

    /** 网络连接失败 */
    public static final int ERR_CONNECTION_FAILED = 4001;

    /** 网络断开 */
    public static final int ERR_CONNECTION_LOST = 4002;

    /** 网络超时 */
    public static final int ERR_CONNECTION_TIMEOUT = 4003;

    /** UDP 端口绑定失败 */
    public static final int ERR_UDP_BIND_FAILED = 4004;

    /** 网络启动失败 */
    public static final int ERR_NETWORK_START_FAILED = 4005;

    /** RTP 会话创建失败 */
    public static final int ERR_RTP_SESSION_FAILED = 4006;

    // ========== 5xxx: WiFi P2P 错误 ==========

    /** WiFi P2P 不可用 */
    public static final int ERR_WIFI_P2P_UNAVAILABLE = 5001;

    /** WiFi P2P 开启失败 */
    public static final int ERR_WIFI_P2P_ENABLE_FAILED = 5002;

    /** 服务发现失败 */
    public static final int ERR_SERVICE_DISCOVERY_FAILED = 5003;

    /** 连接失败 */
    public static final int ERR_P2P_CONNECT_FAILED = 5004;

    /** 发现者启动失败 */
    public static final int ERR_DISCOVERER_START_FAILED = 5005;

    // ========== 通用错误 ==========

    /** 未知错误 */
    public static final int ERR_UNKNOWN = 9999;

    /**
     * 获取错误码对应的描述
     */
    public static String getDescription(int errorCode) {
        switch (errorCode) {
            // 1xxx
            case ERR_PERMISSION_DENIED: return "Permission denied";
            case ERR_CONTEXT_NULL: return "Context is null";
            case ERR_CONFIG_INVALID: return "Invalid configuration";
            case ERR_INVALID_STATE: return "Invalid state";

            // 2xxx
            case ERR_CAPTURE_START_FAILED: return "Screen capture start failed";
            case ERR_MEDIA_PROJECTION_DENIED: return "MediaProjection denied";
            case ERR_CAPTURE_NOT_INIT: return "Capture not initialized";
            case ERR_VIRTUAL_DISPLAY_FAILED: return "VirtualDisplay failed";

            // 3xxx
            case ERR_ENCODER_INIT_FAILED: return "Encoder init failed";
            case ERR_DECODER_INIT_FAILED: return "Decoder init failed";
            case ERR_ENCODER_TIMEOUT: return "Encoder timeout";
            case ERR_DECODER_TIMEOUT: return "Decoder timeout";
            case ERR_CODEC_NOT_SUPPORTED: return "Codec not supported";

            // 4xxx
            case ERR_CONNECTION_FAILED: return "Connection failed";
            case ERR_CONNECTION_LOST: return "Connection lost";
            case ERR_CONNECTION_TIMEOUT: return "Connection timeout";
            case ERR_UDP_BIND_FAILED: return "UDP bind failed";
            case ERR_NETWORK_START_FAILED: return "Network start failed";
            case ERR_RTP_SESSION_FAILED: return "RTP session failed";

            // 5xxx
            case ERR_WIFI_P2P_UNAVAILABLE: return "WiFi P2P unavailable";
            case ERR_WIFI_P2P_ENABLE_FAILED: return "WiFi P2P enable failed";
            case ERR_SERVICE_DISCOVERY_FAILED: return "Service discovery failed";
            case ERR_P2P_CONNECT_FAILED: return "P2P connect failed";
            case ERR_DISCOVERER_START_FAILED: return "Discoverer start failed";

            default: return "Unknown error: " + errorCode;
        }
    }
}