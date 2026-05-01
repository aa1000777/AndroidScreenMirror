package com.screenshare.sdk;

import com.screenshare.sdk.Common.ConnectionStateMachine;
import com.screenshare.sdk.Common.ConnectionStateMachine.State;
import com.screenshare.sdk.Common.ConnectionStateMachine.TransitionEvent;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Unit tests for ConnectionStateMachine - validates state transitions
 */
public class ConnectionStateMachineTest {

    @Test
    public void testInitialState() {
        ConnectionStateMachine sm = new ConnectionStateMachine(ConnectionStateMachine.Role.SENDER);
        assertEquals(State.IDLE, sm.getState());
        assertEquals(ConnectionStateMachine.Role.SENDER, sm.getRole());
    }

    @Test
    public void testValidTransitionsFromIdle() {
        ConnectionStateMachine sm = new ConnectionStateMachine(ConnectionStateMachine.Role.SENDER);
        
        assertTrue(sm.transitionTo(State.DISCOVERING, "Start discovery"));
        assertEquals(State.DISCOVERING, sm.getState());
        
        // Can transition to same state
        assertTrue(sm.transitionTo(State.DISCOVERING, "Already discovering"));
        assertEquals(State.DISCOVERING, sm.getState());
    }

    @Test
    public void testValidTransitionsFromDiscovering() {
        ConnectionStateMachine sm = new ConnectionStateMachine(ConnectionStateMachine.Role.SENDER);
        sm.transitionTo(State.DISCOVERING, "Start discovery");
        
        assertTrue(sm.transitionTo(State.CONNECTING, "Connect"));
        assertEquals(State.CONNECTING, sm.getState());
        
        assertTrue(sm.transitionTo(State.IDLE, "Cancel"));
        assertEquals(State.IDLE, sm.getState());
    }

    @Test
    public void testValidTransitionsFromConnecting() {
        ConnectionStateMachine sm = new ConnectionStateMachine(ConnectionStateMachine.Role.SENDER);
        sm.transitionTo(State.DISCOVERING, "Start discovery");
        sm.transitionTo(State.CONNECTING, "Connect");
        
        assertTrue(sm.transitionTo(State.CONNECTED, "Connected"));
        assertEquals(State.CONNECTED, sm.getState());
        
        // Reset and try CONNECTION_LOST
        sm.transitionTo(State.DISCOVERING, "Start");
        sm.transitionTo(State.CONNECTING, "Connect");
        assertTrue(sm.transitionTo(State.CONNECTION_LOST, "Lost connection"));
        assertEquals(State.CONNECTION_LOST, sm.getState());
    }

    @Test
    public void testValidTransitionsFromConnected() {
        ConnectionStateMachine sm = new ConnectionStateMachine(ConnectionStateMachine.Role.SENDER);
        sm.transitionTo(State.DISCOVERING, "Start");
        sm.transitionTo(State.CONNECTING, "Connect");
        sm.transitionTo(State.CONNECTED, "Connected");
        
        assertTrue(sm.transitionTo(State.STREAMING, "Start streaming"));
        assertEquals(State.STREAMING, sm.getState());
        
        assertTrue(sm.transitionTo(State.CONNECTION_LOST, "Connection lost"));
        assertEquals(State.CONNECTION_LOST, sm.getState());
    }

    @Test
    public void testValidTransitionsFromStreaming() {
        ConnectionStateMachine sm = new ConnectionStateMachine(ConnectionStateMachine.Role.SENDER);
        sm.transitionTo(State.DISCOVERING, "Start");
        sm.transitionTo(State.CONNECTING, "Connect");
        sm.transitionTo(State.CONNECTED, "Connected");
        sm.transitionTo(State.STREAMING, "Streaming");
        
        assertTrue(sm.transitionTo(State.CONNECTION_LOST, "Lost"));
        assertEquals(State.CONNECTION_LOST, sm.getState());
        
        assertTrue(sm.transitionTo(State.IDLE, "Disconnect"));
        assertEquals(State.IDLE, sm.getState());
    }

    @Test
    public void testValidTransitionsFromConnectionLost() {
        ConnectionStateMachine sm = new ConnectionStateMachine(ConnectionStateMachine.Role.SENDER);
        sm.transitionTo(State.DISCOVERING, "Start");
        sm.transitionTo(State.CONNECTING, "Connect");
        sm.transitionTo(State.CONNECTED, "Connected");
        sm.transitionTo(State.STREAMING, "Streaming");
        sm.transitionTo(State.CONNECTION_LOST, "Lost");
        
        assertTrue(sm.transitionTo(State.DISCOVERING, "Reconnect"));
        assertEquals(State.DISCOVERING, sm.getState());
        
        // Can also go to CONNECTING for reconnect
        assertTrue(sm.transitionTo(State.CONNECTING, "Reconnect directly"));
        assertEquals(State.CONNECTING, sm.getState());
    }

    @Test
    public void testValidTransitionsFromDisconnecting() {
        ConnectionStateMachine sm = new ConnectionStateMachine(ConnectionStateMachine.Role.SENDER);
        sm.transitionTo(State.DISCOVERING, "Start");
        sm.transitionTo(State.CONNECTING, "Connect");
        sm.transitionTo(State.CONNECTED, "Connected");
        sm.transitionTo(State.DISCONNECTING, "User disconnect");
        
        assertTrue(sm.transitionTo(State.IDLE, "Disconnected"));
        assertEquals(State.IDLE, sm.getState());
    }

    @Test
    public void testInvalidTransitions() {
        ConnectionStateMachine sm = new ConnectionStateMachine(ConnectionStateMachine.Role.SENDER);
        
        // Cannot go directly from IDLE to CONNECTED
        assertFalse(sm.transitionTo(State.CONNECTED, "Invalid"));
        assertEquals(State.IDLE, sm.getState());
        
        // Cannot go directly from IDLE to STREAMING
        assertFalse(sm.transitionTo(State.STREAMING, "Invalid"));
        assertEquals(State.IDLE, sm.getState());
    }

    @Test
    public void testTransitionHistory() {
        ConnectionStateMachine sm = new ConnectionStateMachine(ConnectionStateMachine.Role.SENDER);
        
        sm.transitionTo(State.DISCOVERING, "Start");
        sm.transitionTo(State.CONNECTING, "Connect");
        sm.transitionTo(State.CONNECTED, "Connected");
        
        List<TransitionEvent> history = sm.getTransitionHistory();
        assertEquals(3, history.size());
        
        // Check first transition
        assertEquals(State.IDLE, history.get(0).fromState);
        assertEquals(State.DISCOVERING, history.get(0).toState);
        assertEquals("Start", history.get(0).reason);
        
        // Check latest transition
        TransitionEvent lastEvent = history.get(history.size() - 1);
        assertEquals(State.CONNECTING, lastEvent.fromState);
        assertEquals(State.CONNECTED, lastEvent.toState);
    }

    @Test
    public void testTransitionListener() {
        ConnectionStateMachine sm = new ConnectionStateMachine(ConnectionStateMachine.Role.SENDER);
        AtomicInteger transitionCount = new AtomicInteger(0);
        
        sm.addTransitionListener(event -> transitionCount.incrementAndGet());
        
        sm.transitionTo(State.DISCOVERING, "Start");
        assertEquals(1, transitionCount.get());
        
        sm.transitionTo(State.CONNECTING, "Connect");
        assertEquals(2, transitionCount.get());
    }

    @Test
    public void testRemoveTransitionListener() {
        ConnectionStateMachine sm = new ConnectionStateMachine(ConnectionStateMachine.Role.SENDER);
        AtomicInteger count1 = new AtomicInteger(0);
        AtomicInteger count2 = new AtomicInteger(0);
        
        java.util.function.Consumer<TransitionEvent> listener1 = e -> count1.incrementAndGet();
        java.util.function.Consumer<TransitionEvent> listener2 = e -> count2.incrementAndGet();
        
        sm.addTransitionListener(listener1);
        sm.addTransitionListener(listener2);
        
        sm.transitionTo(State.DISCOVERING, "Start");
        assertEquals(1, count1.get());
        assertEquals(1, count2.get());
        
        sm.removeTransitionListener(listener1);
        
        sm.transitionTo(State.CONNECTING, "Connect");
        assertEquals(1, count1.get()); // Not incremented
        assertEquals(2, count2.get());
    }

    @Test
    public void testCanPerform() {
        ConnectionStateMachine sm = new ConnectionStateMachine(ConnectionStateMachine.Role.SENDER);
        
        assertTrue(sm.canPerform("startDiscovery"));
        assertFalse(sm.canPerform("connect"));
        
        sm.transitionTo(State.DISCOVERING, "Start");
        assertTrue(sm.canPerform("connect"));
        assertFalse(sm.canPerform("startStream"));
        
        sm.transitionTo(State.CONNECTING, "Connect");
        sm.transitionTo(State.CONNECTED, "Connected");
        assertTrue(sm.canPerform("startStream"));
        
        sm.transitionTo(State.STREAMING, "Stream");
        assertTrue(sm.canPerform("stopStream"));
        assertTrue(sm.canPerform("disconnect"));
    }

    @Test
    public void testReset() {
        ConnectionStateMachine sm = new ConnectionStateMachine(ConnectionStateMachine.Role.SENDER);
        
        sm.transitionTo(State.DISCOVERING, "Start");
        sm.transitionTo(State.CONNECTING, "Connect");
        
        sm.reset();
        
        assertEquals(State.IDLE, sm.getState());
    }

    @Test
    public void testForceState() {
        ConnectionStateMachine sm = new ConnectionStateMachine(ConnectionStateMachine.Role.SENDER);
        
        sm.forceState(State.CONNECTION_LOST);
        
        assertEquals(State.CONNECTION_LOST, sm.getState());
    }
}