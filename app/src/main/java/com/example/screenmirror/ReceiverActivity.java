package com.example.screenmirror;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * 接收端Activity：负责接收网络视频流、解码渲染和触摸事件反控
 * 主要功能：
 * 1. 接收网络视频流（TCP/UDP）
 * 2. 使用MediaCodec进行H.264硬解码
 * 3. 将解码后的视频帧渲染到SurfaceView
 * 4. 采集触摸事件并通过网络发送到发送端
 * 5. 支持WiFi P2P和局域网两种连接方式
 */
public class ReceiverActivity extends AppCompatActivity implements SurfaceHolder.Callback {

    // 日志标签
    private static final String TAG = "ReceiverActivity";
    
    // 权限请求代码
    private static final int REQUEST_CODE_PERMISSIONS = 2001;
    
    // 需要的权限列表
    private static final String[] REQUIRED_PERMISSIONS = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.INTERNET
    };
    
    // 网络相关
    private static final int DEFAULT_PORT = 8888;
    private ServerSocket tcpServerSocket;
    private Socket tcpSocket;
    private InputStream tcpInputStream;
    private DatagramSocket udpSocket;
    private Thread networkThread;
    
    // 解码相关
    private MediaCodec mediaCodec;
    private SurfaceView surfaceView;
    private SurfaceHolder surfaceHolder;
    private FrameLayout videoContainer;
    private TextView tvVideoInfo;
    
    // WiFi P2P相关
    private WifiP2pManager wifiP2pManager;
    private WifiP2pManager.Channel channel;
    private WifiP2pManager.PeerListListener peerListListener;
    
    // UI组件
    private Button btnSearch, btnStartReceive, btnStopReceive, btnBack;
    private TextView tvStatus;
    private ListView lvDevices;
    private ArrayAdapter<String> deviceAdapter;
    private List<String> deviceList = new ArrayList<>();
    
    // 状态变量
    private boolean isReceiving = false;
    private boolean isConnected = false;
    private boolean surfaceReady = false;
    private ConnectionType connectionType = ConnectionType.WIFI_P2P;
    
    // 低延迟优化模块
    private LowLatencyNetwork lowLatencyNetwork;
    private TouchEventSerializer touchEventSerializer;
    
    // 连接类型枚举
    private enum ConnectionType {
        WIFI_P2P,
        LAN_TCP,
        LAN_UDP
    }
    
    // 主线程Handler
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    
    // 触摸事件相关
    private float lastTouchX = -1;
    private float lastTouchY = -1;
    private long lastTouchTime = 0;
    
    // 解码线程
    private Thread decodingThread;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_receiver);
        
        // 初始化视图组件
        initViews();
        
        // 初始化设备列表适配器
        deviceAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, deviceList);
        lvDevices.setAdapter(deviceAdapter);
        
        // 初始化WiFi P2P
        initWifiP2p();
        
        // 检查并请求权限
        checkPermissions();
        
        // 初始化触摸监听
        setupTouchListener();
        
        // 初始化低延迟优化模块
        lowLatencyNetwork = new LowLatencyNetwork();
        touchEventSerializer = new TouchEventSerializer();
        
        // 获取屏幕分辨率用于坐标归一化
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        touchEventSerializer.setScreenResolution(metrics.widthPixels, metrics.heightPixels);
    }
    
    /**
     * 初始化视图组件
     */
    private void initViews() {
        btnSearch = findViewById(R.id.btn_search);
        btnStartReceive = findViewById(R.id.btn_start_receive);
        btnStopReceive = findViewById(R.id.btn_stop_receive);
        btnBack = findViewById(R.id.btn_back);
        tvStatus = findViewById(R.id.tv_status);
        lvDevices = findViewById(R.id.lv_devices);
        surfaceView = findViewById(R.id.sv_video);
        videoContainer = findViewById(R.id.fl_video_container);
        tvVideoInfo = findViewById(R.id.tv_video_info);
        
        // 设置SurfaceHolder回调
        surfaceHolder = surfaceView.getHolder();
        surfaceHolder.addCallback(this);
        surfaceHolder.setFormat(PixelFormat.TRANSPARENT);
        
        // 设置按钮初始状态
        btnStartReceive.setEnabled(false);
        btnStopReceive.setEnabled(false);
        
        // 设置按钮点击监听器
        btnSearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                searchDevices();
            }
        });
        
        btnStartReceive.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startReceiving();
            }
        });
        
        btnStopReceive.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopReceiving();
            }
        });
        
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
                Toast.makeText(ReceiverActivity.this, "选择设备: " + deviceInfo, Toast.LENGTH_SHORT).show();
                // 在实际实现中，这里应该建立连接
                connectToDevice(deviceInfo);
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
                    btnStartReceive.setEnabled(true);
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
     * 搜索附近设备
     */
    private void searchDevices() {
        if (wifiP2pManager == null || channel == null) {
            Toast.makeText(this, "WiFi P2P不可用", Toast.LENGTH_SHORT).show();
            return;
        }
        
        updateStatus("正在搜索设备...");
        
        // 请求发现附近设备
        wifiP2pManager.discoverPeers(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "设备发现已启动");
                updateStatus("设备发现已启动");
                
                // 设置设备列表监听器
                wifiP2pManager.requestPeers(channel, peerListListener);
            }
            
            @Override
            public void onFailure(int reason) {
                Log.e(TAG, "设备发现失败，原因: " + reason);
                updateStatus("设备发现失败");
            }
        });
    }
    
    /**
     * 连接到设备
     */
    private void connectToDevice(String deviceInfo) {
        updateStatus("正在连接到设备...");
        
        // 在实际实现中，这里应该解析设备信息并建立网络连接
        // 由于是示例，我们模拟连接过程
        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                isConnected = true;
                updateStatus("已连接到设备");
                btnStartReceive.setEnabled(true);
                Toast.makeText(ReceiverActivity.this, "连接成功", Toast.LENGTH_SHORT).show();
            }
        }, 1000);
    }
    
    /**
     * 开始接收视频流
     */
    private void startReceiving() {
        if (isReceiving) {
            Toast.makeText(this, "已在接收视频流", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (!isConnected) {
            Toast.makeText(this, "请先连接设备", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (!surfaceReady) {
            Toast.makeText(this, "视频表面未就绪", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // 初始化解码器
        if (!initMediaCodec()) {
            Toast.makeText(this, "解码器初始化失败", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // 启动网络接收
        startNetworkReceiving();
        
        // 启动解码
        isReceiving = true;
        startDecoding();
        
        // 显示视频容器
        videoContainer.setVisibility(View.VISIBLE);
        tvVideoInfo.setVisibility(View.VISIBLE);
        
        // 更新UI状态
        btnStartReceive.setEnabled(false);
        btnStopReceive.setEnabled(true);
        btnSearch.setEnabled(false);
        
        updateStatus("正在接收视频流...");
        
        Toast.makeText(this, "开始接收视频流", Toast.LENGTH_SHORT).show();
    }
    
    /**
     * 停止接收视频流
     */
    private void stopReceiving() {
        if (!isReceiving) {
            Toast.makeText(this, "未在接收视频流", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // 停止解码
        stopDecoding();
        
        // 停止网络接收
        stopNetworkReceiving();
        
        // 更新状态
        isReceiving = false;
        
        // 隐藏视频容器
        videoContainer.setVisibility(View.GONE);
        
        // 更新UI状态
        btnStartReceive.setEnabled(true);
        btnStopReceive.setEnabled(false);
        btnSearch.setEnabled(true);
        
        updateStatus("已停止接收视频流");
        
        Toast.makeText(this, "已停止接收视频流", Toast.LENGTH_SHORT).show();
    }
    
    /**
     * 初始化MediaCodec解码器
     */
    private boolean initMediaCodec() {
        try {
            // 创建MediaFormat配置
            // 注意：在实际应用中，应该从SPS/PPS信息中获取视频参数
            MediaFormat format = MediaFormat.createVideoFormat("video/avc", 1280, 720);
            format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 1024 * 1024); // 1MB
            
            // 创建解码器
            mediaCodec = MediaCodec.createDecoderByType("video/avc");
            mediaCodec.configure(format, surfaceHolder.getSurface(), null, 0);
            
            // 启动解码器
            mediaCodec.start();
            
            Log.d(TAG, "MediaCodec解码器初始化成功");
            return true;
        } catch (IOException e) {
            Log.e(TAG, "MediaCodec解码器初始化失败: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 开始网络接收
     */
    private void startNetworkReceiving() {
        if (networkThread != null && networkThread.isAlive()) {
            return;
        }
        
        networkThread = new Thread(new Runnable() {
            @Override
            public void run() {
                receiveNetworkData();
            }
        });
        networkThread.start();
    }
    
    /**
     * 接收网络数据
     */
    private void receiveNetworkData() {
        try {
            // 在实际实现中，这里应该根据连接类型接收数据
            // 由于是示例，我们模拟接收过程
            byte[] buffer = new byte[1024 * 1024]; // 1MB缓冲区
            
            while (isReceiving) {
                // 模拟接收数据
                Thread.sleep(33); // 约30fps
                
                // 在实际实现中，这里应该从网络接收数据
                // 然后送入解码器
                if (mediaCodec != null && isReceiving) {
                    feedDataToDecoder(buffer, 1024); // 模拟数据
                }
            }
        } catch (InterruptedException e) {
            Log.d(TAG, "网络接收线程被中断");
        } catch (Exception e) {
            Log.e(TAG, "网络接收错误: " + e.getMessage());
        }
    }
    
    /**
     * 将数据送入解码器
     */
    private void feedDataToDecoder(byte[] data, int length) {
        try {
            // 获取输入缓冲区
            int inputBufferId = mediaCodec.dequeueInputBuffer(10000);
            if (inputBufferId >= 0) {
                ByteBuffer inputBuffer = mediaCodec.getInputBuffer(inputBufferId);
                if (inputBuffer != null) {
                    inputBuffer.clear();
                    inputBuffer.put(data, 0, length);
                    
                    // 将数据送入解码器
                    mediaCodec.queueInputBuffer(inputBufferId, 0, length, 
                            System.nanoTime() / 1000, 0);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "送入解码器失败: " + e.getMessage());
        }
    }
    
    /**
     * 开始解码
     */
    private void startDecoding() {
        if (decodingThread != null && decodingThread.isAlive()) {
            return;
        }
        
        decodingThread = new Thread(new Runnable() {
            @Override
            public void run() {
                decodeVideoFrames();
            }
        });
        decodingThread.start();
    }
    
    /**
     * 解码视频帧
     */
    private void decodeVideoFrames() {
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        
        while (isReceiving) {
            try {
                // 获取解码输出缓冲区
                int outputBufferId = mediaCodec.dequeueOutputBuffer(bufferInfo, 10000);
                
                if (outputBufferId >= 0) {
                    // 渲染帧
                    mediaCodec.releaseOutputBuffer(outputBufferId, true);
                    
                    // 更新UI显示解码状态
                    updateDecodingStatus();
                } else if (outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    // 解码格式发生变化
                    MediaFormat newFormat = mediaCodec.getOutputFormat();
                    Log.d(TAG, "解码格式变化: " + newFormat);
                } else if (outputBufferId == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    // 输出缓冲区发生变化
                    Log.d(TAG, "输出缓冲区发生变化");
                }
            } catch (Exception e) {
                Log.e(TAG, "解码错误: " + e.getMessage());
                break;
            }
        }
    }
    
    /**
     * 更新解码状态
     */
    private void updateDecodingStatus() {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                tvVideoInfo.setVisibility(View.GONE);
            }
        });
    }
    
    /**
     * 停止解码
     */
    private void stopDecoding() {
        if (decodingThread != null && decodingThread.isAlive()) {
            isReceiving = false;
            try {
                decodingThread.join(1000);
            } catch (InterruptedException e) {
                Log.e(TAG, "停止解码线程时出错: " + e.getMessage());
            }
        }
        
        if (mediaCodec != null) {
            mediaCodec.stop();
            mediaCodec.release();
            mediaCodec = null;
        }
    }
    
    /**
     * 停止网络接收
     */
    private void stopNetworkReceiving() {
        isReceiving = false;
        
        if (networkThread != null && networkThread.isAlive()) {
            networkThread.interrupt();
            try {
                networkThread.join(1000);
            } catch (InterruptedException e) {
                Log.e(TAG, "停止网络线程时出错: " + e.getMessage());
            }
        }
        
        try {
            if (tcpInputStream != null) {
                tcpInputStream.close();
                tcpInputStream = null;
            }
            
            if (tcpSocket != null) {
                tcpSocket.close();
                tcpSocket = null;
            }
            
            if (tcpServerSocket != null) {
                tcpServerSocket.close();
                tcpServerSocket = null;
            }
            
            if (udpSocket != null) {
                udpSocket.close();
                udpSocket = null;
            }
        } catch (IOException e) {
            Log.e(TAG, "关闭网络连接时出错: " + e.getMessage());
        }
    }
    
    /**
     * 设置触摸监听器
     */
    private void setupTouchListener() {
        surfaceView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (!isReceiving || !isConnected) {
                    return false;
                }
                
                // 处理触摸事件
                handleTouchEvent(event);
                return true;
            }
        });
    }
    
    /**
     * 处理触摸事件
     */
    private void handleTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        float x = event.getX();
        float y = event.getY();
        
        // 转换为发送端屏幕坐标
        // 在实际实现中，需要根据屏幕分辨率进行坐标转换
        float normalizedX = x / surfaceView.getWidth();
        float normalizedY = y / surfaceView.getHeight();
        
        // 发送触摸事件到发送端
        sendTouchEvent(action, normalizedX, normalizedY, event.getPressure());
        
        // 记录最后触摸位置
        lastTouchX = x;
        lastTouchY = y;
        lastTouchTime = System.currentTimeMillis();
    }
    
    /**
     * 发送触摸事件到发送端（优化版：实际通过网络发送）
     */
    private void sendTouchEvent(int action, float x, float y, float pressure) {
        if (!isReceiving || !isConnected) {
            return;
        }
        
        // 创建模拟的MotionEvent（简化版）
        // 在实际实现中，应该使用真实的MotionEvent
        // 这里使用简化的事件数据
        int eventType = convertActionToEventType(action);
        
        // 创建触摸点数据
        TouchEventSerializer.TouchPoint point = new TouchEventSerializer.TouchPoint(
                0, // pointerId
                x, // 归一化X坐标
                y, // 归一化Y坐标
                pressure, // 压力值
                0.0f, // touchMajor
                0.0f, // touchMinor
                0.0f  // orientation
        );
        
        TouchEventSerializer.TouchPoint[] points = {point};
        
        // 创建触摸事件数据
        TouchEventSerializer.TouchEventData touchData = 
                new TouchEventSerializer.TouchEventData(
                        eventType,
                        1, // pointerCount
                        System.currentTimeMillis(),
                        System.nanoTime(),
                        points
                );
        
        // 序列化并发送
        if (touchEventSerializer != null) {
            // 这里应该序列化touchData，但TouchEventSerializer目前没有序列化TouchEventData的方法
            // 需要简化：直接创建字节数组
            ByteBuffer buffer = ByteBuffer.allocate(16);
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            buffer.putInt(eventType);
            buffer.putFloat(x);
            buffer.putFloat(y);
            buffer.putFloat(pressure);
            byte[] touchBytes = buffer.array();
            
            // 通过低延迟网络发送
            if (lowLatencyNetwork != null && lowLatencyNetwork.isConnected()) {
                lowLatencyNetwork.sendTouchData(touchBytes);
                Log.d(TAG, String.format("触摸事件已发送: %s (%.2f, %.2f)", 
                        getActionString(action), x, y));
            } else {
                Log.w(TAG, "低延迟网络未连接，触摸事件未发送");
            }
        }
    }
    
    /**
     * 转换Android action到自定义事件类型
     */
    private int convertActionToEventType(int action) {
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                return TouchEventSerializer.EVENT_DOWN;
            case MotionEvent.ACTION_UP:
                return TouchEventSerializer.EVENT_UP;
            case MotionEvent.ACTION_MOVE:
                return TouchEventSerializer.EVENT_MOVE;
            case MotionEvent.ACTION_CANCEL:
                return TouchEventSerializer.EVENT_CANCEL;
            case MotionEvent.ACTION_POINTER_DOWN:
                return TouchEventSerializer.EVENT_POINTER_DOWN;
            case MotionEvent.ACTION_POINTER_UP:
                return TouchEventSerializer.EVENT_POINTER_UP;
            default:
                return TouchEventSerializer.EVENT_MOVE;
        }
    }
    
    /**
     * 获取action字符串
     */
    private String getActionString(int action) {
        switch (action) {
            case MotionEvent.ACTION_DOWN: return "按下";
            case MotionEvent.ACTION_UP: return "抬起";
            case MotionEvent.ACTION_MOVE: return "移动";
            case MotionEvent.ACTION_CANCEL: return "取消";
            case MotionEvent.ACTION_POINTER_DOWN: return "多点按下";
            case MotionEvent.ACTION_POINTER_UP: return "多点抬起";
            default: return "未知";
        }
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
    
    // SurfaceHolder.Callback接口实现
    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Log.d(TAG, "Surface已创建");
        surfaceReady = true;
    }
    
    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Log.d(TAG, "Surface已改变: " + width + "x" + height);
    }
    
    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.d(TAG, "Surface已销毁");
        surfaceReady = false;
        
        // 如果正在接收，停止接收
        if (isReceiving) {
            stopReceiving();
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
        
        // 停止接收
        if (isReceiving) {
            stopReceiving();
        }
        
        // 清理低延迟网络模块
        if (lowLatencyNetwork != null) {
            lowLatencyNetwork.cleanup();
            lowLatencyNetwork = null;
        }
        
        touchEventSerializer = null;
    }
}