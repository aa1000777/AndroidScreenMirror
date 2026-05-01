package com.example.screenmirror;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * RTP打包器 - 将H.264 NAL单元打包为RTP包
 * 支持单包模式和FU-A分片模式
 * 关键优化：减少大帧传输延迟，支持实时流传输
 */
public class RtpPacketizer {
    
    // RTP头大小
    private static final int RTP_HEADER_SIZE = 12;
    
    // 避免IP分片的最大包大小（以太网MTU 1500 - IP头20 - UDP头8）
    private static final int MAX_PACKET_SIZE = 1400;
    
    // RTP版本（2）
    private static final byte RTP_VERSION = (byte) 0x80;
    
    // 静态SSRC（同步源标识符）
    private static final int SSRC = 0x12345678;
    
    // 序列号（递增）
    private int sequenceNumber = 0;
    
    // 时间戳（90kHz时钟）
    private long timestamp = 0;
    
    // 负载类型（动态范围96-127）
    private static final byte PAYLOAD_TYPE = 96;
    
    // H.264的NALU类型掩码
    private static final byte NALU_TYPE_MASK = 0x1F;
    
    /**
     * 将H.264 NAL单元打包为RTP包
     * 
     * @param nalUnit H.264原始NAL单元（包含起始码0x00000001）
     * @param timestamp 时间戳（90kHz）
     * @return RTP包列表
     */
    public List<byte[]> packetize(byte[] nalUnit, long timestamp) {
        List<byte[]> packets = new ArrayList<>();
        
        // 移除起始码（0x00000001），获取NAL单元数据
        int nalStart = findNalStart(nalUnit);
        if (nalStart == -1) {
            // 没有找到有效的NAL单元
            return packets;
        }
        
        byte[] nalData = new byte[nalUnit.length - nalStart];
        System.arraycopy(nalUnit, nalStart, nalData, 0, nalData.length);
        
        // 获取NAL单元类型
        byte nalType = (byte) (nalData[0] & NALU_TYPE_MASK);
        
        // 检查是否需要分片
        if (nalData.length <= MAX_PACKET_SIZE - RTP_HEADER_SIZE - 1) {
            // 单包模式
            packets.add(createSinglePacket(nalData, timestamp, nalType));
        } else {
            // FU-A分片模式
            packets.addAll(createFuAFragments(nalData, timestamp, nalType));
        }
        
        this.timestamp = timestamp;
        return packets;
    }
    
    /**
     * 创建单个RTP包（适合小NAL单元）
     */
    private byte[] createSinglePacket(byte[] nalData, long timestamp, byte nalType) {
        int packetSize = RTP_HEADER_SIZE + nalData.length;
        byte[] packet = new byte[packetSize];
        
        // 设置RTP头
        setRtpHeader(packet, timestamp, sequenceNumber++, false, false);
        
        // 复制NAL单元数据
        System.arraycopy(nalData, 0, packet, RTP_HEADER_SIZE, nalData.length);
        
        return packet;
    }
    
    /**
     * 创建FU-A分片包（适合大NAL单元）
     */
    private List<byte[]> createFuAFragments(byte[] nalData, long timestamp, byte nalType) {
        List<byte[]> fragments = new ArrayList<>();
        
        // FU-A分片的有效载荷大小（减去FU-A头）
        int maxPayloadSize = MAX_PACKET_SIZE - RTP_HEADER_SIZE - 2;
        
        // 分片起始位置（跳过NAL头）
        int offset = 1;
        boolean firstFragment = true;
        boolean lastFragment = false;
        
        while (offset < nalData.length) {
            // 计算当前分片大小
            int fragmentSize = Math.min(maxPayloadSize, nalData.length - offset);
            lastFragment = (offset + fragmentSize >= nalData.length);
            
            // 创建分片包
            byte[] fragment = createFuAPacket(nalData, offset, fragmentSize, 
                                            timestamp, nalType, firstFragment, lastFragment);
            fragments.add(fragment);
            
            // 更新状态
            offset += fragmentSize;
            firstFragment = false;
        }
        
        return fragments;
    }
    
    /**
     * 创建FU-A分片包
     */
    private byte[] createFuAPacket(byte[] nalData, int offset, int fragmentSize, 
                                 long timestamp, byte nalType, boolean first, boolean last) {
        int packetSize = RTP_HEADER_SIZE + 2 + fragmentSize;
        byte[] packet = new byte[packetSize];
        
        // 设置RTP头
        setRtpHeader(packet, timestamp, sequenceNumber++, false, false);
        
        // FU-A指示字节（保留NAL类型，设置FU-A标志）
        byte fuIndicator = (byte) ((nalData[0] & 0xE0) | 28); // 28 = FU-A类型
        
        // FU-A头字节
        byte fuHeader = (byte) (nalType & NALU_TYPE_MASK);
        if (first) {
            fuHeader |= 0x80; // Start bit
        }
        if (last) {
            fuHeader |= 0x40; // End bit
        }
        
        // 设置FU-A头
        packet[RTP_HEADER_SIZE] = fuIndicator;
        packet[RTP_HEADER_SIZE + 1] = fuHeader;
        
        // 复制NAL单元数据
        System.arraycopy(nalData, offset, packet, RTP_HEADER_SIZE + 2, fragmentSize);
        
        return packet;
    }
    
    /**
     * 设置RTP头部
     */
    private void setRtpHeader(byte[] packet, long timestamp, int seqNum, 
                             boolean marker, boolean padding) {
        // 版本(2) + 填充 + 扩展 + CSRC计数
        packet[0] = RTP_VERSION;
        if (padding) packet[0] |= 0x20;
        if (marker) packet[1] |= 0x80;
        
        // 负载类型
        packet[1] = (byte) (packet[1] | (PAYLOAD_TYPE & 0x7F));
        
        // 序列号（网络字节序）
        packet[2] = (byte) ((seqNum >> 8) & 0xFF);
        packet[3] = (byte) (seqNum & 0xFF);
        
        // 时间戳（网络字节序）
        packet[4] = (byte) ((timestamp >> 24) & 0xFF);
        packet[5] = (byte) ((timestamp >> 16) & 0xFF);
        packet[6] = (byte) ((timestamp >> 8) & 0xFF);
        packet[7] = (byte) (timestamp & 0xFF);
        
        // SSRC（同步源标识符）
        packet[8] = (byte) ((SSRC >> 24) & 0xFF);
        packet[9] = (byte) ((SSRC >> 16) & 0xFF);
        packet[10] = (byte) ((SSRC >> 8) & 0xFF);
        packet[11] = (byte) (SSRC & 0xFF);
    }
    
    /**
     * 查找NAL单元起始位置（跳过起始码）
     */
    private int findNalStart(byte[] data) {
        // 查找起始码 0x00000001 或 0x000001
        for (int i = 0; i <= data.length - 4; i++) {
            if (data[i] == 0x00 && data[i + 1] == 0x00 && 
                data[i + 2] == 0x00 && data[i + 3] == 0x01) {
                return i + 4;
            }
        }
        
        for (int i = 0; i <= data.length - 3; i++) {
            if (data[i] == 0x00 && data[i + 1] == 0x00 && data[i + 2] == 0x01) {
                return i + 3;
            }
        }
        
        return 0; // 假设没有起始码
    }
    
    /**
     * 解析RTP包，提取NAL单元（接收端使用）
     */
    public static byte[] depacketize(List<byte[]> rtpPackets) {
        if (rtpPackets == null || rtpPackets.isEmpty()) {
            return new byte[0];
        }
        
        // 检查是否是FU-A分片
        byte[] firstPacket = rtpPackets.get(0);
        if (firstPacket.length < RTP_HEADER_SIZE + 1) {
            return new byte[0];
        }
        
        byte fuIndicator = firstPacket[RTP_HEADER_SIZE];
        byte nalType = (byte) (fuIndicator & 0x1F);
        
        if (nalType == 28) { // FU-A类型
            return reassembleFuAFragments(rtpPackets);
        } else {
            // 单包模式
            return extractSinglePacket(firstPacket);
        }
    }
    
    /**
     * 重组FU-A分片
     */
    private static byte[] reassembleFuAFragments(List<byte[]> packets) {
        // 计算总大小
        int totalSize = 0;
        for (byte[] packet : packets) {
            if (packet.length > RTP_HEADER_SIZE + 2) {
                totalSize += packet.length - RTP_HEADER_SIZE - 2;
            }
        }
        
        // 添加NAL头大小
        totalSize += 1;
        
        ByteBuffer buffer = ByteBuffer.allocate(totalSize);
        
        // 从第一个分片获取FU头
        byte[] firstPacket = packets.get(0);
        byte fuHeader = firstPacket[RTP_HEADER_SIZE + 1];
        byte nalType = (byte) (fuHeader & 0x1F);
        
        // 重建NAL头
        byte fuIndicator = firstPacket[RTP_HEADER_SIZE];
        byte nalHeader = (byte) ((fuIndicator & 0xE0) | nalType);
        buffer.put(nalHeader);
        
        // 拼接所有分片数据
        for (byte[] packet : packets) {
            if (packet.length > RTP_HEADER_SIZE + 2) {
                int dataSize = packet.length - RTP_HEADER_SIZE - 2;
                buffer.put(packet, RTP_HEADER_SIZE + 2, dataSize);
            }
        }
        
        return buffer.array();
    }
    
    /**
     * 提取单包NAL单元
     */
    private static byte[] extractSinglePacket(byte[] packet) {
        if (packet.length <= RTP_HEADER_SIZE) {
            return new byte[0];
        }
        
        int nalSize = packet.length - RTP_HEADER_SIZE;
        byte[] nalUnit = new byte[nalSize];
        System.arraycopy(packet, RTP_HEADER_SIZE, nalUnit, 0, nalSize);
        
        return nalUnit;
    }
    
    /**
     * 获取RTP头中的时间戳
     */
    public static long getTimestamp(byte[] rtpPacket) {
        if (rtpPacket.length < 12) {
            return 0;
        }
        
        return ((rtpPacket[4] & 0xFFL) << 24) |
               ((rtpPacket[5] & 0xFFL) << 16) |
               ((rtpPacket[6] & 0xFFL) << 8) |
               (rtpPacket[7] & 0xFFL);
    }
    
    /**
     * 获取RTP头中的序列号
     */
    public static int getSequenceNumber(byte[] rtpPacket) {
        if (rtpPacket.length < 4) {
            return 0;
        }
        
        return ((rtpPacket[2] & 0xFF) << 8) | (rtpPacket[3] & 0xFF);
    }
    
    /**
     * 重置序列号和时间戳（开始新的RTP会话）
     */
    public void reset() {
        sequenceNumber = 0;
        timestamp = 0;
    }
    
    /**
     * 设置自定义SSRC
     */
    public void setSsrc(int ssrc) {
        // 可以在需要时更新SSRC
    }
}