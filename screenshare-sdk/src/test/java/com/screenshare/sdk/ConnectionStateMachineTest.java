package com.screenshare.sdk;

import com.screenshare.sdk.Common.ConnectionStateMachine;
import com.screenshare.sdk.Common.ConnectionStateMachine.State;
import com.screenshare.sdk.Common.ConnectionStateMachine.TransitionEvent;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.List;

/**
 * Unit tests for ConnectionStateMachine - validates state transitions
 * 
 * NOTE: Full testing requires Robolectric due to android.util.Log dependency.
 * These tests focus on pure logic that can be tested.
 */
public class ConnectionStateMachineTest {

    @Test
    public void testInitialState() {
        ConnectionStateMachine sm = new ConnectionStateMachine(ConnectionStateMachine.Role.SENDER);
        assertEquals(State.IDLE, sm.getState());
        assertEquals(ConnectionStateMachine.Role.SENDER, sm.getRole());
    }

    @Test
    public void testInitialStateReceiver() {
        ConnectionStateMachine sm = new ConnectionStateMachine(ConnectionStateMachine.Role.RECEIVER);
        assertEquals(State.IDLE, sm.getState());
        assertEquals(ConnectionStateMachine.Role.RECEIVER, sm.getRole());
    }

    @Test
    public void testForceState() {
        ConnectionStateMachine sm = new ConnectionStateMachine(ConnectionStateMachine.Role.SENDER);
        
        sm.forceState(State.CONNECTION_LOST);
        assertEquals(State.CONNECTION_LOST, sm.getState());
        
        sm.forceState(State.STREAMING);
        assertEquals(State.STREAMING, sm.getState());
    }

    @Test
    public void testGetState() {
        ConnectionStateMachine sm = new ConnectionStateMachine(ConnectionStateMachine.Role.SENDER);
        
        // Initial state
        assertEquals(State.IDLE, sm.getState());
        
        // After forceState
        sm.forceState(State.CONNECTED);
        assertEquals(State.CONNECTED, sm.getState());
    }

    @Test
    public void testGetTransitionHistory() {
        ConnectionStateMachine sm = new ConnectionStateMachine(ConnectionStateMachine.Role.SENDER);
        
        // Initially empty
        List<TransitionEvent> history = sm.getTransitionHistory();
        assertNotNull(history);
    }

    @Test
    public void testStateEnumValues() {
        // Verify all states exist
        assertEquals(7, State.values().length);
        assertNotNull(State.valueOf("IDLE"));
        assertNotNull(State.valueOf("DISCOVERING"));
        assertNotNull(State.valueOf("CONNECTING"));
        assertNotNull(State.valueOf("CONNECTED"));
        assertNotNull(State.valueOf("STREAMING"));
        assertNotNull(State.valueOf("CONNECTION_LOST"));
        assertNotNull(State.valueOf("DISCONNECTING"));
    }

    @Test
    public void testRoleEnumValues() {
        assertEquals(2, ConnectionStateMachine.Role.values().length);
        assertNotNull(ConnectionStateMachine.Role.valueOf("SENDER"));
        assertNotNull(ConnectionStateMachine.Role.valueOf("RECEIVER"));
    }
}