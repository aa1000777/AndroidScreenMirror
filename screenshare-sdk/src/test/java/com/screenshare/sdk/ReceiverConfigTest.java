package com.screenshare.sdk;

import static org.junit.Assert.*;
import org.junit.Test;

/**
 * Unit tests for ReceiverConfig validation
 */
public class ReceiverConfigTest {

    @Test
    public void testDefaultValues() {
        ReceiverConfig config = new ReceiverConfig();
        
        assertEquals(1920, config.width);
        assertEquals(1080, config.height);
        assertEquals(ReceiverConfig.VideoCodecType.H264_HARDWARE, config.videoCodec);
        assertFalse(config.withAudio);
        assertEquals(8888, config.listenVideoPort);
        assertEquals(8889, config.listenTouchPort);
        assertEquals(3, config.maxReconnect);
        assertEquals(5000, config.connectTimeout);
        assertEquals(512 * 1024, config.receiveBufferSize);
        assertTrue(config.lowLatencyMode);
        assertEquals(2, config.maxFrameQueue);
    }

    @Test
    public void testCustomValues() {
        ReceiverConfig config = new ReceiverConfig();
        config.width = 1280;
        config.height = 720;
        config.listenVideoPort = 7777;
        config.listenTouchPort = 7778;
        config.lowLatencyMode = false;
        config.maxFrameQueue = 4;
        
        assertEquals(1280, config.width);
        assertEquals(720, config.height);
        assertEquals(7777, config.listenVideoPort);
        assertEquals(7778, config.listenTouchPort);
        assertFalse(config.lowLatencyMode);
        assertEquals(4, config.maxFrameQueue);
    }

    @Test
    public void testVideoCodecTypeValues() {
        ReceiverConfig config = new ReceiverConfig();
        
        assertEquals(4, ReceiverConfig.VideoCodecType.values().length);
        assertNotNull(ReceiverConfig.VideoCodecType.valueOf("H264_HARDWARE"));
        assertNotNull(ReceiverConfig.VideoCodecType.valueOf("H265_HARDWARE"));
        assertNotNull(ReceiverConfig.VideoCodecType.valueOf("VP8"));
        assertNotNull(ReceiverConfig.VideoCodecType.valueOf("VP9"));
    }

    @Test
    public void testAudioConfiguration() {
        ReceiverConfig config = new ReceiverConfig();
        config.withAudio = true;
        
        assertTrue(config.withAudio);
    }

    @Test
    public void testBufferSizeConfiguration() {
        ReceiverConfig config = new ReceiverConfig();
        
        // Default buffer size
        assertEquals(512 * 1024, config.receiveBufferSize);
        
        // Custom buffer size
        config.receiveBufferSize = 1024 * 1024;
        assertEquals(1024 * 1024, config.receiveBufferSize);
    }

    @Test
    public void testReconnectConfiguration() {
        ReceiverConfig config = new ReceiverConfig();
        
        // Default values
        assertEquals(3, config.maxReconnect);
        assertEquals(5000, config.connectTimeout);
        
        // Custom values
        config.maxReconnect = 5;
        config.connectTimeout = 10000;
        assertEquals(5, config.maxReconnect);
        assertEquals(10000, config.connectTimeout);
    }
}