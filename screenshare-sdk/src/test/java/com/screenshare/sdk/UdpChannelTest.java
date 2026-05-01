package com.screenshare.sdk;

import com.screenshare.sdk.network.UdpChannel;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for UdpChannel - validates channel operations with mocks
 */
public class UdpChannelTest {

    @Test
    public void testUdpChannelCreation() {
        UdpChannel channel = new UdpChannel(8888);
        assertNotNull(channel);
    }

    @Test
    public void testSetPeerAddress() {
        UdpChannel channel = new UdpChannel(8888);
        
        // Should not throw
        channel.setPeerAddress("192.168.1.1");
        
        // With port
        channel.setPeerAddress("192.168.1.1", 8888);
    }

    @Test
    public void testSetMode() {
        UdpChannel channel = new UdpChannel(8888);
        
        // Default is not listener mode
        assertFalse(channel.isConnected());
        
        // Set to listener mode
        channel.setMode(true);
        
        // In listener mode, isConnected returns true
        assertTrue(channel.isConnected());
    }

    @Test
    public void testChannelState() {
        UdpChannel channel = new UdpChannel(8888);
        
        // Initially not connected
        assertFalse(channel.isConnected());
        
        // Set peer address with port to simulate connection
        channel.setPeerAddress("127.0.0.1", 8888);
        assertTrue(channel.isConnected());
        
        // Disconnect should set connected to false
        channel.disconnect();
        assertFalse(channel.isConnected());
    }

    @Test
    public void testInvalidAddressHandling() {
        UdpChannel channel = new UdpChannel(8888);
        
        // Set an invalid address - should not crash
        // The actual resolution happens in open()
        channel.setPeerAddress("invalid_address");
        
        // isConnected should still work
        assertFalse(channel.isConnected());
    }

    @Test
    public void testCloseWithoutOpen() {
        UdpChannel channel = new UdpChannel(8888);
        
        // Calling close without open should not crash
        channel.close();
        
        // State should be consistent
        assertFalse(channel.isConnected());
    }

    @Test
    public void testPortAssignment() {
        int port = 9876;
        UdpChannel channel = new UdpChannel(port);
        
        // Channel is created with port
        // The actual port binding happens in open()
        assertFalse(channel.isConnected());
    }
}