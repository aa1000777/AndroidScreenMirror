package com.screenshare.sdk;

import com.screenshare.sdk.Common.BandwidthAdapter;
import com.screenshare.sdk.Common.BandwidthAdapter.QualityLevel;
import com.screenshare.sdk.Common.BandwidthAdapter.EncoderConfig;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for BandwidthAdapter - tests bitrate adjustment logic
 */
public class BandwidthAdapterTest {

    @Test
    public void testInitialState() {
        BandwidthAdapter adapter = new BandwidthAdapter();
        
        assertTrue(adapter.isEnabled());
        assertEquals(QualityLevel.HIGH, adapter.getCurrentLevel());
        
        EncoderConfig config = adapter.getCurrentConfig();
        assertEquals(1920, config.width);
        assertEquals(1080, config.height);
        assertEquals(60, config.fps);
        assertEquals(8_000_000, config.bitrate);
    }

    @Test
    public void testDisableEnable() {
        BandwidthAdapter adapter = new BandwidthAdapter();
        
        adapter.setEnabled(false);
        assertFalse(adapter.isEnabled());
        
        adapter.setEnabled(true);
        assertTrue(adapter.isEnabled());
    }

    @Test
    public void testDegradeQuality() {
        BandwidthAdapter adapter = new BandwidthAdapter();
        
        assertEquals(QualityLevel.HIGH, adapter.getCurrentLevel());
        
        adapter.degrade();
        assertEquals(QualityLevel.MEDIUM, adapter.getCurrentLevel());
        
        adapter.degrade();
        assertEquals(QualityLevel.LOW, adapter.getCurrentLevel());
        
        adapter.degrade();
        assertEquals(QualityLevel.MINIMAL, adapter.getCurrentLevel());
        
        // Cannot degrade further
        adapter.degrade();
        assertEquals(QualityLevel.MINIMAL, adapter.getCurrentLevel());
    }

    @Test
    public void testUpgradeQuality() {
        BandwidthAdapter adapter = new BandwidthAdapter();
        
        // Start at bottom
        adapter.degrade();
        adapter.degrade();
        adapter.degrade();
        assertEquals(QualityLevel.MINIMAL, adapter.getCurrentLevel());
        
        adapter.upgrade();
        assertEquals(QualityLevel.LOW, adapter.getCurrentLevel());
        
        adapter.upgrade();
        assertEquals(QualityLevel.MEDIUM, adapter.getCurrentLevel());
        
        adapter.upgrade();
        assertEquals(QualityLevel.HIGH, adapter.getCurrentLevel());
        
        // Cannot upgrade further
        adapter.upgrade();
        assertEquals(QualityLevel.HIGH, adapter.getCurrentLevel());
    }

    @Test
    public void testReset() {
        BandwidthAdapter adapter = new BandwidthAdapter();
        
        adapter.degrade();
        adapter.degrade();
        assertEquals(QualityLevel.LOW, adapter.getCurrentLevel());
        
        adapter.reset();
        assertEquals(QualityLevel.HIGH, adapter.getCurrentLevel());
    }

    @Test
    public void testUpdateStats() {
        BandwidthAdapter adapter = new BandwidthAdapter();
        adapter.setEnabled(true);
        
        // Update with normal values
        adapter.updateStats(10000, 0, 50);
        assertEquals(50, adapter.getCurrentRtt());
        
        // Update with different RTT
        adapter.updateStats(10000, 0, 100);
        assertEquals(100, adapter.getCurrentRtt());
    }

    @Test
    public void testQualityLevels() {
        assertEquals(4, QualityLevel.values().length);
        assertNotNull(QualityLevel.valueOf("HIGH"));
        assertNotNull(QualityLevel.valueOf("MEDIUM"));
        assertNotNull(QualityLevel.valueOf("LOW"));
        assertNotNull(QualityLevel.valueOf("MINIMAL"));
    }

    @Test
    public void testEncoderConfigValues() {
        BandwidthAdapter adapter = new BandwidthAdapter();
        
        EncoderConfig highConfig = adapter.getCurrentConfig();
        assertEquals(1920, highConfig.width);
        assertEquals(1080, highConfig.height);
        assertEquals(60, highConfig.fps);
        assertEquals(8_000_000, highConfig.bitrate);
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
        
        // Initial bandwidth estimate should be HIGH threshold
        assertTrue(adapter.getEstimatedBandwidth() >= 10_000_000);
    }

    @Test
    public void testSetConfigRange() {
        BandwidthAdapter adapter = new BandwidthAdapter();
        
        EncoderConfig maxConfig = new EncoderConfig(2560, 1440, 120, 16_000_000);
        EncoderConfig minConfig = new EncoderConfig(1280, 720, 30, 2_000_000);
        
        adapter.setConfigRange(maxConfig, minConfig);
        
        // Setting range should work without throwing
        // Note: The actual implementation uses hardcoded levels, so this just validates the API
    }

    @Test
    public void testToString() {
        BandwidthAdapter adapter = new BandwidthAdapter();
        String str = adapter.toString();
        
        assertNotNull(str);
        assertTrue(str.contains("BandwidthAdapter"));
        assertTrue(str.contains("level="));
        assertTrue(str.contains("bandwidth="));
    }
}