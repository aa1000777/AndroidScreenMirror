package com.screenshare.sdk.wifi;

import android.content.Context;
import android.content.Intent;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.screenshare.sdk.Common.ErrorCode;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

/**
 * WiFi P2P 服务发现与连接管理
 *
 * 负责：
 * - 发现附近投屏接收端
 * - 建立 P2P 连接
 * - 获取 Group Owner 地址
 */
public class P2pConnectionManager {

    private static final String TAG = "P2pConnectionManager";

    private final Context context;
    private WifiP2pManager.Channel channel;
    private WifiP2pManager wifiP2pManager;

    private boolean isDiscovering = false;
    private boolean isConnected = false;
    private EventListener eventListener;
    private Handler mainHandler;

    private List<WifiP2pDevice> discoveredDevices = new ArrayList<>();

    public interface EventListener {
        void onStateChanged(int state);
        void onDevicesFound(List<WifiP2pDevice> devices);
        void onConnected(InetAddress groupOwnerAddress, String deviceName);
        void onDisconnected();
        void onError(int errorCode, String message);
    }

    public P2pConnectionManager(Context context) {
        this.context = context.getApplicationContext();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public void setEventListener(EventListener listener) {
        this.eventListener = listener;
    }

    /**
     * 初始化 WiFi P2P
     */
    public synchronized void initialize() {
        if (wifiP2pManager != null) {
            return;
        }

        try {
            wifiP2pManager = (WifiP2pManager)
                context.getSystemService(Context.WIFI_P2P_SERVICE);

            if (wifiP2pManager == null) {
                notifyError(ErrorCode.ERR_WIFI_P2P_UNAVAILABLE, "WiFi P2P not available");
                return;
            }

            channel = wifiP2pManager.initialize(context, Looper.getMainLooper(), () -> {
                // Channel disconnected
                isConnected = false;
                isDiscovering = false;
                if (eventListener != null) {
                    eventListener.onDisconnected();
                }
            });

            Log.d(TAG, "WiFi P2P initialized");

        } catch (Exception e) {
            notifyError(ErrorCode.ERR_WIFI_P2P_ENABLE_FAILED, "Failed to initialize WiFi P2P: " + e.getMessage());
        }
    }

    /**
     * 开始发现设备
     */
    public synchronized void startDiscovery() {
        if (wifiP2pManager == null || channel == null) {
            initialize();
        }

        if (isDiscovering) {
            return;
        }

        try {
            wifiP2pManager.discoverPeers(channel, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    isDiscovering = true;
                    Log.d(TAG, "Discovery started");
                    if (eventListener != null) {
                        eventListener.onStateChanged(DISCOVERY_STARTED);
                    }
                }

                @Override
                public void onFailure(int reason) {
                    isDiscovering = false;
                    notifyError(ErrorCode.ERR_DISCOVERER_START_FAILED,
                        "Discovery failed: " + getFailureReason(reason));
                }
            });

        } catch (Exception e) {
            notifyError(ErrorCode.ERR_DISCOVERER_START_FAILED, e.getMessage());
        }
    }

    /**
     * 停止发现
     */
    public synchronized void stopDiscovery() {
        if (wifiP2pManager == null || channel == null) {
            return;
        }

        try {
            wifiP2pManager.stopPeerDiscovery(channel, null);
            isDiscovering = false;

        } catch (Exception e) {
            // ignore
        }
    }

    /**
     * 请求设备列表
     */
    public synchronized void requestPeers() {
        if (wifiP2pManager == null || channel == null) {
            return;
        }

        wifiP2pManager.requestPeers(channel, peers -> {
            if (peers != null) {
                discoveredDevices = new ArrayList<>(peers.getDeviceList());
                if (eventListener != null) {
                    eventListener.onDevicesFound(discoveredDevices);
                }
            }
        });
    }

    /**
     * 连接到指定设备
     */
    public synchronized void connect(WifiP2pDevice device) {
        if (wifiP2pManager == null || channel == null) {
            initialize();
        }

        try {
            WifiP2pConfig config = new WifiP2pConfig();
            config.deviceAddress = device.deviceAddress;

            notifyState(CONNECTING);

            wifiP2pManager.connect(channel, config, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    Log.d(TAG, "Connect initiated to " + device.deviceName);
                }

                @Override
                public void onFailure(int reason) {
                    notifyError(ErrorCode.ERR_P2P_CONNECT_FAILED,
                        "Connect failed: " + getFailureReason(reason));
                }
            });

        } catch (Exception e) {
            notifyError(ErrorCode.ERR_P2P_CONNECT_FAILED, e.getMessage());
        }
    }

    /**
     * 断开连接
     */
    public synchronized void disconnect() {
        if (wifiP2pManager == null || channel == null) {
            return;
        }

        try {
            wifiP2pManager.removeGroup(channel, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    isConnected = false;
                    notifyState(DISCONNECTED);
                }

                @Override
                public void onFailure(int reason) {
                    Log.d(TAG, "Disconnect failed: " + reason);
                }
            });
        } catch (Exception e) {
            // ignore
        }
    }

    /**
     * 请求连接信息
     */
    public synchronized void requestConnectionInfo() {
        if (wifiP2pManager == null || channel == null) {
            return;
        }

        wifiP2pManager.requestConnectionInfo(channel, info -> {
            if (info != null && info.groupFormed) {
                isConnected = true;
                InetAddress groupOwner = info.groupOwnerAddress;
                String deviceName = info.groupOwnerAddress != null
                    ? info.groupOwnerAddress.getHostName()
                    : "Unknown";

                if (eventListener != null) {
                    eventListener.onConnected(groupOwner, deviceName);
                }
            }
        });
    }

    /**
     * 创建 P2P 组（作为 Group Owner）
     */
    public synchronized void createGroup() {
        if (wifiP2pManager == null || channel == null) {
            initialize();
        }

        try {
            wifiP2pManager.createGroup(channel, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    Log.d(TAG, "Group created");
                    notifyState(GROUP_CREATED);
                }

                @Override
                public void onFailure(int reason) {
                    notifyError(ErrorCode.ERR_WIFI_P2P_ENABLE_FAILED,
                        "Create group failed: " + getFailureReason(reason));
                }
            });

        } catch (Exception e) {
            notifyError(ErrorCode.ERR_WIFI_P2P_ENABLE_FAILED, e.getMessage());
        }
    }

    /**
     * 获取是否正在发现
     */
    public boolean isDiscovering() {
        return isDiscovering;
    }

    /**
     * 获取是否已连接
     */
    public boolean isConnected() {
        return isConnected;
    }

    private void notifyState(int state) {
        if (eventListener != null) {
            mainHandler.post(() -> eventListener.onStateChanged(state));
        }
    }

    private void notifyError(int code, String message) {
        Log.e(TAG, message);
        if (eventListener != null) {
            mainHandler.post(() -> eventListener.onError(code, message));
        }
    }

    private String getFailureReason(int reason) {
        switch (reason) {
            case WifiP2pManager.P2P_UNSUPPORTED: return "P2P_UNSUPPORTED";
            case WifiP2pManager.BUSY: return "BUSY";
            default: return "ERROR";
        }
    }

    // Constants for state
    public static final int DISCOVERY_STARTED = 0;
    public static final int DISCOVERY_STOPPED = 1;
    public static final int CONNECTING = 2;
    public static final int CONNECTED = 3;
    public static final int DISCONNECTED = 4;
    public static final int GROUP_CREATED = 5;
}
