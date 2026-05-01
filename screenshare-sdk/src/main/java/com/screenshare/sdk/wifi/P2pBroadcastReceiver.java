package com.screenshare.sdk.wifi;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;
import android.util.Log;

/**
 * WiFi P2P 广播接收器
 *
 * 接收 WiFi P2P 状态变化广播并通知 P2pConnectionManager
 */
public class P2pBroadcastReceiver extends BroadcastReceiver {

    private static final String TAG = "P2pBroadcastReceiver";

    private P2pConnectionManager manager;
    private P2pConnectionManager.EventListener listener;

    public P2pBroadcastReceiver(P2pConnectionManager manager, P2pConnectionManager.EventListener listener) {
        this.manager = manager;
        this.listener = listener;
    }

    // WiFi P2P broadcast actions
    private static final String ACTION_P2P_STATE_CHANGED = "android.net.wifi.p2p.STATE_CHANGED";
    private static final String ACTION_P2P_PEERS_CHANGED = "android.net.wifi.p2p.PEERS_CHANGED";
    private static final String ACTION_P2P_CONNECTION_CHANGED = "android.net.wifi.p2p.CONNECTION_STATE_CHANGE";
    private static final String ACTION_P2P_THIS_DEVICE_CHANGED = "android.net.wifi.p2p.THIS_DEVICE_CHANGED";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        if (ACTION_P2P_STATE_CHANGED.equals(action)) {
            int state = intent.getIntExtra("wifi_p2p_state",
                WifiP2pManager.WIFI_P2P_STATE_DISABLED);
            handleStateChanged(state);

        } else if (ACTION_P2P_PEERS_CHANGED.equals(action)) {
            // Peers changed, request them
            if (manager != null) {
                manager.requestPeers();
            }

        } else if (ACTION_P2P_CONNECTION_CHANGED.equals(action)) {
            // Connection changed
            if (manager != null) {
                manager.requestConnectionInfo();
            }

        } else if (ACTION_P2P_THIS_DEVICE_CHANGED.equals(action)) {
            // This device changed
            WifiP2pDevice device = intent.getParcelableExtra("wifi_p2p_device");
            if (device != null) {
                Log.d(TAG, "Device name: " + device.deviceName + ", status: " + getDeviceStatus(device.status));
            }
        }
    }

    private void handleStateChanged(int state) {
        if (listener == null) {
            return;
        }

        switch (state) {
            case WifiP2pManager.WIFI_P2P_STATE_ENABLED:
                Log.d(TAG, "WiFi P2P is enabled");
                break;
            case WifiP2pManager.WIFI_P2P_STATE_DISABLED:
                Log.d(TAG, "WiFi P2P is disabled");
                listener.onStateChanged(P2pConnectionManager.DISCONNECTED);
                break;
        }
    }

    private String getDeviceStatus(int status) {
        switch (status) {
            case WifiP2pDevice.AVAILABLE: return "Available";
            case WifiP2pDevice.INVITED: return "Invited";
            case WifiP2pDevice.CONNECTED: return "Connected";
            case WifiP2pDevice.FAILED: return "Failed";
            case WifiP2pDevice.UNAVAILABLE: return "Unavailable";
            default: return "Unknown";
        }
    }
}
