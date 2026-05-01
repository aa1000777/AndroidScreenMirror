package com.example.screenmirror;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * WiFi P2P服务
 * 负责管理WiFi直连功能，包括设备发现、连接建立等
 */
public class WifiP2pService extends Service {

    // 日志标签
    private static final String TAG = "WifiP2pService";
    
    // WiFi P2P管理器
    private WifiP2pManager wifiP2pManager;
    private WifiP2pManager.Channel channel;
    
    // 广播接收器
    private BroadcastReceiver receiver;
    
    // 设备列表
    private List<WifiP2pDevice> deviceList = new ArrayList<>();
    
    // 服务绑定器
    private final IBinder binder = new LocalBinder();
    
    // 服务状态
    private boolean isDiscovering = false;
    
    // 监听器接口
    public interface WifiP2pListener {
        void onDeviceListUpdated(List<WifiP2pDevice> devices);
        void onConnectionInfoAvailable(WifiP2pInfo info);
        void onDiscoveryStarted();
        void onDiscoveryStopped();
        void onConnectionChanged(boolean isConnected);
    }
    
    private WifiP2pListener listener;
    
    /**
     * 本地绑定器类
     */
    public class LocalBinder extends Binder {
        public WifiP2pService getService() {
            return WifiP2pService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "WiFi P2P服务已创建");
        
        // 初始化WiFi P2P管理器
        wifiP2pManager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        if (wifiP2pManager == null) {
            Log.e(TAG, "设备不支持WiFi P2P");
            return;
        }
        
        // 初始化频道
        channel = wifiP2pManager.initialize(this, getMainLooper(), null);
        if (channel == null) {
            Log.e(TAG, "无法初始化WiFi P2P频道");
            return;
        }
        
        // 初始化广播接收器
        initBroadcastReceiver();
    }
    
    /**
     * 初始化广播接收器
     */
    private void initBroadcastReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        filter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        filter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        filter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);
        
        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                handleWifiP2pBroadcast(intent);
            }
        };
        
        registerReceiver(receiver, filter);
        Log.d(TAG, "广播接收器已注册");
    }
    
    /**
     * 处理WiFi P2P广播
     */
    private void handleWifiP2pBroadcast(Intent intent) {
        String action = intent.getAction();
        
        if (action == null) {
            return;
        }
        
        switch (action) {
            case WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION:
                // WiFi P2P状态变化
                int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);
                boolean isEnabled = state == WifiP2pManager.WIFI_P2P_STATE_ENABLED;
                Log.d(TAG, "WiFi P2P状态: " + (isEnabled ? "已启用" : "已禁用"));
                break;
                
            case WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION:
                // 对等设备列表变化
                requestPeers();
                break;
                
            case WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION:
                // 连接状态变化
                boolean isConnected = intent.getBooleanExtra(WifiP2pManager.EXTRA_CONNECTION_STATE, false);
                Log.d(TAG, "连接状态: " + (isConnected ? "已连接" : "已断开"));
                
                if (listener != null) {
                    listener.onConnectionChanged(isConnected);
                }
                
                if (isConnected) {
                    // 获取连接信息
                    wifiP2pManager.requestConnectionInfo(channel, new WifiP2pManager.ConnectionInfoListener() {
                        @Override
                        public void onConnectionInfoAvailable(WifiP2pInfo info) {
                            if (listener != null) {
                                listener.onConnectionInfoAvailable(info);
                            }
                        }
                    });
                }
                break;
                
            case WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION:
                // 本设备信息变化
                WifiP2pDevice device = intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE);
                if (device != null) {
                    Log.d(TAG, "本设备: " + device.deviceName);
                }
                break;
        }
    }
    
    /**
     * 请求对等设备列表
     */
    private void requestPeers() {
        if (wifiP2pManager == null || channel == null) {
            return;
        }
        
        wifiP2pManager.requestPeers(channel, new WifiP2pManager.PeerListListener() {
            @Override
            public void onPeersAvailable(WifiP2pDeviceList peers) {
                deviceList.clear();
                deviceList.addAll(peers.getDeviceList());
                
                Log.d(TAG, "发现 " + deviceList.size() + " 个设备");
                
                if (listener != null) {
                    listener.onDeviceListUpdated(deviceList);
                }
            }
        });
    }
    
    /**
     * 开始发现设备
     */
    public void startDiscovery() {
        if (wifiP2pManager == null || channel == null) {
            Log.e(TAG, "WiFi P2P未初始化");
            return;
        }
        
        if (isDiscovering) {
            Log.d(TAG, "已经在发现设备");
            return;
        }
        
        wifiP2pManager.discoverPeers(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "设备发现已启动");
                isDiscovering = true;
                
                if (listener != null) {
                    listener.onDiscoveryStarted();
                }
            }
            
            @Override
            public void onFailure(int reason) {
                Log.e(TAG, "设备发现失败，原因: " + reason);
                isDiscovering = false;
                
                if (listener != null) {
                    listener.onDiscoveryStopped();
                }
            }
        });
    }
    
    /**
     * 停止发现设备
     */
    public void stopDiscovery() {
        if (!isDiscovering) {
            return;
        }
        
        // 注意：WiFi P2P没有直接的停止发现API
        // 在实际实现中，可能需要停止服务或取消注册接收器
        isDiscovering = false;
        
        if (listener != null) {
            listener.onDiscoveryStopped();
        }
        
        Log.d(TAG, "设备发现已停止");
    }
    
    /**
     * 连接到设备
     */
    public void connectToDevice(WifiP2pDevice device) {
        if (wifiP2pManager == null || channel == null || device == null) {
            Log.e(TAG, "无法连接到设备，参数无效");
            return;
        }
        
        Log.d(TAG, "尝试连接到设备: " + device.deviceName);
        
        // 在实际实现中，这里需要配置连接参数
        // 由于这是示例，我们只打印日志
        // wifiP2pManager.connect(channel, config, listener);
    }
    
    /**
     * 设置监听器
     */
    public void setListener(WifiP2pListener listener) {
        this.listener = listener;
    }
    
    /**
     * 获取设备列表
     */
    public List<WifiP2pDevice> getDeviceList() {
        return new ArrayList<>(deviceList);
    }
    
    /**
     * 检查是否正在发现设备
     */
    public boolean isDiscovering() {
        return isDiscovering;
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "服务已绑定");
        return binder;
    }
    
    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "服务已解绑");
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "WiFi P2P服务已销毁");
        
        // 取消注册广播接收器
        if (receiver != null) {
            unregisterReceiver(receiver);
        }
        
        // 停止设备发现
        stopDiscovery();
    }
}