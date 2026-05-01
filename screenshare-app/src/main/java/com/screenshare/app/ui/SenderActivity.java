package com.screenshare.app.ui;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.media.projection.MediaProjectionManager;
import android.content.IntentFilter;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.screenshare.app.R;
import com.screenshare.sdk.ScreenshareSDK;
import com.screenshare.sdk.Sender;
import com.screenshare.sdk.SenderConfig;
import com.screenshare.sdk.Common.SenderState;
import com.screenshare.sdk.wifi.P2pBroadcastReceiver;
import com.screenshare.sdk.wifi.P2pConnectionManager;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

/**
 * 发送端 Activity
 *
 * 1. 请求 MediaProjection 权限
 * 2. 初始化 WiFi P2P 发现
 * 3. 连接到接收端
 * 4. 开始屏幕采集和推流
 */
public class SenderActivity extends Activity {

    private static final String TAG = "SenderActivity";
    private static final int REQUEST_MEDIA_PROJECTION = 2001;

    private TextView tvStatus;
    private TextView tvState;
    private Button btnStop;
    private ListView lvDevices;
    private Button btnDiscover;

    private Sender sender;
    private SenderConfig config;
    private P2pConnectionManager p2pManager;
    private P2pBroadcastReceiver p2pReceiver;
    private IntentFilter p2pFilter;

    private List<WifiP2pDevice> devices = new ArrayList<>();
    private ArrayAdapter<String> deviceAdapter;

    private boolean isCapturing = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 全屏横屏
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        );
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_sender);

        initViews();
        initP2p();
        requestMediaProjection();
    }

    private void initViews() {
        tvStatus = findViewById(R.id.tv_status);
        tvState = findViewById(R.id.tv_state);
        btnStop = findViewById(R.id.btn_stop);
        lvDevices = findViewById(R.id.lv_devices);
        btnDiscover = findViewById(R.id.btn_discover);

        btnStop.setOnClickListener(v -> stopCapture());
        btnDiscover.setOnClickListener(v -> startDiscovery());

        deviceAdapter = new ArrayAdapter<>(this,
            android.R.layout.simple_list_item_1,
            new ArrayList<>());
        lvDevices.setAdapter(deviceAdapter);

        lvDevices.setOnItemClickListener((parent, view, position, id) -> {
            if (position < devices.size()) {
                connectToDevice(devices.get(position));
            }
        });

        updateState("等待权限...");
    }

    private void initP2p() {
        p2pManager = new P2pConnectionManager(this);
        p2pManager.setEventListener(new P2pConnectionManager.EventListener() {
            @Override
            public void onStateChanged(int state) {
                runOnUiThread(() -> {
                    switch (state) {
                        case P2pConnectionManager.DISCOVERY_STARTED:
                            tvStatus.setText("🔍 搜索中...");
                            btnDiscover.setEnabled(false);
                            break;
                        case P2pConnectionManager.DISCOVERY_STOPPED:
                            tvStatus.setText("📡 待命中");
                            btnDiscover.setEnabled(true);
                            break;
                        case P2pConnectionManager.CONNECTING:
                            tvStatus.setText("🔗 连接中...");
                            break;
                    }
                });
            }

            @Override
            public void onDevicesFound(List<WifiP2pDevice> foundDevices) {
                runOnUiThread(() -> {
                    devices.clear();
                    devices.addAll(foundDevices);

                    deviceAdapter.clear();
                    for (WifiP2pDevice device : foundDevices) {
                        deviceAdapter.add(device.deviceName + " (" + device.deviceAddress + ")");
                    }
                    deviceAdapter.notifyDataSetChanged();

                    tvStatus.setText("📱 发现 " + foundDevices.size() + " 个设备");
                });
            }

            @Override
            public void onConnected(InetAddress groupOwnerAddress, String deviceName) {
                runOnUiThread(() -> {
                    tvStatus.setText("✅ 已连接: " + deviceName);
                    Toast.makeText(SenderActivity.this,
                        "连接到 " + deviceName, Toast.LENGTH_SHORT).show();

                    if (sender != null) {
                        String addr = groupOwnerAddress != null ?
                            groupOwnerAddress.getHostAddress() : "192.168.49.1";
                        sender.connect(addr);
                    }
                });
            }

            @Override
            public void onDisconnected() {
                runOnUiThread(() -> {
                    tvStatus.setText("❌ 连接断开");
                    updateState("IDLE");
                });
            }

            @Override
            public void onError(int errorCode, String message) {
                runOnUiThread(() -> {
                    Toast.makeText(SenderActivity.this,
                        "错误: " + message, Toast.LENGTH_LONG).show();
                });
            }
        });

        p2pManager.initialize();

        p2pReceiver = new P2pBroadcastReceiver(p2pManager, null);
        p2pFilter = new IntentFilter();
        p2pFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
    }

    private void requestMediaProjection() {
        MediaProjectionManager mgr = (MediaProjectionManager)
            getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        if (mgr != null) {
            startActivityForResult(mgr.createScreenCaptureIntent(), REQUEST_MEDIA_PROJECTION);
        } else {
            Toast.makeText(this, "无法获取屏幕采集权限", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            if (resultCode == RESULT_OK && data != null) {
                startCapture(resultCode, data);
            } else {
                Toast.makeText(this, "权限被拒绝", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    private void startCapture(int resultCode, Intent data) {
        config = new SenderConfig();
        config.width = 1920;
        config.height = 1080;
        config.fps = 60;
        config.videoPort = 8888;
        config.touchPort = 8889;

        sender = ScreenshareSDK.createSender(this, config);
        sender.setEventListener(new Sender.EventListener() {
            @Override
            public void onStateChanged(SenderState state) {
                runOnUiThread(() -> updateState(state.name()));
            }

            @Override
            public void onError(int errorCode, String message) {
                runOnUiThread(() -> {
                    Toast.makeText(SenderActivity.this,
                        "错误 " + errorCode + ": " + message, Toast.LENGTH_LONG).show();
                });
            }

            @Override
            public void onConnected(String peerAddress) {
                runOnUiThread(() -> {
                    tvStatus.setText("📺 推流中 → " + peerAddress);
                    isCapturing = true;
                });
            }

            @Override
            public void onDisconnected() {
                runOnUiThread(() -> {
                    tvStatus.setText("❌ 连接断开");
                    isCapturing = false;
                });
            }

            @Override
            public void onFrameSent(long timestamp, int size) {
                // 统计信息，可忽略
            }
        });

        // 启动采集
        sender.startCapture();
        isCapturing = true;
        updateState("CAPTURING");

        // 启动 P2P 发现
        p2pManager.startDiscovery();
    }

    private void startDiscovery() {
        if (p2pManager != null) {
            p2pManager.startDiscovery();
        }
    }

    private void connectToDevice(WifiP2pDevice device) {
        if (p2pManager != null && sender != null) {
            p2pManager.connect(device);
        }
    }

    private void stopCapture() {
        isCapturing = false;

        if (sender != null) {
            sender.stopCapture();
            sender.release();
            sender = null;
        }

        if (p2pManager != null) {
            p2pManager.stopDiscovery();
            p2pManager.disconnect();
        }

        finish();
    }

    private void updateState(String state) {
        tvState.setText("状态: " + state);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (p2pReceiver != null) {
            registerReceiver(p2pReceiver, p2pFilter);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (p2pReceiver != null) {
            unregisterReceiver(p2pReceiver);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopCapture();
    }
}
