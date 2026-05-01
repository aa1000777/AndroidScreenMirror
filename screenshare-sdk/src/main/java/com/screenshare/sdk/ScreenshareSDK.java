package com.screenshare.sdk;

import android.content.Context;
import com.screenshare.sdk.capture.ScreenCapturer;
import com.screenshare.sdk.codec.VideoEncoder;
import com.screenshare.sdk.codec.VideoDecoder;
import com.screenshare.sdk.network.RtpSession;
import com.screenshare.sdk.network.UdpChannel;
import com.screenshare.sdk.touch.TouchEncoder;
import com.screenshare.sdk.touch.TouchDecoder;

/**
 * Screenshare SDK 入口类
 *
 * 使用示例：
 * <pre>
 * // 发送端
 * SenderConfig config = new SenderConfig();
 * config.width = 1920;
 * config.height = 1080;
 * config.fps = 60;
 * Sender sender = ScreenshareSDK.createSender(context, config);
 * sender.setEventListener(myListener);
 * sender.startScreenCapture();
 *
 * // 接收端
 * ReceiverConfig rc = new ReceiverConfig();
 * Receiver receiver = ScreenshareSDK.createReceiver(context, rc);
 * receiver.setEventListener(myListener);
 * receiver.startListening();
 * </pre>
 */
public class ScreenshareSDK {

    /** SDK 版本名称 */
    public static final String VERSION_NAME = "1.0.0";

    /** SDK 版本号 */
    public static final int VERSION_CODE = 100;

    /** SDK 版本 major.minor.patch */
    public static final String VERSION = VERSION_NAME;

    private ScreenshareSDK() {
        // 私有构造函数，防止实例化
    }

    /**
     * 获取 SDK 版本信息
     */
    public static String getVersion() {
        return VERSION_NAME;
    }

    /**
     * 获取 SDK 版本码
     */
    public static int getVersionCode() {
        return VERSION_CODE;
    }

    /**
     * 创建发送端实例
     *
     * @param context Android Context
     * @param config  发送端配置（见 {@link SenderConfig}）
     * @return Sender 实例
     */
    public static Sender createSender(Context context, SenderConfig config) {
        if (context == null) {
            throw new IllegalArgumentException("context cannot be null");
        }
        if (config == null) {
            config = new SenderConfig();
        }
        return new Sender(context.getApplicationContext(), config);
    }

    /**
     * 创建接收端实例
     *
     * @param context Android Context
     * @param config  接收端配置（见 {@link ReceiverConfig}）
     * @return Receiver 实例
     */
    public static Receiver createReceiver(Context context, ReceiverConfig config) {
        if (context == null) {
            throw new IllegalArgumentException("context cannot be null");
        }
        if (config == null) {
            config = new ReceiverConfig();
        }
        return new Receiver(context.getApplicationContext(), config);
    }

    /**
     * 检查设备是否支持屏幕采集
     * （MediaProjection 需要 API 21+，本 SDK 要求 minSdk 26）
     */
    public static boolean isScreenCaptureSupported() {
        return true; // minSdk = 26，始终支持
    }

    /**
     * 检查设备是否支持 WiFi P2P
     */
    public static boolean isWifiP2pSupported(Context context) {
        android.content.pm.PackageManager pm = context.getPackageManager();
        return pm.hasSystemFeature(android.content.pm.PackageManager.FEATURE_WIFI_DIRECT);
    }
}
