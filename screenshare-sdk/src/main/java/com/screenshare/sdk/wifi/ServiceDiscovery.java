package com.screenshare.sdk.wifi;

import android.content.Context;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * WiFi P2P 服务发现协议（ServiceDiscovery）
 *
 * 负责：
 * - 广播服务公告（Sender 调用）
 * - 发现服务公告（Receiver 调用）
 * - DNS-SD 风格的服务注册与查询
 *
 * 服务类型：_screenshare._udp
 * TXT 记录包含：device_name, ip_address, video_port, touch_port, version
 */
public class ServiceDiscovery {

    private static final String TAG = "ServiceDiscovery";

    // 服务类型（DNS-SD 风格）
    public static final String SERVICE_TYPE = "_screenshare._udp.";
    public static final int SERVICE_PORT = 8765;  // 服务发现专用端口

    // 服务名称前缀
    private static final String SERVICE_NAME_PREFIX = "ScreenShare_";

    // TXT 记录键
    private static final String TXT_DEVICE_NAME = "name";
    private static final String TXT_IP_ADDRESS = "ip";
    private static final String TXT_VIDEO_PORT = "vport";
    private static final String TXT_TOUCH_PORT = "tport";
    private static final String TXT_VERSION = "ver";

    /**
     * 已发现的服务信息
     */
    public static class ServiceInfo {
        public final String deviceName;
        public final InetAddress ipAddress;
        public final int videoPort;
        public final int touchPort;
        public final String version;
        public final long timestamp;
        public final WifiP2pDevice p2pDevice;  // 可选，关联的 P2P 设备

        public ServiceInfo(String name, InetAddress ip, int vport, int tport, String ver, WifiP2pDevice device) {
            this.deviceName = name;
            this.ipAddress = ip;
            this.videoPort = vport;
            this.touchPort = tport;
            this.version = ver;
            this.timestamp = System.currentTimeMillis();
            this.p2pDevice = device;
        }

        @Override
        public String toString() {
            return "ServiceInfo{" +
                "name=" + deviceName +
                ", ip=" + ipAddress.getHostAddress() +
                ", videoPort=" + videoPort +
                ", touchPort=" + touchPort +
                ", ver=" + version +
                '}';
        }
    }

    /**
     * 服务发现事件回调
     */
    public interface ServiceDiscoveryListener {
        void onServiceRegistered(String serviceName);
        void onServiceUnregistered();
        void onServiceFound(ServiceInfo serviceInfo);
        void onServiceLost(String serviceName);
        void onError(int errorCode, String message);
    }

    private Context context;
    private ServiceDiscoveryListener listener;
    private Handler mainHandler;

    // 角色
    private final boolean isBroadcaster;  // true = Sender 广播服务, false = Receiver 发现服务

    // 服务信息（广播者用）
    private String serviceName;
    private String deviceName;
    private int videoPort;
    private int touchPort;

    // 运行状态
    private volatile boolean isRunning = false;
    private Thread discoveryThread;
    private DatagramSocket socket;

    // 已发现的服务列表
    private CopyOnWriteArrayList<ServiceInfo> discoveredServices = new CopyOnWriteArrayList<>();

    // 服务过期时间（毫秒）
    private static final long SERVICE_TTL_MS = 10000;

    public ServiceDiscovery(Context context, boolean isBroadcaster) {
        this.context = context.getApplicationContext();
        this.isBroadcaster = isBroadcaster;
        this.mainHandler = new Handler(Looper.getMainLooper());

        // 生成服务名
        this.serviceName = SERVICE_NAME_PREFIX + android.os.Build.MODEL + "_" + System.currentTimeMillis() % 10000;
    }

    public void setListener(ServiceDiscoveryListener listener) {
        this.listener = listener;
    }

    /**
     * 设置服务信息（Sender 调用）
     */
    public void setServiceInfo(String deviceName, int videoPort, int touchPort) {
        this.deviceName = deviceName;
        this.videoPort = videoPort;
        this.touchPort = touchPort;
    }

    /**
     * 设置服务名（用于标识自身）
     */
    public void setServiceName(String name) {
        this.serviceName = name;
    }

    /**
     * 启动服务发现/广播
     */
    public synchronized void start() {
        if (isRunning) {
            Log.w(TAG, "ServiceDiscovery already running");
            return;
        }

        isRunning = true;

        // 启动发现线程
        discoveryThread = new Thread(this::discoveryLoop, "ServiceDiscoveryThread");
        discoveryThread.start();

        Log.i(TAG, "ServiceDiscovery started as " + (isBroadcaster ? "broadcaster" : "listener"));
    }

    /**
     * 停止服务发现/广播
     */
    public synchronized void stop() {
        if (!isRunning) {
            return;
        }

        isRunning = false;

        if (socket != null) {
            socket.close();
            socket = null;
        }

        if (discoveryThread != null) {
            try {
                discoveryThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            discoveryThread = null;
        }

        discoveredServices.clear();

        Log.i(TAG, "ServiceDiscovery stopped");
    }

    /**
     * 广播服务公告（Sender 调用）
     */
    public void broadcastService() {
        if (!isRunning || !isBroadcaster) {
            return;
        }

        sendServiceAnnouncement();
    }

    /**
     * 发送服务公告包
     */
    private void sendServiceAnnouncement() {
        try {
            byte[] data = buildServiceAnnouncement();

            // 广播到本地网络
            InetAddress broadcastAddr = InetAddress.getByName("255.255.255.255");
            DatagramPacket packet = new DatagramPacket(data, data.length, broadcastAddr, SERVICE_PORT);
            socket.send(packet);

            Log.d(TAG, "Service announcement sent: " + serviceName);

        } catch (Exception e) {
            Log.e(TAG, "Failed to send service announcement", e);
        }
    }

    /**
     * 构建服务公告数据
     */
    private byte[] buildServiceAnnouncement() {
        try {
            ByteBuffer buffer = ByteBuffer.allocate(512);

            // 协议头
            buffer.put((byte) 0x53);  // 'S'
            buffer.put((byte) 0x44);  // 'D'
            buffer.put((byte) 0x01);  // 版本 1

            // 服务名
            byte[] nameBytes = serviceName.getBytes("UTF-8");
            buffer.put((byte) nameBytes.length);
            buffer.put(nameBytes);

            // 设备名
            byte[] deviceBytes = deviceName != null ? deviceName.getBytes("UTF-8") : new byte[0];
            buffer.put((byte) deviceBytes.length);
            buffer.put(deviceBytes);

            // IP 地址
            try {
                // 获取本机 IP
                java.net.NetworkInterface ni = java.net.NetworkInterface.getNetworkInterfaces().nextElement();
                java.net.InetAddress addr = ni.getInetAddresses().nextElement();
                byte[] ipBytes = addr.getAddress();
                buffer.put(ipBytes);
            } catch (Exception e) {
                buffer.put(new byte[]{0, 0, 0, 0});  // fallback
            }

            // 端口信息
            buffer.putShort((short) videoPort);
            buffer.putShort((short) touchPort);

            // 版本
            byte[] verBytes = "1.0".getBytes("UTF-8");
            buffer.put((byte) verBytes.length);
            buffer.put(verBytes);

            byte[] result = new byte[buffer.position()];
            System.arraycopy(buffer.array(), 0, result, 0, result.length);
            return result;
        } catch (Exception e) {
            Log.e(TAG, "Failed to build service announcement", e);
            return new byte[0];
        }
    }

    /**
     * 解析服务公告
     */
    private ServiceInfo parseServiceAnnouncement(byte[] data, InetAddress from) {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(data);

            // 检查协议头
            if (buffer.get() != 0x53 || buffer.get() != 0x44 || buffer.get() != 0x01) {
                return null;
            }

            // 解析服务名
            int nameLen = buffer.get() & 0xFF;
            byte[] nameBytes = new byte[nameLen];
            buffer.get(nameBytes);
            String name = new String(nameBytes, "UTF-8");

            // 解析设备名
            int deviceLen = buffer.get() & 0xFF;
            byte[] deviceBytes = new byte[deviceLen];
            buffer.get(deviceBytes);
            String deviceNameStr = new String(deviceBytes, "UTF-8");

            // 解析 IP
            byte[] ipBytes = new byte[4];
            buffer.get(ipBytes);
            InetAddress ip = InetAddress.getByAddress(ipBytes);

            // 解析端口
            int videoPort = buffer.getShort() & 0xFFFF;
            int touchPort = buffer.getShort() & 0xFFFF;

            // 解析版本
            int verLen = buffer.get() & 0xFF;
            byte[] verBytes = new byte[verLen];
            buffer.get(verBytes);
            String version = new String(verBytes, "UTF-8");

            return new ServiceInfo(deviceNameStr, ip, videoPort, touchPort, version, null);

        } catch (Exception e) {
            Log.e(TAG, "Failed to parse service announcement", e);
            return null;
        }
    }

    /**
     * 发现循环
     */
    private void discoveryLoop() {
        try {
            socket = new DatagramSocket(SERVICE_PORT);
            socket.setBroadcast(true);
            socket.setReuseAddress(true);

            byte[] buffer = new byte[1024];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

            while (isRunning) {
                try {
                    socket.receive(packet);

                    int length = packet.getLength();
                    if (length > 0) {
                        byte[] data = new byte[length];
                        System.arraycopy(buffer, 0, data, 0, length);

                        InetAddress fromAddress = packet.getAddress();

                        if (isBroadcaster) {
                            // 广播模式：只发不收
                        } else {
                            // 监听模式：解析收到的公告
                            ServiceInfo info = parseServiceAnnouncement(data, fromAddress);
                            if (info != null) {
                                handleServiceFound(info);
                            }
                        }
                    }

                } catch (IOException e) {
                    if (isRunning) {
                        // 可能 socket 被关闭
                        break;
                    }
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Discovery loop error", e);
            notifyError(-1, "Discovery failed: " + e.getMessage());
        }
    }

    /**
     * 处理发现的服务
     */
    private void handleServiceFound(ServiceInfo info) {
        // 检查是否已存在
        boolean found = false;
        for (int i = 0; i < discoveredServices.size(); i++) {
            ServiceInfo existing = discoveredServices.get(i);
            if (existing.deviceName.equals(info.deviceName)) {
                // 更新
                discoveredServices.set(i, info);
                found = true;
                break;
            }
        }

        if (!found) {
            discoveredServices.add(info);
            Log.i(TAG, "Service found: " + info);

            mainHandler.post(() -> {
                if (listener != null) {
                    listener.onServiceFound(info);
                }
            });
        }
    }

    /**
     * 获取已发现的服务列表
     */
    public CopyOnWriteArrayList<ServiceInfo> getDiscoveredServices() {
        // 清理过期服务
        long now = System.currentTimeMillis();
        for (ServiceInfo info : discoveredServices) {
            if (now - info.timestamp > SERVICE_TTL_MS) {
                discoveredServices.remove(info);
            }
        }
        return discoveredServices;
    }

    /**
     * 获取本机 IP 地址
     */
    public static String getLocalIpAddress() {
        try {
            java.util.Enumeration<java.net.NetworkInterface> interfaces =
                java.net.NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                java.net.NetworkInterface ni = interfaces.nextElement();
                java.util.Enumeration<java.net.InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    java.net.InetAddress addr = addresses.nextElement();
                    if (!addr.isLoopbackAddress() && addr instanceof java.net.Inet4Address) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to get local IP", e);
        }
        return "0.0.0.0";
    }

    /**
     * 开始定期广播服务（Sender 用）
     */
    public void startBroadcasting(long intervalMs) {
        if (!isBroadcaster) {
            return;
        }

        new Thread(() -> {
            while (isRunning) {
                sendServiceAnnouncement();
                try {
                    Thread.sleep(intervalMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "ServiceBroadcastThread").start();
    }

    private void notifyError(int code, String message) {
        if (listener != null) {
            mainHandler.post(() -> listener.onError(code, message));
        }
    }

    @Override
    protected void finalize() throws Throwable {
        stop();
        super.finalize();
    }
}