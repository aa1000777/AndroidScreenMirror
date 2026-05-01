package com.example.screenmirror;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 网络传输服务
 * 负责管理TCP/UDP网络连接和数据传输
 * 支持两种工作模式：发送模式和接收模式
 */
public class NetworkService extends Service {

    // 日志标签
    private static final String TAG = "NetworkService";
    
    // 线程池
    private ExecutorService executorService;
    
    // TCP相关
    private ServerSocket tcpServerSocket;
    private Socket tcpSocket;
    private InputStream tcpInputStream;
    private OutputStream tcpOutputStream;
    
    // UDP相关
    private DatagramSocket udpSocket;
    private InetAddress remoteAddress;
    private int remotePort = 8888;
    private int localPort = 8888;
    
    // 服务状态
    private boolean isRunning = false;
    private boolean isConnected = false;
    private NetworkMode networkMode = NetworkMode.NONE;
    
    // 绑定器
    private final IBinder binder = new LocalBinder();
    
    // 监听器接口
    public interface NetworkListener {
        void onConnected(String address, int port);
        void onDisconnected();
        void onDataReceived(byte[] data, int length);
        void onError(String errorMessage);
    }
    
    private NetworkListener listener;
    
    /**
     * 网络模式枚举
     */
    public enum NetworkMode {
        NONE,
        TCP_SERVER,    // TCP服务器模式（接收端）
        TCP_CLIENT,    // TCP客户端模式（发送端）
        UDP_SENDER,    // UDP发送模式
        UDP_RECEIVER   // UDP接收模式
    }

    /**
     * 本地绑定器类
     */
    public class LocalBinder extends Binder {
        public NetworkService getService() {
            return NetworkService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "网络服务已创建");
        
        // 创建线程池
        executorService = Executors.newFixedThreadPool(4);
    }
    
    /**
     * 启动TCP服务器（接收端使用）
     */
    public void startTcpServer(int port) {
        if (isRunning) {
            stopNetwork();
        }
        
        networkMode = NetworkMode.TCP_SERVER;
        localPort = port;
        
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    tcpServerSocket = new ServerSocket(localPort);
                    isRunning = true;
                    
                    Log.d(TAG, "TCP服务器已启动，监听端口: " + localPort);
                    
                    // 等待客户端连接
                    tcpSocket = tcpServerSocket.accept();
                    remoteAddress = tcpSocket.getInetAddress();
                    remotePort = tcpSocket.getPort();
                    
                    tcpInputStream = tcpSocket.getInputStream();
                    tcpOutputStream = tcpSocket.getOutputStream();
                    
                    isConnected = true;
                    
                    Log.d(TAG, "客户端已连接: " + remoteAddress.getHostAddress() + ":" + remotePort);
                    
                    if (listener != null) {
                        listener.onConnected(remoteAddress.getHostAddress(), remotePort);
                    }
                    
                    // 开始接收数据
                    receiveTcpData();
                    
                } catch (IOException e) {
                    Log.e(TAG, "启动TCP服务器失败: " + e.getMessage());
                    if (listener != null) {
                        listener.onError("启动TCP服务器失败: " + e.getMessage());
                    }
                }
            }
        });
    }
    
    /**
     * 连接TCP服务器（发送端使用）
     */
    public void connectTcpServer(String address, int port) {
        if (isRunning) {
            stopNetwork();
        }
        
        networkMode = NetworkMode.TCP_CLIENT;
        remoteAddress = null;
        
        try {
            remoteAddress = InetAddress.getByName(address);
        } catch (Exception e) {
            Log.e(TAG, "解析地址失败: " + e.getMessage());
            if (listener != null) {
                listener.onError("解析地址失败: " + e.getMessage());
            }
            return;
        }
        
        remotePort = port;
        
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    tcpSocket = new Socket(remoteAddress, remotePort);
                    tcpInputStream = tcpSocket.getInputStream();
                    tcpOutputStream = tcpSocket.getOutputStream();
                    
                    isRunning = true;
                    isConnected = true;
                    
                    Log.d(TAG, "已连接到TCP服务器: " + address + ":" + port);
                    
                    if (listener != null) {
                        listener.onConnected(address, port);
                    }
                    
                } catch (IOException e) {
                    Log.e(TAG, "连接TCP服务器失败: " + e.getMessage());
                    if (listener != null) {
                        listener.onError("连接TCP服务器失败: " + e.getMessage());
                    }
                }
            }
        });
    }
    
    /**
     * 启动UDP发送端
     */
    public void startUdpSender(String address, int port) {
        if (isRunning) {
            stopNetwork();
        }
        
        networkMode = NetworkMode.UDP_SENDER;
        
        try {
            remoteAddress = InetAddress.getByName(address);
            remotePort = port;
            
            udpSocket = new DatagramSocket();
            isRunning = true;
            isConnected = true;
            
            Log.d(TAG, "UDP发送端已启动，目标: " + address + ":" + port);
            
            if (listener != null) {
                listener.onConnected(address, port);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "启动UDP发送端失败: " + e.getMessage());
            if (listener != null) {
                listener.onError("启动UDP发送端失败: " + e.getMessage());
            }
        }
    }
    
    /**
     * 启动UDP接收端
     */
    public void startUdpReceiver(int port) {
        if (isRunning) {
            stopNetwork();
        }
        
        networkMode = NetworkMode.UDP_RECEIVER;
        localPort = port;
        
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    udpSocket = new DatagramSocket(localPort);
                    isRunning = true;
                    
                    Log.d(TAG, "UDP接收端已启动，监听端口: " + localPort);
                    
                    // 开始接收数据
                    receiveUdpData();
                    
                } catch (IOException e) {
                    Log.e(TAG, "启动UDP接收端失败: " + e.getMessage());
                    if (listener != null) {
                        listener.onError("启动UDP接收端失败: " + e.getMessage());
                    }
                }
            }
        });
    }
    
    /**
     * 接收TCP数据
     */
    private void receiveTcpData() {
        byte[] buffer = new byte[1024 * 1024]; // 1MB缓冲区
        
        try {
            while (isRunning && isConnected) {
                int bytesRead = tcpInputStream.read(buffer);
                if (bytesRead > 0) {
                    if (listener != null) {
                        byte[] data = new byte[bytesRead];
                        System.arraycopy(buffer, 0, data, 0, bytesRead);
                        listener.onDataReceived(data, bytesRead);
                    }
                } else if (bytesRead == -1) {
                    // 连接已关闭
                    Log.d(TAG, "TCP连接已关闭");
                    disconnect();
                    break;
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "接收TCP数据失败: " + e.getMessage());
            disconnect();
        }
    }
    
    /**
     * 接收UDP数据
     */
    private void receiveUdpData() {
        byte[] buffer = new byte[65507]; // UDP最大包大小
        
        try {
            while (isRunning) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                udpSocket.receive(packet);
                
                remoteAddress = packet.getAddress();
                remotePort = packet.getPort();
                
                if (!isConnected) {
                    isConnected = true;
                    Log.d(TAG, "UDP连接已建立: " + remoteAddress.getHostAddress() + ":" + remotePort);
                    
                    if (listener != null) {
                        listener.onConnected(remoteAddress.getHostAddress(), remotePort);
                    }
                }
                
                if (listener != null) {
                    byte[] data = new byte[packet.getLength()];
                    System.arraycopy(buffer, 0, data, 0, packet.getLength());
                    listener.onDataReceived(data, packet.getLength());
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "接收UDP数据失败: " + e.getMessage());
            disconnect();
        }
    }
    
    /**
     * 发送数据
     */
    public void sendData(byte[] data) {
        if (!isRunning || !isConnected) {
            Log.w(TAG, "网络未连接，无法发送数据");
            return;
        }
        
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    switch (networkMode) {
                        case TCP_SERVER:
                        case TCP_CLIENT:
                            if (tcpOutputStream != null) {
                                tcpOutputStream.write(data);
                                tcpOutputStream.flush();
                            }
                            break;
                            
                        case UDP_SENDER:
                            if (udpSocket != null && remoteAddress != null) {
                                DatagramPacket packet = new DatagramPacket(data, data.length, 
                                        remoteAddress, remotePort);
                                udpSocket.send(packet);
                            }
                            break;
                            
                        case UDP_RECEIVER:
                            // 接收端通常不发送数据
                            break;
                    }
                } catch (IOException e) {
                    Log.e(TAG, "发送数据失败: " + e.getMessage());
                    disconnect();
                }
            }
        });
    }
    
    /**
     * 断开连接
     */
    public void disconnect() {
        if (!isConnected && !isRunning) {
            return;
        }
        
        isConnected = false;
        isRunning = false;
        
        try {
            if (tcpInputStream != null) {
                tcpInputStream.close();
            }
            
            if (tcpOutputStream != null) {
                tcpOutputStream.close();
            }
            
            if (tcpSocket != null) {
                tcpSocket.close();
            }
            
            if (tcpServerSocket != null) {
                tcpServerSocket.close();
            }
            
            if (udpSocket != null) {
                udpSocket.close();
            }
        } catch (IOException e) {
            Log.e(TAG, "关闭网络连接时出错: " + e.getMessage());
        }
        
        tcpInputStream = null;
        tcpOutputStream = null;
        tcpSocket = null;
        tcpServerSocket = null;
        udpSocket = null;
        remoteAddress = null;
        
        networkMode = NetworkMode.NONE;
        
        Log.d(TAG, "网络连接已断开");
        
        if (listener != null) {
            listener.onDisconnected();
        }
    }
    
    /**
     * 停止网络服务
     */
    public void stopNetwork() {
        disconnect();
    }
    
    /**
     * 设置监听器
     */
    public void setListener(NetworkListener listener) {
        this.listener = listener;
    }
    
    /**
     * 获取网络模式
     */
    public NetworkMode getNetworkMode() {
        return networkMode;
    }
    
    /**
     * 检查是否已连接
     */
    public boolean isConnected() {
        return isConnected;
    }
    
    /**
     * 检查是否正在运行
     */
    public boolean isRunning() {
        return isRunning;
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
        Log.d(TAG, "网络服务已销毁");
        
        // 停止网络连接
        stopNetwork();
        
        // 关闭线程池
        if (executorService != null) {
            executorService.shutdown();
        }
    }
}