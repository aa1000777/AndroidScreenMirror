package com.example.screenmirror;

/**
 * 应用常量定义类
 * 包含网络端口、编码参数、配置常量等
 */
public class Constants {
    
    // 网络配置
    public static final int DEFAULT_TCP_PORT = 8888;
    public static final int DEFAULT_UDP_PORT = 8889;
    public static final int BUFFER_SIZE = 1024 * 1024; // 1MB
    public static final int SOCKET_TIMEOUT = 5000; // 5秒
    
    // 视频编码配置
    public static final String VIDEO_MIME_TYPE = "video/avc";
    public static final int VIDEO_BIT_RATE = 5000000; // 5 Mbps
    public static final int VIDEO_FRAME_RATE = 30;
    public static final int VIDEO_I_FRAME_INTERVAL = 1; // 1秒
    public static final int VIDEO_COLOR_FORMAT = 0x00000015; // COLOR_FormatSurface
    
    // 屏幕采集配置
    public static final int SCREEN_DENSITY = 1; // 默认密度
    
    // 触摸事件配置
    public static final int TOUCH_EVENT_DOWN = 0;
    public static final int TOUCH_EVENT_UP = 1;
    public static final int TOUCH_EVENT_MOVE = 2;
    public static final int TOUCH_EVENT_CANCEL = 3;
    
    // 消息类型
    public static final int MSG_TYPE_VIDEO_FRAME = 100;
    public static final int MSG_TYPE_TOUCH_EVENT = 101;
    public static final int MSG_TYPE_CONNECTION = 102;
    public static final int MSG_TYPE_DISCONNECTION = 103;
    
    // 错误代码
    public static final int ERROR_PERMISSION_DENIED = 1001;
    public static final int ERROR_SCREEN_CAPTURE_FAILED = 1002;
    public static final int ERROR_ENCODER_FAILED = 1003;
    public static final int ERROR_DECODER_FAILED = 1004;
    public static final int ERROR_NETWORK_FAILED = 1005;
    public static final int ERROR_CONNECTION_FAILED = 1006;
    
    // 请求代码
    public static final int REQUEST_CODE_SCREEN_CAPTURE = 10001;
    public static final int REQUEST_CODE_PERMISSIONS = 10002;
    public static final int REQUEST_CODE_WIFI_P2P = 10003;
    
    // 共享首选项键名
    public static final String PREF_KEY_LAST_CONNECTION_TYPE = "last_connection_type";
    public static final String PREF_KEY_LAST_DEVICE_ADDRESS = "last_device_address";
    public static final String PREF_KEY_LAST_DEVICE_NAME = "last_device_name";
    
    // 通知相关
    public static final String NOTIFICATION_CHANNEL_ID = "screen_mirror_channel";
    public static final int NOTIFICATION_ID_SCREEN_CAPTURE = 20001;
    public static final int NOTIFICATION_ID_NETWORK = 20002;
    
    // 日志标签
    public static final String TAG_SENDER = "ScreenMirrorSender";
    public static final String TAG_RECEIVER = "ScreenMirrorReceiver";
    public static final String TAG_NETWORK = "ScreenMirrorNetwork";
    public static final String TAG_ENCODER = "ScreenMirrorEncoder";
    public static final String TAG_DECODER = "ScreenMirrorDecoder";
    
    /**
     * 获取连接类型名称
     */
    public static String getConnectionTypeName(int type) {
        switch (type) {
            case 0:
                return "WiFi P2P";
            case 1:
                return "TCP";
            case 2:
                return "UDP";
            default:
                return "未知";
        }
    }
    
    /**
     * 获取错误信息
     */
    public static String getErrorMessage(int errorCode) {
        switch (errorCode) {
            case ERROR_PERMISSION_DENIED:
                return "权限被拒绝";
            case ERROR_SCREEN_CAPTURE_FAILED:
                return "屏幕录制失败";
            case ERROR_ENCODER_FAILED:
                return "视频编码失败";
            case ERROR_DECODER_FAILED:
                return "视频解码失败";
            case ERROR_NETWORK_FAILED:
                return "网络错误";
            case ERROR_CONNECTION_FAILED:
                return "连接失败";
            default:
                return "未知错误";
        }
    }
}