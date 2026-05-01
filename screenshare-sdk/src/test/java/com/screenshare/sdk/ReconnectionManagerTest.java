package com.screenshare.sdk;

import com.screenshare.sdk.Common.ReconnectionManager;
import com.screenshare.sdk.Common.ConnectionStateMachine;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for ReconnectionManager - validates retry logic
 * 
 * NOTE: Full testing of ReconnectionManager requires Robolectric because
 * it uses android.os.Handler and android.os.Looper internally.
 * These tests focus on what can be tested without Android runtime.
 */
public class ReconnectionManagerTest {

    @Test
    public void testReconnectStates() {
        // Test enum values - this doesn't require Android runtime
        assertEquals(5, ReconnectionManager.ReconnectState.values().length);
        assertNotNull(ReconnectionManager.ReconnectState.valueOf("IDLE"));
        assertNotNull(ReconnectionManager.ReconnectState.valueOf("SCHEDULED"));
        assertNotNull(ReconnectionManager.ReconnectState.valueOf("IN_PROGRESS"));
        assertNotNull(ReconnectionManager.ReconnectState.valueOf("SUCCESS"));
        assertNotNull(ReconnectionManager.ReconnectState.valueOf("FAILED"));
    }
}