package com.screenshare.sdk;

import com.screenshare.sdk.Common.LatencyTester;
import com.screenshare.sdk.Common.LatencyTester.LatencyRecord;
import com.screenshare.sdk.Common.LatencyTester.LatencyStats;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.List;

/**
 * Unit tests for LatencyTester - validates statistics calculation
 * 
 * NOTE: Full testing requires Robolectric due to android.os.SystemClock dependency.
 * These tests focus on pure logic that can be tested.
 */
public class LatencyTesterTest {

    @Test
    public void testLatencyStatsWithNoRecords() {
        LatencyTester tester = new LatencyTester();
        
        LatencyStats stats = tester.getStats();
        
        assertEquals(0, stats.count);
        assertEquals(0, stats.totalRecords);
    }

    @Test
    public void testClearRecords() {
        LatencyTester tester = new LatencyTester();
        
        tester.clearRecords();
        assertEquals(0, tester.getRecordCount());
    }

    @Test
    public void testGetRecords() {
        LatencyTester tester = new LatencyTester();
        
        List<LatencyRecord> records = tester.getRecords();
        assertNotNull(records);
        assertEquals(0, records.size());
    }

    @Test
    public void testIsRunning() {
        LatencyTester tester = new LatencyTester();
        assertFalse(tester.isRunning());
    }

    @Test
    public void testToString() {
        LatencyTester tester = new LatencyTester();
        String str = tester.toString();
        assertNotNull(str);
        assertTrue(str.contains("LatencyTester"));
    }

    @Test
    public void testLatencyRecordConstruction() {
        LatencyRecord record = new LatencyRecord(1000L, 50L, true);
        assertEquals(1000L, record.timestamp);
        assertEquals(50L, record.roundTripTimeMs);
        assertTrue(record.isValid);
    }

    @Test
    public void testLatencyRecordInvalid() {
        LatencyRecord record = new LatencyRecord(1000L, 0L, false);
        assertEquals(1000L, record.timestamp);
        assertEquals(0L, record.roundTripTimeMs);
        assertFalse(record.isValid);
    }

    @Test
    public void testLatencyStatsEmpty() {
        LatencyStats stats = new LatencyStats(java.util.Collections.emptyList());
        
        assertEquals(0, stats.count);
        assertEquals(0, stats.minMs);
        assertEquals(0, stats.maxMs);
        assertEquals(0, stats.avgMs);
        assertEquals(0f, stats.lossRate, 0.001);
    }

    @Test
    public void testLatencyStatsToString() {
        LatencyStats stats = new LatencyStats(java.util.Collections.emptyList());
        String str = stats.toString();
        assertNotNull(str);
        assertTrue(str.contains("LatencyStats"));
    }

    @Test
    public void testLatencyStatsGetReport() {
        LatencyStats stats = new LatencyStats(java.util.Collections.emptyList());
        String report = stats.getReport();
        assertNotNull(report);
        assertTrue(report.contains("Latency Test Report"));
    }
}