package com.screenshare.app.ui;

import android.app.Activity;
import android.content.Context;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.screenshare.app.R;
import com.screenshare.sdk.ScreenshareSDK;
import com.screenshare.sdk.Receiver;
import com.screenshare.sdk.ReceiverConfig;
import com.screenshare.sdk.Common.ReceiverState;
import com.screenshare.sdk.codec.VideoDecoder;
import com.screenshare.sdk.touch.TouchDecoder;
import com.screenshare.sdk.touch.TouchInjectorService;
import com.screenshare.sdk.wifi.P2pBroadcastReceiver;
import com.screenshare.sdk.wifi.P2pConnectionManager;

import java.net.InetAddress;
import java.util.List;

/**
 * 接收端 Activity
 *
 * 1. 创建 WiFi P2P Group（作为 Group Owner）
 * 2. 监听 UDP 端口接收视频流
 * 3. 解码并渲染视频
 * 4. 接收触摸事件并注入
 */
public class ReceiverActivity extends Activity implements TextureView.SurfaceTextureListener {

    private static final String TAG = "ReceiverActivity";

    private TextView tvStatus;
    private TextView tvState;
    private Button btnStop;
    private TextureView textureView;
    private Surface surface;

    private Receiver receiver;
    private ReceiverConfig config;
    private VideoDecoder videoDecoder;
    private TouchDecoder touchDecoder;
    private P2pConnectionManager p2pManager;
    private P2pBroadcastReceiver p2pReceiver;
    private android.content.IntentFilter p2pFilter;

    private Handler mainHandler;
    private boolean isListening = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 全屏横屏
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        );
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_receiver);

        mainHandler = new Handler(Looper.getMainLooper());

        initViews();
        initP2p();
    }

    private void initViews() {
        tvStatus = findViewById(R.id.tv_status);
        tvState = findViewById(R.id.tv_state);
        btnStop = findViewById(R.id.btn_stop);
        textureView = findViewById(R.id.texture_view);

        btnStop.setOnClickListener(v -> stopListening());

        textureView.setSurfaceTextureListener(this);

        updateState("LISTENING");
        tvStatus.setText("📡 等待投屏...");
    }

    private void initP2p() {
        p2pManager = new P2pConnectionManager(this);
        p2pManager.setEventListener(new P2pConnectionManager.EventListener() {
            @Override
            public void onStateChanged(int state) {
                runOnUiThread(() -> {
                    switch (state) {
                        case P2pConnectionManager.GROUP_CREATED:
                            tvStatus.setText("📡 监听中，等待发送端...");
                            break;
                        case P2pConnectionManager.CONNECTED:
                            tvStatus.setText("✅ 发送端已连接");
                            break;
                        case P2pConnectionManager.DISCONNECTED:
                            tvStatus.setText("❌ 连接断开");
                            break;
                    }
                });
            }

            @Override
            public void onDevicesFound(List<WifiP2pDevice> devices) {
                // 接收端不主动发现
            }

            @Override
            public void onConnected(InetAddress groupOwnerAddress, String deviceName) {
                runOnUiThread(() -> {
                    tvStatus.setText("✅ 发送端已连接: " + deviceName);
                    Toast.makeText(ReceiverActivity.this,
                        "发送端已连接", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onDisconnected() {
                runOnUiThread(() -> {
                    tvStatus.setText("❌ 连接断开");
                });
            }

            @Override
            public void onError(int errorCode, String message) {
                runOnUiThread(() -> {
                    Toast.makeText(ReceiverActivity.this,
                        "错误: " + message, Toast.LENGTH_LONG).show();
                });
            }
        });

        p2pManager.initialize();

        // 创建 Group（接收端作为 Group Owner）
        p2pManager.createGroup();

        p2pReceiver = new P2pBroadcastReceiver(p2pManager, null);
        p2pFilter = new android.content.IntentFilter();
        p2pFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
    }

    private void startListening() {
        if (isListening) {
            return;
        }

        config = new ReceiverConfig();
        config.width = 1920;
        config.height = 1080;
        config.listenVideoPort = 8888;
        config.listenTouchPort = 8889;

        receiver = ScreenshareSDK.createReceiver(this, config);
        receiver.setEventListener(new Receiver.EventListener() {
            @Override
            public void onStateChanged(ReceiverState state) {
                runOnUiThread(() -> updateState(state.name()));
            }

            @Override
            public void onError(int errorCode, String message) {
                runOnUiThread(() -> {
                    Toast.makeText(ReceiverActivity.this,
                        "错误 " + errorCode + ": " + message, Toast.LENGTH_LONG).show();
                });
            }

            @Override
            public void onFrameReceived(long timestamp, int size) {
                // 统计信息
            }

            @Override
            public void onTouchEventReceived(long timestamp, int action, float x, float y) {
                // 注入触摸事件
                TouchInjectorService.injectTouch(timestamp, action, x, y, 1f, 1f);
            }

            @Override
            public void onConnected(String senderAddress) {
                runOnUiThread(() -> {
                    tvStatus.setText("📺 接收中 ← " + senderAddress);
                    isListening = true;
                });
            }

            @Override
            public void onDisconnected() {
                runOnUiThread(() -> {
                    tvStatus.setText("❌ 连接断开");
                    isListening = false;
                });
            }
        });

        // 如果 Surface 已准备好，启动接收
        if (surface != null) {
            startReceiving();
        }
    }

    private void startReceiving() {
        if (receiver != null && surface != null) {
            receiver.startListening();
            isListening = true;
            updateState("STREAMING");
            tvStatus.setText("📺 接收中...");
        }
    }

    private void stopListening() {
        isListening = false;

        if (receiver != null) {
            receiver.stopListening();
            receiver.release();
            receiver = null;
        }

        if (p2pManager != null) {
            p2pManager.disconnect();
        }

        finish();
    }

    private void updateState(String state) {
        tvState.setText("状态: " + state);
    }

    // TextureView.SurfaceTextureListener
    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
        surface = new Surface(surfaceTexture);
        Log.d(TAG, "Surface ready: " + width + "x" + height);

        if (receiver != null) {
            startReceiving();
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int width, int height) {
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
        if (surface != null) {
            surface.release();
            surface = null;
        }
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
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
        stopListening();

        if (surface != null) {
            surface.release();
            surface = null;
        }
    }
}
