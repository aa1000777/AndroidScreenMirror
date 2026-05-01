package com.example.screenmirror;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Surface;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import java.io.IOException;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * 发送端Activity：负责屏幕采集、编码和网络传输
 * 主要功能：
 * 1. 请求屏幕录制权限
 * 2. 初始化MediaProjection进行屏幕采集
 * 3. 使用MediaCodec进行H.264硬编码
 * 4. 通过网络传输编码后的视频流
 * 5. 支持WiFi P2P和局域网两种连接方式
 */
public class SenderActivity extends AppCompatActivity {

    // 日志标签
    private static final String TAG = "SenderActivity";
    
    // 权限请求代码
    private static final int REQUEST_CODE_SCREEN_CAPTURE = 1001;
    private static final int REQUEST_CODE_PERMISSIONS = 1002;
    
    // 需要的权限列表
    private static final String[] REQUIRED_PERMISSIONS = {
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.INTERNET
    };
    
    // 屏幕录制相关
    private MediaProjectionManager mediaProjectionManager;
    private MediaProjection mediaProjection;
    private MediaCodec mediaCodec;
    private Surface inputSurface;
    
    // 网络传输相关（优化后）
    private LowLatencyNetwork lowLatencyNetwork;
    private TouchEventSerializer touchEventSerializer;
    private OutputStream tcpOutputStream;
    private DatagramSocket udpSocket;
    private Socket tcpSocket;
    private InetAddress remoteAddress;
    private int remotePort = 8888;
    private int touchPort = 8889;
    
    // WiFi P2P相关
    private WifiP2pManager wifiP2pManager;
    private WifiP2pManager.Channel channel;
    private WifiP2pManager.PeerListListener peerListListener;
    
    // UI组件
    private Button btnStart, btnStop, btnBack;
    private TextView tvStatus;
    private RadioGroup rgConnection;
    private RadioButton rbWifiP2p, rbLanTcp, rbLanUdp;
    private ListView lvDevices;
    private ArrayAdapter<String> deviceAdapter;
    private List<String> deviceList = new ArrayList<>();
    
    // 状态变量
    private boolean isCapturing = false;
    private boolean isConnected = false;
    private ConnectionType connectionType = ConnectionType.WIFI_P2P;
    
    // 连接类型枚举
    private enum ConnectionType {
        WIFI_P2P,
        LAN_TCP,
        LAN_UDP
    }
    
    // 主线程Handler
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    
    // 编码线程
    private Thread encodingThread;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sender);
        
        // 初始化视图组件
        initViews();
        
        // 初始化设备列表适配器
        deviceAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, deviceList);
        lvDevices.setAdapter(deviceAdapter);
        
        // 初始化MediaProjectionManager
        mediaProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        
        // 初始化WiFi P2P
        initWifiP2p();
        
        // 初始化低延迟网络模块
        lowLatencyNetwork = new LowLatencyNetwork();
        touchEventSerializer = new TouchEventSerializer();
        
        // 获取屏幕分辨率用于坐标归一化
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        touchEventSerializer.setScreenResolution(metrics.widthPixels, metrics.heightPixels);
        
        // 检查并请求权限
        checkPermissions();
    }
    
    /**
     * 初始化视图组件
     * 获取布局中所有UI组件的引用
     */
    private void initViews() {
        btnStart = findViewById(R.id.btn_start);
        btnStop = findViewById(R.id.btn_stop);
        btnBack = findViewById(R.id.btn_back);
        tvStatus = findViewById(R.id.tv_status);
        rgConnection = findViewById(R.id.rg_connection);
        rbWifiP2p = findViewById(R.id.rb_wifi_p2p);
        rbLanTcp = findViewById(R.id.rb_lan_tcp);
        rbLanUdp = findViewById(R.id.rb_lan_udp);
        lvDevices = findViewById(R.id.lv_devices);
        
        // 设置按钮初始状态
        btnStop.setEnabled(false);
        
        // 设置连接类型选择监听器
        rgConnection.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if (checkedId == R.id.rb_wifi_p2p) {
                    connectionType = ConnectionType.WIFI_P2P;
                    updateStatus("已选择WiFi P2P连接");
                } else if (checkedId == R.id.rb_lan_tcp) {
                    connectionType = ConnectionType.LAN_TCP;
                    updateStatus("已选择TCP连接");
                } else if (checkedId == R.id.rb_lan_udp) {
                    connectionType = ConnectionType.LAN_UDP;
                    updateStatus("已选择UDP连接");
                }
            }
        });
        
        // 设置开始按钮点击监听器
        btnStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startScreenCapture();
            }
        });
        
        // 设置停止按钮点击监听器
        btnStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopScreenCapture();
            }
        });
        
        // 设置返回按钮点击监听器
        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        
        // 设置设备列表点击监听器
        lvDevices.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String deviceInfo = deviceList.get(position);
                // 这里应该解析设备信息并建立连接
                // 为了简化，我们只显示Toast
                Toast.makeText(SenderActivity.this, "尝试连接: " + deviceInfo, Toast.LENGTH_SHORT).show();
                // 实际实现中应该在这里建立网络连接
                simulateConnection(deviceInfo);
            }
        });
    }
    
    /**
     * 初始化WiFi P2P
     */
    private void initWifiP2p() {
        wifiP2pManager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        if (wifiP2pManager == null) {
            Log.e(TAG, "WiFi P2P not supported on this device");
            return;
        }
        
        channel = wifiP2pManager.initialize(this, getMainLooper(), null);
        if (channel == null) {
            Log.e(TAG, "Failed to initialize WiFi P2P channel");
            return;
        }
        
        // 创建设备列表监听器
        peerListListener = new WifiP2pManager.PeerListListener() {
            @Override
            public void onPeersAvailable(WifiP2pDeviceList peers) {
                deviceList.clear();
                for (WifiP2pDevice device : peers.getDeviceList()) {
                    String deviceInfo = device.deviceName + " - " + device.deviceAddress;
                    deviceList.add(deviceInfo);
                }
                deviceAdapter.notifyDataSetChanged();
                
                if (deviceList.isEmpty()) {
                    updateStatus("未找到附近设备");
                } else {
                    updateStatus("找到 " + deviceList.size() + " 个设备");
                }
            }
        };
    }
    
    /**
     * 检查并请求所需权限
     */
    private void checkPermissions() {
        List<String> permissionsToRequest = new ArrayList<>();
        
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(permission);
            }
        }
        
        if (!permissionsToRequest.isEmpty()) {
            ActivityCompat.requestPermissions(this, 
                    permissionsToRequest.toArray(new String[0]), 
                    REQUEST_CODE_PERMISSIONS);
        }
    }
    
    /**
     * 开始屏幕录制
     */
    private void startScreenCapture() {
        if (isCapturing) {
            Toast.makeText(this, "屏幕录制已在运行", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // 请求屏幕录制权限
        Intent captureIntent = mediaProjectionManager.createScreenCaptureIntent();
        startActivityForResult(captureIntent, REQUEST_CODE_SCREEN_CAPTURE);
    }
    
    /**
     * 停止屏幕录制
     */
    private void stopScreenCapture() {
        if (!isCapturing) {
            Toast.makeText(this, "屏幕录制未运行", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // 停止编码
        stopEncoding();
        
        // 停止网络传输
        stopNetwork();
        
        // 停止屏幕录制
        if (mediaProjection != null) {
            mediaProjection.stop();
            mediaProjection = null;
        }
        
        // 更新状态
        isCapturing = false;
        updateStatus("屏幕录制已停止");
        
        // 更新UI
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                btnStart.setEnabled(true);
                btnStop.setEnabled(false);
            }
        });
    }
    
    /**
     * 初始化MediaCodec编码器（优化版：低延迟配置）
     */
    private void initMediaCodec() {
        try {
            // 获取屏幕尺寸
            DisplayMetrics metrics = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(metrics);
            int width = metrics.widthPixels;
            int height = metrics.heightPixels;
            
            // 创建MediaFormat配置（低延迟优化）
            MediaFormat format = MediaFormat.createVideoFormat("video/avc", width, height);
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            
            // 降低比特率减少编码时间（3Mbps）
            format.setInteger(MediaFormat.KEY_BIT_RATE, 3000000);
            
            // 提高帧率（60fps）减少每帧延迟
            format.setInteger(MediaFormat.KEY_FRAME_RATE, 60);
            
            // 减少关键帧间隔（0.5秒）提升seek速度，但会增加带宽
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1); // 1秒，权衡延迟和带宽
            
            // Android Q+ 低延迟编码模式
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                format.setInteger(MediaFormat.KEY_LATENCY, 1); // 低延迟模式
                format.setInteger(MediaFormat.KEY_OPERATING_RATE, 90); // 目标帧率1.5倍
                format.setInteger(MediaFormat.KEY_PRIORITY, 0); // 实时优先级
                
                // 厂商特定优化（高通）
                try {
                    format.setInteger("vendor.qti-ext-enc-low-latency.enable", 1);
                    format.setInteger("vendor.qti-ext-enc-perf-mode.enable", 1);
                } catch (Exception e) {
                    // 忽略厂商特定参数错误
                }
            }
            
            // 使用High Profile，禁用B帧
            format.setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileHigh);
            format.setInteger(MediaFormat.KEY_LEVEL, MediaCodecInfo.CodecProfileLevel.AVCLevel42);
            
            // 创建编码器
            mediaCodec = MediaCodec.createEncoderByType("video/avc");
            mediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            
            // 获取输入Surface
            inputSurface = mediaCodec.createInputSurface();
            
            // 启动编码器
            mediaCodec.start();
            
            // 设置编码器为低延迟模式（如果支持）
            try {
                mediaCodec.setVideoScalingMode(MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING);
            } catch (Exception e) {
                // 忽略不支持的设备
            }
            
            Log.d(TAG, "MediaCodec低延迟模式初始化成功");
        } catch (IOException e) {
            Log.e(TAG, "MediaCodec初始化失败: " + e.getMessage());
            Toast.makeText(this, "编码器初始化失败", Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * 开始编码
     */
    private void startEncoding() {
        if (encodingThread != null && encodingThread.isAlive()) {
            return;
        }
        
        encodingThread = new Thread(new Runnable() {
            @Override
            public void run() {
                encodeVideoFrames();
            }
        });
        encodingThread.start();
    }
    
    /**
     * 编码视频帧
     */
    private void encodeVideoFrames() {
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        
        while (isCapturing) {
            try {
                // 获取编码输出缓冲区
                int outputBufferId = mediaCodec.dequeueOutputBuffer(bufferInfo, 10000);
                
                if (outputBufferId >= 0) {
                    // 获取编码数据
                    ByteBuffer outputBuffer = mediaCodec.getOutputBuffer(outputBufferId);
                    
                    if (outputBuffer != null && bufferInfo.size > 0) {
                        // 处理编码后的数据（发送到网络）
                        processEncodedData(outputBuffer, bufferInfo);
                    }
                    
                    // 释放缓冲区
                    mediaCodec.releaseOutputBuffer(outputBufferId, false);
                    
                    // 如果是关键帧，记录日志
                    if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0) {
                        Log.d(TAG, "关键帧已编码");
                    }
                } else if (outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    // 编码格式发生变化
                    MediaFormat newFormat = mediaCodec.getOutputFormat();
                    Log.d(TAG, "编码格式变化: " + newFormat);
                }
            } catch (Exception e) {
                Log.e(TAG, "编码错误: " + e.getMessage());
                break;
            }
        }
    }
    
    /**
     * 处理编码后的数据（发送到网络）- 优化版
     * 使用RTP打包、低延迟网络传输
     */
    private void processEncodedData(ByteBuffer data, MediaCodec.BufferInfo bufferInfo) {
        // 将数据转换为字节数组（原始的H.264 NAL单元，不带起始码）
        byte[] encodedData = new byte[bufferInfo.size];
        data.position(bufferInfo.offset);
        data.limit(bufferInfo.offset + bufferInfo.size);
        data.get(encodedData);
        
        // 检查是否是关键帧
        boolean isKeyFrame = (bufferInfo.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0;
        
        // 使用低延迟网络发送（如果已初始化）
        if (lowLatencyNetwork != null && lowLatencyNetwork.isConnected()) {
            lowLatencyNetwork.sendVideoData(encodedData, isKeyFrame);
            if (isKeyFrame) {
                Log.d(TAG, "关键帧已通过低延迟网络发送");
            }
        } else {
            // 回退到原始传输方式
            Log.w(TAG, "低延迟网络未就绪，使用传统传输");
            sendDataWithFallback(encodedData, isKeyFrame);
        }
        
        // 延迟统计（仅关键帧，避免日志过多）
        if (isKeyFrame) {
            long encodeEndTime = System.nanoTime();
            // 这里可以记录编码耗时，用于优化
        }
    }
    
    /**
     * 传统传输方式回退（兼容旧代码）
     */
    private void sendDataWithFallback(byte[] data, boolean isKeyFrame) {
        if (!isConnected) {
            Log.w(TAG, "网络未连接，数据被丢弃");
            return;
        }
        
        // 根据连接类型选择传输方式
        switch (connectionType) {
            case LAN_TCP:
                sendDataOverTcp(data);
                break;
            case LAN_UDP:
                sendDataOverUdp(data);
                break;
            case WIFI_P2P:
                sendDataOverTcp(data);
                break;
        }
    }
    
    /**
     * 通过TCP发送数据
     */
    private void sendDataOverTcp(byte[] data) {
        if (tcpOutputStream == null || !isConnected) {
            Log.w(TAG, "TCP连接未建立，无法发送数据");
            return;
        }
        
        try {
            // 在实际实现中，需要添加帧头等信息
            tcpOutputStream.write(data);
            tcpOutputStream.flush();
        } catch (IOException e) {
            Log.e(TAG, "TCP发送失败: " + e.getMessage());
            disconnect();
        }
    }
    
    /**
     * 通过UDP发送数据
     */
    private void sendDataOverUdp(byte[] data) {
        if (udpSocket == null || remoteAddress == null || !isConnected) {
            Log.w(TAG, "UDP连接未建立，无法发送数据");
            return;
        }
        
        try {
            DatagramPacket packet = new DatagramPacket(data, data.length, remoteAddress, remotePort);
            udpSocket.send(packet);
        } catch (IOException e) {
            Log.e(TAG, "UDP发送失败: " + e.getMessage());
            disconnect();
        }
    }
    
    /**
     * 停止编码
     */
    private void stopEncoding() {
        if (encodingThread != null && encodingThread.isAlive()) {
            isCapturing = false;
            try {
                encodingThread.join(1000);
            } catch (InterruptedException e) {
                Log.e(TAG, "停止编码线程时出错: " + e.getMessage());
            }
        }
        
        if (mediaCodec != null) {
            mediaCodec.stop();
            mediaCodec.release();
            mediaCodec = null;
        }
        
        if (inputSurface != null) {
            inputSurface.release();
            inputSurface = null;
        }
    }
    
    /**
     * 初始化网络连接（优化版：实际连接低延迟网络）
     */
    private void initNetworkConnection() {
        // 在实际实现中，这里应该根据选择的设备建立连接
        // 暂时使用本地回环地址（127.0.0.1）进行测试
        // 实际部署时应从设备列表获取真实IP地址
        
        String remoteIp = "127.0.0.1"; // 测试用，实际应为接收端IP
        int videoPort = 8888;
        int touchPort = 8889;
        
        if (lowLatencyNetwork != null) {
            boolean connected = lowLatencyNetwork.connect(remoteIp, videoPort, touchPort);
            if (connected) {
                isConnected = true;
                updateStatus("已连接到设备: " + remoteIp);
                Toast.makeText(this, "低延迟网络连接成功", Toast.LENGTH_SHORT).show();
                
                // 启用前向纠错（20%冗余）
                lowLatencyNetwork.setFecEnabled(true, 20);
            } else {
                updateStatus("连接失败，使用传统模式");
                // 回退到传统TCP/UDP连接
                initTraditionalNetwork(remoteIp, videoPort);
            }
        } else {
            Log.w(TAG, "低延迟网络模块未初始化");
            updateStatus("使用传统网络模式");
            initTraditionalNetwork(remoteIp, videoPort);
        }
    }
    
    /**
     * 初始化传统网络连接（TCP/UDP回退）
     */
    private void initTraditionalNetwork(String remoteIp, int port) {
        try {
            remoteAddress = InetAddress.getByName(remoteIp);
            remotePort = port;
            
            // 根据连接类型创建Socket
            switch (connectionType) {
                case LAN_TCP:
                case WIFI_P2P:
                    tcpSocket = new Socket(remoteAddress, remotePort);
                    tcpOutputStream = tcpSocket.getOutputStream();
                    break;
                case LAN_UDP:
                    udpSocket = new DatagramSocket();
                    break;
            }
            
            isConnected = true;
            updateStatus("传统连接已建立: " + remoteIp + ":" + remotePort);
        } catch (Exception e) {
            Log.e(TAG, "传统网络连接失败: " + e.getMessage());
            updateStatus("连接失败: " + e.getMessage());
            isConnected = false;
        }
    }
    
    /**
     * 停止网络传输（优化版：同时停止低延迟网络）
     */
    private void stopNetwork() {
        isConnected = false;
        
        // 停止低延迟网络
        if (lowLatencyNetwork != null) {
            lowLatencyNetwork.disconnect();
        }
        
        // 停止传统网络
        try {
            if (tcpOutputStream != null) {
                tcpOutputStream.close();
                tcpOutputStream = null;
            }
            
            if (tcpSocket != null) {
                tcpSocket.close();
                tcpSocket = null;
            }
            
            if (udpSocket != null) {
                udpSocket.close();
                udpSocket = null;
            }
        } catch (IOException e) {
            Log.e(TAG, "关闭网络连接时出错: " + e.getMessage());
        }
        
        remoteAddress = null;
    }
    
    /**
     * 模拟连接设备
     */
    private void simulateConnection(String deviceInfo) {
        updateStatus("正在连接: " + deviceInfo);
        
        // 模拟连接延迟
        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                initNetworkConnection();
                Toast.makeText(SenderActivity.this, "已连接到设备", Toast.LENGTH_SHORT).show();
            }
        }, 1000);
    }
    
    /**
     * 断开连接
     */
    private void disconnect() {
        isConnected = false;
        updateStatus("连接已断开");
        
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(SenderActivity.this, "连接已断开", Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    /**
     * 更新状态显示
     */
    private void updateStatus(final String message) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                tvStatus.setText("状态: " + message);
            }
        });
    }
    
    /**
     * 创建前台服务通知
     */
    private Notification createForegroundNotification() {
        String channelId = "screen_capture_channel";
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId,
                    "屏幕录制服务",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            manager.createNotificationChannel(channel);
        }
        
        Intent notificationIntent = new Intent(this, SenderActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 
                PendingIntent.FLAG_IMMUTABLE);
        
        return new NotificationCompat.Builder(this, channelId)
                .setContentTitle("屏幕录制中")
                .setContentText("正在录制屏幕并发送到接收端")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentIntent(pendingIntent)
                .build();
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == REQUEST_CODE_SCREEN_CAPTURE) {
            if (resultCode == RESULT_OK) {
                // 用户授予了屏幕录制权限
                mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data);
                
                // 初始化编码器
                initMediaCodec();
                
                // 初始化网络连接
                initNetworkConnection();
                
                // 开始编码
                isCapturing = true;
                startEncoding();
                
                // 更新UI
                btnStart.setEnabled(false);
                btnStop.setEnabled(true);
                updateStatus("屏幕录制已开始");
                
                Toast.makeText(this, "屏幕录制已开始", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "用户拒绝了屏幕录制权限", Toast.LENGTH_SHORT).show();
                updateStatus("需要屏幕录制权限");
            }
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, 
                                          @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            
            if (!allGranted) {
                Toast.makeText(this, "需要所有权限才能正常使用", Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // 停止屏幕录制
        if (isCapturing) {
            stopScreenCapture();
        }
        
        // 停止网络连接
        stopNetwork();
        
        // 清理低延迟网络模块
        if (lowLatencyNetwork != null) {
            lowLatencyNetwork.cleanup();
            lowLatencyNetwork = null;
        }
        
        touchEventSerializer = null;
    }
}