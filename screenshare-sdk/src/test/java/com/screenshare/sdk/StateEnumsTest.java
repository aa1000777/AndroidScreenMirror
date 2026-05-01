package com.screenshare.sdk;

import com.screenshare.sdk.Common.SenderState;
import com.screenshare.sdk.Common.ReceiverState;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for State enums - validates state values
 */
public class StateEnumsTest {

    @Test
    public void testSenderStateValues() {
        assertEquals(6, SenderState.values().length);
        
        assertEquals(SenderState.IDLE, SenderState.valueOf("IDLE"));
        assertEquals(SenderState.CAPTURING, SenderState.valueOf("CAPTURING"));
        assertEquals(SenderState.DISCOVERING, SenderState.valueOf("DISCOVERING"));
        assertEquals(SenderState.CONNECTING, SenderState.valueOf("CONNECTING"));
        assertEquals(SenderState.STREAMING, SenderState.valueOf("STREAMING"));
        assertEquals(SenderState.CONNECTION_LOST, SenderState.valueOf("CONNECTION_LOST"));
    }

    @Test
    public void testReceiverStateValues() {
        assertEquals(3, ReceiverState.values().length);
        
        assertEquals(ReceiverState.LISTENING, ReceiverState.valueOf("LISTENING"));
        assertEquals(ReceiverState.ACCEPTED, ReceiverState.valueOf("ACCEPTED"));
        assertEquals(ReceiverState.STREAMING, ReceiverState.valueOf("STREAMING"));
    }

    @Test
    public void testSenderStateOrdinals() {
        assertEquals(0, SenderState.IDLE.ordinal());
        assertEquals(1, SenderState.CAPTURING.ordinal());
        assertEquals(2, SenderState.DISCOVERING.ordinal());
        assertEquals(3, SenderState.CONNECTING.ordinal());
        assertEquals(4, SenderState.STREAMING.ordinal());
        assertEquals(5, SenderState.CONNECTION_LOST.ordinal());
    }

    @Test
    public void testReceiverStateOrdinals() {
        assertEquals(0, ReceiverState.LISTENING.ordinal());
        assertEquals(1, ReceiverState.ACCEPTED.ordinal());
        assertEquals(2, ReceiverState.STREAMING.ordinal());
    }

    @Test
    public void testStateToString() {
        assertEquals("IDLE", SenderState.IDLE.toString());
        assertEquals("LISTENING", ReceiverState.LISTENING.toString());
        assertEquals("STREAMING", SenderState.STREAMING.toString());
        assertEquals("STREAMING", ReceiverState.STREAMING.toString());
    }
}