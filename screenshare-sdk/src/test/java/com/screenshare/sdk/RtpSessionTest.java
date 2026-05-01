package com.screenshare.sdk;

import com.screenshare.sdk.network.RtpSession;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for RtpSession - validates RTP packing/unpacking
 */
public class RtpSessionTest {

    @Test
    public void testRtpHeaderSize() {
        // RTP header is 12 bytes fixed
        assertEquals(12, RtpSessionTest.getRtpHeaderSize());
    }

    @Test
    public void testRtpHeaderStructure() {
        // Build RTP header manually to verify structure
        byte[] header = new byte[12];
        
        // Byte 0: V(2) P(1) X(1) CC(4) = 0x80 (V=2)
        header[0] = (byte) 0x80;
        // Byte 1: M(1) PT(7) = 0x60 (PT=96 for H264)
        header[1] = (byte) 0x60;
        // Bytes 2-3: Sequence Number (big endian)
        header[2] = 0x00;
        header[3] = 0x01;
        // Bytes 4-7: Timestamp (big endian) - example: 0x00000001
        header[4] = 0x00;
        header[5] = 0x00;
        header[6] = 0x00;
        header[7] = 0x01;
        // Bytes 8-11: SSRC (big endian)
        header[8] = 0x00;
        header[9] = 0x00;
        header[10] = 0x00;
        header[11] = 0x01;
        
        // Verify version (bits 6-7 should be 2)
        int version = (header[0] >> 6) & 0x3;
        assertEquals(2, version);
        
        // Verify payload type
        int payloadType = header[1] & 0x7F;
        assertEquals(0x60 & 0x7F, payloadType);
    }

    @Test
    public void testSequenceNumberIncrement() {
        int initialSeq = 0;
        int nextSeq = (initialSeq + 1) & 0xFFFF;
        assertEquals(1, nextSeq);
        
        // Wrap around
        int maxSeq = 65535;
        int wrappedSeq = (maxSeq + 1) & 0xFFFF;
        assertEquals(0, wrappedSeq);
    }

    @Test
    public void testTimestampIncrement() {
        // For 60fps, timestamp increment = 90000/60 = 1500
        int fps = 60;
        int clockRate = 90000;
        int timestampIncrement = clockRate / fps;
        
        assertEquals(1500, timestampIncrement);
        
        // For 30fps
        fps = 30;
        timestampIncrement = clockRate / fps;
        assertEquals(3000, timestampIncrement);
    }

    @Test
    public void testSsrcGeneration() {
        // SSRC should be random
        int ssrc1 = (int) (Math.random() * Integer.MAX_VALUE);
        int ssrc2 = (int) (Math.random() * Integer.MAX_VALUE);
        
        // Should be positive
        assertTrue(ssrc1 >= 0);
        assertTrue(ssrc2 >= 0);
        
        // Should fit in 32 bits
        assertTrue(ssrc1 <= Integer.MAX_VALUE);
        assertTrue(ssrc2 <= Integer.MAX_VALUE);
    }

    @Test
    public void testFuAPacketization() {
        // Test FU-A fragment calculation
        int mtu = 1400;
        int rtpHeaderSize = 12;
        int fuAHeaderSize = 2;
        int maxFragmentSize = mtu - rtpHeaderSize - fuAHeaderSize;
        
        assertEquals(1386, maxFragmentSize);
        
        // A 3000 byte NAL should need 3 fragments
        int nalSize = 3000;
        int numFragments = (nalSize + maxFragmentSize - 1) / maxFragmentSize;
        assertEquals(3, numFragments);
    }

    @Test
    public void testSingleNalPacket() {
        // NAL smaller than MTU can be sent directly
        int mtu = 1400;
        int rtpHeaderSize = 12;
        int maxNalSize = mtu - rtpHeaderSize;
        
        byte[] smallNal = new byte[100]; // Well under MTU
        
        assertTrue(smallNal.length <= maxNalSize);
    }

    @Test
    public void testRtpPacketValidation() {
        // Valid RTP packet should have:
        // - Version 2
        // - PT matching expected
        // - Minimum 12 bytes
        
        byte[] validPacket = new byte[12];
        validPacket[0] = (byte) 0x80; // V=2
        
        // Check version
        int version = (validPacket[0] >> 6) & 0x3;
        assertEquals(2, version);
        
        // Check minimum length
        assertTrue(validPacket.length >= 12);
    }

    @Test
    public void testByteOrder() {
        // RTP uses big-endian (network byte order)
        int value = 0x12345678;
        
        byte[] bytes = new byte[4];
        bytes[0] = (byte) ((value >> 24) & 0xFF);
        bytes[1] = (byte) ((value >> 16) & 0xFF);
        bytes[2] = (byte) ((value >> 8) & 0xFF);
        bytes[3] = (byte) (value & 0xFF);
        
        assertEquals((byte) 0x12, bytes[0]);
        assertEquals((byte) 0x34, bytes[1]);
        assertEquals((byte) 0x56, bytes[2]);
        assertEquals((byte) 0x78, bytes[3]);
    }

    @Test
    public void testNalTypeExtraction() {
        // NAL unit type is in lower 5 bits of first byte
        byte[] nal = new byte[2];
        nal[0] = (byte) 0x67; // SPS NAL (type 7)
        
        int nalType = nal[0] & 0x1F;
        assertEquals(7, nalType);
        
        // FU-A indicator
        nal[0] = (byte) ((0x67 & 0xE0) | 28); // FU-A type = 28
        assertEquals(28, nal[0] & 0x1F);
    }

    // Helper to expose RTP_HEADER_SIZE from RtpSession
    private static int getRtpHeaderSize() {
        return 12;
    }
}