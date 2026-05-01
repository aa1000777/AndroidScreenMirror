package com.example.screenmirror;

import android.os.Build;
import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 低延迟网络传输类
 * 关键优化：优先级队列、前向纠错、UDP低延迟传输
 * 支持视频数据（中优先级）和触摸数据（最高优先级）分离传输
 */
public class LowLatencyNetwork {
    
    private static final String TAG = "LowLatencyNetwork";
    
    // 网络配置
    private DatagramSocket videoSocket;
    private DatagramSocket touchSocket;
    private InetAddress remoteAddress;
    private int videoPort = 8888;
    private int touchPort = 8889;
    private int fecPort = 8890;
    
    // 线程池和队列
    private ExecutorService highPriorityExecutor;
    private BlockingQueue<NetworkTask> videoQueue;
    private BlockingQueue<NetworkTask> touchQueue;
    
    // 前向纠错配置
    private boolean enableFec = true;
    private int fecRedundancy = 20; // 20%冗余
    
    // RTP打包器
    private RtpPacketizer rtpPacketizer;
    
    // 状态
    private boolean isRunning = false;
    private boolean isConnected = false;
    
    // 统计信息
    private long videoPacketsSent = 0;
    private long touchPacketsSent = 0;
    private long videoBytesSent = 0;
    private long touchBytesSent = 0;
    
    /**
     * 网络任务优先级枚举
     */
    private enum Priority {
        TOUCH_HIGHEST(1),     // 触摸事件，最高优先级
        VIDEO_HIGH(5),        // 视频关键帧，高优先级
        VIDEO_NORMAL(10),     // 视频普通帧，普通优先级
        CONTROL(20);          // 控制消息，低优先级
        
        final int value;
        Priority(int value) {
            this.value = value;
        }
    }
    
    /**
     * 网络任务类（支持优先级）
     */
    private static class NetworkTask implements Comparable<NetworkTask> {
        final byte[] data;
        final Priority priority;
        final long timestamp;
        final boolean requireAck;
        
        NetworkTask(byte[] data, Priority priority, boolean requireAck) {
            this.data = data;
            this.priority = priority;
            this.timestamp = System.nanoTime();
            this.requireAck = requireAck;
        }
        
        @Override
        public int compareTo(NetworkTask other) {
            // 优先级高的先执行
            int priorityCompare = Integer.compare(this.priority.value, other.priority.value);
            if (priorityCompare != 0) {
                return priorityCompare;
            }
            // 同优先级按时间先后
            return Long.compare(this.timestamp, other.timestamp);
        }
    }
    
    /**
     * 优先级线程工厂
     */
    private static class PriorityThreadFactory implements ThreadFactory {
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String namePrefix;
        
        PriorityThreadFactory() {
            namePrefix = "LowLatencyNetwork-";
        }
        
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, namePrefix + threadNumber.getAndIncrement());
            
            // 设置高优先级
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                t.setPriority(Thread.MAX_PRIORITY);
            }
            
            // 设置为守护线程
            t.setDaemon(true);
            return t;
        }
    }
    
    /**
     * 构造函数
     */
    public LowLatencyNetwork() {
        // 初始化队列
        videoQueue = new LinkedBlockingQueue<>(50);  // 限制队列大小，避免内存堆积
        touchQueue = new LinkedBlockingQueue<>(20);  // 更小的触摸队列
        
        // 初始化优先级线程池
        highPriorityExecutor = new ThreadPoolExecutor(
            2, 4,  // 核心2线程，最大4线程
            60L, TimeUnit.SECONDS,
            new PriorityBlockingQueue<>(),
            new PriorityThreadFactory()
        );
        
        // 初始化RTP打包器
        rtpPacketizer = new RtpPacketizer();
        
        Log.d(TAG, "低延迟网络模块已初始化");
    }
    
    /**
     * 连接到远程设备
     */
    public boolean connect(String address, int videoPort, int touchPort) {
        try {
            remoteAddress = InetAddress.getByName(address);
            this.videoPort = videoPort;
            this.touchPort = touchPort;
            
            // 创建视频Socket（设置为低延迟）
            videoSocket = new DatagramSocket();
            videoSocket.setTrafficClass(0x10); // IPTOS_LOWDELAY
            videoSocket.setSoTimeout(100);     // 短超时，快速失败
            
            // 创建触摸Socket（最高优先级）
            touchSocket = new DatagramSocket();
            touchSocket.setTrafficClass(0x10); // IPTOS_LOWDELAY
            touchSocket.setSoTimeout(50);      // 更短的超时
            
            // 启动发送线程
            startSendingThreads();
            
            isRunning = true;
            isConnected = true;
            
            Log.d(TAG, "已连接到: " + address + " 视频端口:" + videoPort + " 触摸端口:" + touchPort);
            return true;
            
        } catch (UnknownHostException e) {
            Log.e(TAG, "未知主机: " + address, e);
        } catch (SocketException e) {
            Log.e(TAG, "Socket创建失败", e);
        }
        
        return false;
    }
    
    /**
     * 启动发送线程
     */
    private void startSendingThreads() {
        // 启动视频发送线程
        highPriorityExecutor.execute(new Runnable() {
            @Override
            public void run() {
                sendVideoLoop();
            }
        });
        
        // 启动触摸发送线程
        highPriorityExecutor.execute(new Runnable() {
            @Override
            public void run() {
                sendTouchLoop();
            }
        });
        
        Log.d(TAG, "发送线程已启动");
    }
    
    /**
     * 视频发送循环
     */
    private void sendVideoLoop() {
        while (isRunning && isConnected) {
            try {
                NetworkTask task = videoQueue.poll(10, TimeUnit.MILLISECONDS);
                if (task != null && videoSocket != null && remoteAddress != null) {
                    sendVideoPacket(task.data);
                    videoPacketsSent++;
                    videoBytesSent += task.data.length;
                }
            } catch (InterruptedException e) {
                Log.d(TAG, "视频发送线程被中断");
                break;
            } catch (Exception e) {
                Log.e(TAG, "视频发送错误", e);
            }
        }
    }
    
    /**
     * 触摸发送循环
     */
    private void sendTouchLoop() {
        while (isRunning && isConnected) {
            try {
                NetworkTask task = touchQueue.poll(5, TimeUnit.MILLISECONDS);
                if (task != null && touchSocket != null && remoteAddress != null) {
                    sendTouchPacket(task.data);
                    touchPacketsSent++;
                    touchBytesSent += task.data.length;
                }
            } catch (InterruptedException e) {
                Log.d(TAG, "触摸发送线程被中断");
                break;
            } catch (Exception e) {
                Log.e(TAG, "触摸发送错误", e);
            }
        }
    }
    
    /**
     * 发送视频数据包（带RTP打包）
     */
    private void sendVideoPacket(byte[] data) throws IOException {
        // 使用RTP打包
        List<byte[]> rtpPackets = rtpPacketizer.packetize(data, System.currentTimeMillis() * 90);
        
        for (byte[] packet : rtpPackets) {
            DatagramPacket datagramPacket = new DatagramPacket(
                packet, packet.length, remoteAddress, videoPort
            );
            
            videoSocket.send(datagramPacket);
            
            // 如果需要前向纠错，发送冗余包
            if (enableFec) {
                sendFecRedundancy(packet);
            }
        }
    }
    
    /**
     * 发送触摸数据包（最高优先级，直接发送）
     */
    private void sendTouchPacket(byte[] data) throws IOException {
        DatagramPacket packet = new DatagramPacket(
            data, data.length, remoteAddress, touchPort
        );
        
        touchSocket.send(packet);
    }
    
    /**
     * 发送前向纠错冗余包
     */
    private void sendFecRedundancy(byte[] originalPacket) {
        if (!enableFec || fecRedundancy <= 0) {
            return;
        }
        
        // 简化的FEC：发送原始包的副本作为冗余
        // 实际应用中应使用Reed-Solomon或类似编码
        try {
            byte[] fecPacket = applyFecEncoding(originalPacket);
            DatagramPacket packet = new DatagramPacket(
                fecPacket, fecPacket.length, remoteAddress, fecPort
            );
            
            if (videoSocket != null) {
                videoSocket.send(packet);
            }
        } catch (Exception e) {
            Log.w(TAG, "FEC发送失败", e);
        }
    }
    
    /**
     * 应用FEC编码（简化实现）
     */
    private byte[] applyFecEncoding(byte[] data) {
        // 实际应用中应使用完整的FEC算法
        // 这里简单添加FEC头
        byte[] fecData = new byte[data.length + 4];
        fecData[0] = 'F';
        fecData[1] = 'E';
        fecData[2] = 'C';
        fecData[3] = (byte) fecRedundancy;
        System.arraycopy(data, 0, fecData, 4, data.length);
        return fecData;
    }
    
    /**
     * 发送视频数据（H.264 NAL单元）
     */
    public void sendVideoData(byte[] nalUnit, boolean isKeyFrame) {
        if (!isRunning || !isConnected) {
            Log.w(TAG, "网络未连接，视频数据被丢弃");
            return;
        }
        
        Priority priority = isKeyFrame ? Priority.VIDEO_HIGH : Priority.VIDEO_NORMAL;
        NetworkTask task = new NetworkTask(nalUnit, priority, false);
        
        // 非阻塞方式添加到队列
        boolean added = videoQueue.offer(task);
        if (!added) {
            // 队列满，丢弃最旧的数据（避免内存堆积）
            videoQueue.poll();
            videoQueue.offer(task);
            Log.w(TAG, "视频队列满，丢弃旧数据");
        }
    }
    
    /**
     * 发送触摸事件数据
     */
    public void sendTouchData(byte[] touchData) {
        if (!isRunning || !isConnected) {
            Log.w(TAG, "网络未连接，触摸数据被丢弃");
            return;
        }
        
        NetworkTask task = new NetworkTask(touchData, Priority.TOUCH_HIGHEST, true);
        
        // 触摸数据必须立即发送，使用put阻塞直到有空间
        try {
            touchQueue.put(task);
        } catch (InterruptedException e) {
            Log.e(TAG, "触摸数据添加被中断", e);
        }
    }
    
    /**
     * 发送控制消息
     */
    public void sendControlData(byte[] controlData) {
        if (!isRunning || !isConnected) {
            return;
        }
        
        NetworkTask task = new NetworkTask(controlData, Priority.CONTROL, true);
        highPriorityExecutor.execute(new SendTask(task));
    }
    
    /**
     * 直接发送任务
     */
    private class SendTask implements Runnable {
        private final NetworkTask task;
        
        SendTask(NetworkTask task) {
            this.task = task;
        }
        
        @Override
        public void run() {
            try {
                if (task.priority == Priority.TOUCH_HIGHEST) {
                    sendTouchPacket(task.data);
                } else {
                    sendVideoPacket(task.data);
                }
            } catch (Exception e) {
                Log.e(TAG, "直接发送失败", e);
            }
        }
    }
    
    /**
     * 断开连接
     */
    public void disconnect() {
        isRunning = false;
        isConnected = false;
        
        if (videoSocket != null) {
            videoSocket.close();
            videoSocket = null;
        }
        
        if (touchSocket != null) {
            touchSocket.close();
            touchSocket = null;
        }
        
        if (highPriorityExecutor != null) {
            highPriorityExecutor.shutdownNow();
        }
        
        videoQueue.clear();
        touchQueue.clear();
        
        Log.d(TAG, "网络连接已断开");
        logStatistics();
    }
    
    /**
     * 记录统计信息
     */
    private void logStatistics() {
        Log.d(TAG, String.format("网络统计: 视频包=%d, 触摸包=%d, 视频字节=%d, 触摸字节=%d, 队列大小=%d/%d",
                videoPacketsSent, touchPacketsSent, videoBytesSent, touchBytesSent,
                videoQueue.size(), touchQueue.size()));
    }
    
    /**
     * 获取统计信息
     */
    public String getStatistics() {
        return String.format("视频: %d包/%dKB, 触摸: %d包/%dKB, 队列: %d/%d",
                videoPacketsSent, videoBytesSent / 1024,
                touchPacketsSent, touchBytesSent / 1024,
                videoQueue.size(), touchQueue.size());
    }
    
    /**
     * 检查是否已连接
     */
    public boolean isConnected() {
        return isConnected && isRunning;
    }
    
    /**
     * 设置前向纠错
     */
    public void setFecEnabled(boolean enabled, int redundancyPercent) {
        this.enableFec = enabled;
        this.fecRedundancy = Math.max(0, Math.min(100, redundancyPercent));
        Log.d(TAG, "FEC设置: " + enabled + ", 冗余度: " + fecRedundancy + "%");
    }
    
    /**
     * 清理资源
     */
    public void cleanup() {
        disconnect();
        rtpPacketizer = null;
        remoteAddress = null;
        
        Log.d(TAG, "网络模块已清理");
    }
}