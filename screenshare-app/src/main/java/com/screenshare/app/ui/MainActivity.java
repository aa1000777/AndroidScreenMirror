package com.screenshare.app.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.screenshare.app.R;

/**
 * 主界面 - 选择发送端或接收端
 */
public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_PERMISSIONS = 1001;

    private Button btnSender;
    private Button btnReceiver;
    private Button btnSettings;
    private TextView tvStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        checkPermissions();
    }

    private void initViews() {
        btnSender = findViewById(R.id.btn_sender);
        btnReceiver = findViewById(R.id.btn_receiver);
        btnSettings = findViewById(R.id.btn_settings);
        tvStatus = findViewById(R.id.tv_status);

        // 检查 WiFi P2P 是否可用
        WifiP2pManager manager = (WifiP2pManager) getSystemService(WIFI_P2P_SERVICE);
        if (manager == null) {
            tvStatus.setText("⚠️ WiFi P2P 不可用");
            btnSender.setEnabled(false);
            btnReceiver.setEnabled(false);
        } else {
            tvStatus.setText("✅ WiFi P2P 就绪");
        }

        btnSender.setOnClickListener(v -> {
            if (checkPermissions()) {
                startSender();
            }
        });

        btnReceiver.setOnClickListener(v -> {
            if (checkPermissions()) {
                startReceiver();
            }
        });

        btnSettings.setOnClickListener(v -> {
            startActivity(new Intent(this, SettingsActivity.class));
        });
    }

    private boolean checkPermissions() {
        String[] permissions = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.INTERNET,
            Manifest.permission.RECORD_AUDIO
        };

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions = new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.CHANGE_WIFI_STATE,
                Manifest.permission.INTERNET,
                Manifest.permission.NEARBY_WIFI_DEVICES,
                Manifest.permission.RECORD_AUDIO
            };
        }

        boolean allGranted = true;
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                break;
            }
        }

        if (!allGranted) {
            ActivityCompat.requestPermissions(this, permissions, REQUEST_PERMISSIONS);
            return false;
        }

        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_PERMISSIONS) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                Toast.makeText(this, "权限已授予", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "部分权限被拒绝，功能可能受限", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void startSender() {
        Intent intent = new Intent(this, SenderActivity.class);
        startActivity(intent);
    }

    private void startReceiver() {
        Intent intent = new Intent(this, ReceiverActivity.class);
        startActivity(intent);
    }
}
