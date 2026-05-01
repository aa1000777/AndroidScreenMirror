package com.screenshare.sdk.network;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * UDP 通道
 *
 * 用于视频帧和触摸事件的传输
 * 支持监听模式和连接模式
 */
public class UdpChannel {

    private static final String TAG = "UdpChannel";

    private final int port;
    private DatagramSocket socket;
    private InetAddress peerAddress;
    private int peerPort;

    private boolean isListenerMode = false;
    private boolean isConnected = false;

    private Thread receiveThread;
    private AtomicBoolean isRunning = new AtomicBoolean(false);

    private BlockingQueue<byte[]> receiveQueue = new LinkedBlockingQueue<>(100);
    private EventListener eventListener;

    public interface EventListener {
        void onReceived(byte[] data, int length, InetAddress from, int fromPort);
        void onError(String message);
    }

    public UdpChannel(int port) {
        this.port = port;
    }

    public void setEventListener(EventListener listener) {
        this.eventListener = listener;
    }

    public void setPeerAddress(String address) {
        try {
            this.peerAddress = InetAddress.getByName(address);
        } catch (Exception e) {
            if (eventListener != null) {
                eventListener.onError("Invalid address: " + address);
            }
        }
    }

    public void setPeerAddress(String address, int port) {
        try {
            this.peerAddress = InetAddress.getByName(address);
            this.peerPort = port;
            this.isConnected = true;
        } catch (Exception e) {
            if (eventListener != null) {
                eventListener.onError("Invalid address: " + address);
            }
        }
    }

    public void setMode(boolean listener) {
        this.isListenerMode = listener;
    }

    public boolean isConnected() {
        return isConnected || isListenerMode;
    }

    /**
     * 打开通道
     */
    public synchronized void open() {
        if (socket != null) {
            return;
        }

        try {
            if (isListenerMode) {
                // 监听模式：绑定端口
                socket = new DatagramSocket(port);
                socket.setReuseAddress(true);
            } else {
                // 连接模式：仅创建 socket
                socket = new DatagramSocket();
            }

            isRunning.set(true);

            // 启动接收线程
            receiveThread = new Thread(this::receiveLoop, "UdpChannel-" + port);
            receiveThread.start();

        } catch (Exception e) {
            if (eventListener != null) {
                eventListener.onError("Failed to open UDP channel: " + e.getMessage());
            }
        }
    }

    private void receiveLoop() {
        byte[] buffer = new byte[64 * 1024]; // 64KB
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

        while (isRunning.get()) {
            try {
                socket.receive(packet);

                int length = packet.getLength();
                if (length > 0) {
                    byte[] data = new byte[length];
                    System.arraycopy(buffer, 0, data, 0, length);

                    if (eventListener != null) {
                        eventListener.onReceived(data, length, packet.getAddress(), packet.getPort());
                    }
                }

            } catch (Exception e) {
                if (!isRunning.get()) {
                    break;
                }
                if (eventListener != null) {
                    eventListener.onError("Receive error: " + e.getMessage());
                }
            }
        }
    }

    /**
     * 发送数据
     */
    public synchronized void send(byte[] data, int offset, int length) {
        if (socket == null || peerAddress == null) {
            return;
        }

        try {
            DatagramPacket packet = new DatagramPacket(data, offset, length, peerAddress, peerPort);
            socket.send(packet);
        } catch (Exception e) {
            if (eventListener != null) {
                eventListener.onError("Send error: " + e.getMessage());
            }
        }
    }

    /**
     * 发送数据到指定地址
     */
    public synchronized void send(byte[] data, int offset, int length, InetAddress address, int port) {
        if (socket == null) {
            return;
        }

        try {
            DatagramPacket packet = new DatagramPacket(data, offset, length, address, port);
            socket.send(packet);
        } catch (Exception e) {
            if (eventListener != null) {
                eventListener.onError("Send error: " + e.getMessage());
            }
        }
    }

    /**
     * 断开连接
     */
    public void disconnect() {
        isConnected = false;
        peerAddress = null;
    }

    /**
     * 关闭通道
     */
    public synchronized void close() {
        isRunning.set(false);

        if (receiveThread != null) {
            try {
                receiveThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            receiveThread = null;
        }

        if (socket != null) {
            socket.close();
            socket = null;
        }

        receiveQueue.clear();
    }
}