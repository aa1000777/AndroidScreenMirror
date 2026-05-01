package com.screenshare.sdk;

import com.screenshare.sdk.Common.BandwidthAdapter;
import com.screenshare.sdk.Common.BandwidthAdapter.QualityLevel;
import com.screenshare.sdk.Common.BandwidthAdapter.EncoderConfig;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for BandwidthAdapter - tests bitrate adjustment logic
 * 
 * NOTE: Full testing requires Robolectric due to android.util.Log dependency.
 * These tests focus on pure logic that can be tested.
 */
public class BandwidthAdapterTest {

    @Test
    public void testQualityLevels() {
        assertEquals(4, QualityLevel.values().length);
        assertNotNull(QualityLevel.valueOf("HIGH"));
        assertNotNull(QualityLevel.valueOf("MEDIUM"));
        assertNotNull(QualityLevel.valueOf("LOW"));
        assertNotNull(QualityLevel.valueOf("MINIMAL"));
    }

    @Test
    public void testEncoderConfigConstruction() {
        EncoderConfig config = new EncoderConfig(1920, 1080, 60, 8_000_000);
        assertEquals(1920, config.width);
        assertEquals(1080, config.height);
        assertEquals(60, config.fps);
        assertEquals(8_000_000, config.bitrate);
    }

    @Test
    public void testEncoderConfigToString() {
        EncoderConfig config = new EncoderConfig(1920, 1080, 60, 8_000_000);
        String str = config.toString();
        assertNotNull(str);
        assertTrue(str.contains("1920"));
        assertTrue(str.contains("1080"));
        assertTrue(str.contains("60"));
    }

    @Test
    public void testLossRate() {
        BandwidthAdapter adapter = new BandwidthAdapter();
        // Default loss rate is 0
        assertEquals(0.0, adapter.getLossRate(), 0.001);
    }

    @Test
    public void testEstimatedBandwidth() {
        BandwidthAdapter adapter = new BandwidthAdapter();
        // Initial bandwidth estimate should be positive
        assertTrue(adapter.getEstimatedBandwidth() > 0);
    }

    @Test
    public void testCurrentRtt() {
        BandwidthAdapter adapter = new BandwidthAdapter();
        // Initially 0
        assertEquals(0, adapter.getCurrentRtt());
    }
}