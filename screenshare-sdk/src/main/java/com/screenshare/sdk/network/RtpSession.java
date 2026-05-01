package com.screenshare.sdk.network;

import com.screenshare.sdk.codec.VideoEncoder;
import com.screenshare.sdk.codec.VideoDecoder;
import com.screenshare.sdk.Common.AtomicBuffer;

import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * RTP 会话
 *
 * 负责：
 * - 发送端：编码帧 → RTP 打包 → UDP 发送
 * - 接收端：UDP 接收 → RTP 解包 → 解码器
 */
public class RtpSession {

    private static final String TAG = "RtpSession";

    // RTP 头固定长度
    private static final int RTP_HEADER_SIZE = 12;

    // RTP 时间戳增量（假设 90kHz 时钟）
    private static final int RTP_TIMESTAMP_CLOCK = 90000;

    // RTP 序列号
    private int sequenceNumber = 0;

    // RTP SSRC（同步源）
    private int ssrc = (int) (Math.random() * Integer.MAX_VALUE);

    private final VideoEncoder encoder;
    private final VideoDecoder decoder;
    private final UdpChannel channel;
    private final AtomicBuffer buffer;

    private boolean isReceiveMode = false;
    private Thread sessionThread;
    private AtomicBoolean isRunning = new AtomicBoolean(false);

    public RtpSession(Object encoderOrDecoder, UdpChannel channel, AtomicBuffer buffer) {
        if (encoderOrDecoder instanceof VideoEncoder) {
            this.encoder = (VideoEncoder) encoderOrDecoder;
            this.decoder = null;
        } else {
            this.encoder = null;
            this.decoder = (VideoDecoder) encoderOrDecoder;
        }
        this.channel = channel;
        this.buffer = buffer;
    }

    public void setReceiveMode(boolean receiveMode) {
        this.isReceiveMode = receiveMode;
    }

    /**
     * 启动 RTP 会话
     */
    public void start() {
        if (isRunning.get()) {
            return;
        }

        isRunning.set(true);

        if (isReceiveMode) {
            sessionThread = new Thread(this::receiveLoop, "RtpSession-Receive");
        } else {
            sessionThread = new Thread(this::sendLoop, "RtpSession-Send");
        }
        sessionThread.start();
    }

    /**
     * 发送端：发送编码帧
     */
    private void sendLoop() {
        // 从编码器输出队列取帧
        BlockingQueue<byte[]> queue = encoder.getOutputQueue();
        long lastTimestamp = 0;
        int fps = encoder != null ? 60 : 30;
        int timestampIncrement = RTP_TIMESTAMP_CLOCK / fps;

        while (isRunning.get()) {
            try {
                byte[] frame = queue.poll(100, java.util.concurrent.TimeUnit.MILLISECONDS);
                if (frame == null) {
                    continue;
                }

                // 打包并发送
                sendRtpPacket(frame, lastTimestamp);
                lastTimestamp += timestampIncrement;

            } catch (Exception e) {
                if (!isRunning.get()) {
                    break;
                }
            }
        }
    }

    /**
     * 接收端：接收 RTP 包
     */
    private void receiveLoop() {
        channel.setEventListener(new UdpChannel.EventListener() {
            @Override
            public void onReceived(byte[] data, int length, java.net.InetAddress from, int fromPort) {
                // 解析 RTP 并发送到解码器
                if (decoder != null) {
                    byte[] rtpPayload = parseRtpPacket(data);
                    if (rtpPayload != null) {
                        decoder.getInputQueue().offer(rtpPayload);
                    }
                }
            }

            @Override
            public void onError(String message) {
                // log error
            }
        });
    }

    /**
     * 发送 RTP 包（单个 NAL 单元）
     */
    private void sendRtpPacket(byte[] nal, long timestamp) {
        if (nal.length <= 1400) {
            // 小于 MTU，直接发送
            sendSingleRtpPacket(nal, timestamp);
        } else {
            // 大于 MTU，分片发送 (FU-A)
            sendFragmentedPacket(nal, timestamp);
        }
    }

    private void sendSingleRtpPacket(byte[] nal, long timestamp) {
        byte[] packet = new byte[RTP_HEADER_SIZE + nal.length];

        // RTP 头
        packet[0] = (byte) 0x80;  // V=2, P=0, X=0, CC=0
        packet[1] = (byte) 0x60;  // M=0, PT=96 (96 = 自定义 H264)
        packet[2] = (byte) ((sequenceNumber >> 8) & 0xFF);
        packet[3] = (byte) (sequenceNumber & 0xFF);
        packet[4] = (byte) ((timestamp >> 24) & 0xFF);
        packet[5] = (byte) ((timestamp >> 16) & 0xFF);
        packet[6] = (byte) ((timestamp >> 8) & 0xFF);
        packet[7] = (byte) (timestamp & 0xFF);
        packet[8] = (byte) ((ssrc >> 24) & 0xFF);
        packet[9] = (byte) ((ssrc >> 16) & 0xFF);
        packet[10] = (byte) ((ssrc >> 8) & 0xFF);
        packet[11] = (byte) (ssrc & 0xFF);

        // NAL 单元
        System.arraycopy(nal, 0, packet, RTP_HEADER_SIZE, nal.length);

        channel.send(packet, 0, packet.length);
        sequenceNumber++;
    }

    private void sendFragmentedPacket(byte[] nal, long timestamp) {
        // 分片 FU-A
        int maxFragmentSize = 1400 - RTP_HEADER_SIZE - 2; // FU-A header = 2 bytes
        int offset = 0;
        boolean start = true;

        while (offset < nal.length) {
            int fragmentSize = Math.min(maxFragmentSize, nal.length - offset);
            boolean end = (offset + fragmentSize >= nal.length);

            byte[] fragment = new byte[RTP_HEADER_SIZE + 2 + fragmentSize];

            // RTP 头
            fragment[0] = (byte) 0x80;
            fragment[1] = (byte) 0x60;
            fragment[2] = (byte) ((sequenceNumber >> 8) & 0xFF);
            fragment[3] = (byte) (sequenceNumber & 0xFF);
            fragment[4] = (byte) ((timestamp >> 24) & 0xFF);
            fragment[5] = (byte) ((timestamp >> 16) & 0xFF);
            fragment[6] = (byte) ((timestamp >> 8) & 0xFF);
            fragment[7] = (byte) (timestamp & 0xFF);
            fragment[8] = (byte) ((ssrc >> 24) & 0xFF);
            fragment[9] = (byte) ((ssrc >> 16) & 0xFF);
            fragment[10] = (byte) ((ssrc >> 8) & 0xFF);
            fragment[11] = (byte) (ssrc & 0xFF);

            // FU-A indicator
            fragment[12] = (byte) ((nal[0] & 0xE0) | 28); // FU-A type = 28
            fragment[12] |= start ? (byte) 0x80 : 0x00;
            fragment[12] |= end ? (byte) 0x40 : 0x00;

            // FU-A header
            fragment[13] = (byte) (nal[0] & 0x1F);
            fragment[13] |= start ? (byte) 0x80 : 0x00;
            fragment[13] |= end ? (byte) 0x40 : 0x00;

            // Fragment data
            System.arraycopy(nal, offset, fragment, RTP_HEADER_SIZE + 2, fragmentSize);

            channel.send(fragment, 0, fragment.length);
            sequenceNumber++;

            offset += fragmentSize;
            start = false;
        }
    }

    /**
     * 解析 RTP 包
     */
    private byte[] parseRtpPacket(byte[] data) {
        if (data.length < RTP_HEADER_SIZE) {
            return null;
        }

        // 检查版本和payload type
        if ((data[0] & 0xC0) != 0x80) {
            return null;
        }

        int payloadType = data[1] & 0x7F;
        if (payloadType != 0x60) {
            return null;
        }

        // 提取 NAL 单元
        // 如果有 FU-A 分片，需要重组
        int nalType = data[RTP_HEADER_SIZE] & 0x1F;

        if (nalType >= 1 && nalType <= 23) {
            // 单个 NAL
            byte[] nal = new byte[data.length - RTP_HEADER_SIZE];
            System.arraycopy(data, RTP_HEADER_SIZE, nal, 0, nal.length);
            return nal;
        } else if (nalType == 28) {
            // FU-A 分片
            // 需要重组，这里简化处理
            return null;
        }

        return null;
    }

    /**
     * 停止 RTP 会话
     */
    public void stop() {
        isRunning.set(false);

        if (sessionThread != null) {
            try {
                sessionThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            sessionThread = null;
        }
    }

    /**
     * 释放资源
     */
    public void release() {
        stop();
    }
}