package com.screenshare.sdk;

import static org.junit.Assert.*;
import org.junit.Test;

/**
 * Unit tests for SenderConfig validation
 */
public class SenderConfigTest {

    @Test
    public void testDefaultValues() {
        SenderConfig config = new SenderConfig();
        
        assertEquals(1920, config.width);
        assertEquals(1080, config.height);
        assertEquals(60, config.fps);
        assertEquals(0, config.videoBitrate);
        assertEquals(2, config.keyFrameInterval);
        assertEquals(SenderConfig.VideoCodecType.H264_HARDWARE, config.videoCodec);
        assertFalse(config.withAudio);
        assertEquals(128000, config.audioBitrate);
        assertEquals(8888, config.videoPort);
        assertEquals(8889, config.touchPort);
        assertEquals(3, config.maxReconnect);
        assertEquals(5000, config.connectTimeout);
        assertFalse(config.performanceMode);
        assertEquals(SenderConfig.PerformancePreset.BALANCED, config.performancePreset);
        assertEquals(512 * 1024, config.sendBufferSize);
    }

    @Test
    public void testCustomValues() {
        SenderConfig config = new SenderConfig();
        config.width = 1280;
        config.height = 720;
        config.fps = 30;
        config.videoBitrate = 4000000;
        config.videoPort = 9999;
        config.touchPort = 9998;
        
        assertEquals(1280, config.width);
        assertEquals(720, config.height);
        assertEquals(30, config.fps);
        assertEquals(4000000, config.videoBitrate);
        assertEquals(9999, config.videoPort);
        assertEquals(9998, config.touchPort);
    }

    @Test
    public void testVideoCodecTypeValues() {
        SenderConfig config = new SenderConfig();
        
        assertEquals(4, SenderConfig.VideoCodecType.values().length);
        assertNotNull(SenderConfig.VideoCodecType.valueOf("H264_HARDWARE"));
        assertNotNull(SenderConfig.VideoCodecType.valueOf("H265_HARDWARE"));
        assertNotNull(SenderConfig.VideoCodecType.valueOf("VP8"));
        assertNotNull(SenderConfig.VideoCodecType.valueOf("VP9"));
    }

    @Test
    public void testPerformancePresetValues() {
        SenderConfig config = new SenderConfig();
        
        assertEquals(3, SenderConfig.PerformancePreset.values().length);
        assertNotNull(SenderConfig.PerformancePreset.valueOf("LOW_LATENCY"));
        assertNotNull(SenderConfig.PerformancePreset.valueOf("BALANCED"));
        assertNotNull(SenderConfig.PerformancePreset.valueOf("HIGH_QUALITY"));
    }

    @Test
    public void testAudioConfiguration() {
        SenderConfig config = new SenderConfig();
        config.withAudio = true;
        config.audioBitrate = 64000;
        
        assertTrue(config.withAudio);
        assertEquals(64000, config.audioBitrate);
    }

    @Test
    public void testBufferSizeConfiguration() {
        SenderConfig config = new SenderConfig();
        
        // Default buffer size
        assertEquals(512 * 1024, config.sendBufferSize);
        
        // Custom buffer size
        config.sendBufferSize = 1024 * 1024;
        assertEquals(1024 * 1024, config.sendBufferSize);
    }
}