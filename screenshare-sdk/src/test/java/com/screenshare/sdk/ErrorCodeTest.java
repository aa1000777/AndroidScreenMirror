package com.screenshare.sdk;

import com.screenshare.sdk.Common.ErrorCode;
import static org.junit.Assert.*;
import org.junit.Test;

/**
 * Unit tests for ErrorCode - validates error code descriptions
 */
public class ErrorCodeTest {

    @Test
    public void testPermissionErrors() {
        assertEquals("Permission denied", ErrorCode.getDescription(ErrorCode.ERR_PERMISSION_DENIED));
        assertEquals("Context is null", ErrorCode.getDescription(ErrorCode.ERR_CONTEXT_NULL));
        assertEquals("Invalid configuration", ErrorCode.getDescription(ErrorCode.ERR_CONFIG_INVALID));
        assertEquals("Invalid state", ErrorCode.getDescription(ErrorCode.ERR_INVALID_STATE));
    }

    @Test
    public void testCaptureErrors() {
        assertEquals("Screen capture start failed", ErrorCode.getDescription(ErrorCode.ERR_CAPTURE_START_FAILED));
        assertEquals("MediaProjection denied", ErrorCode.getDescription(ErrorCode.ERR_MEDIA_PROJECTION_DENIED));
        assertEquals("Capture not initialized", ErrorCode.getDescription(ErrorCode.ERR_CAPTURE_NOT_INIT));
        assertEquals("VirtualDisplay failed", ErrorCode.getDescription(ErrorCode.ERR_VIRTUAL_DISPLAY_FAILED));
    }

    @Test
    public void testCodecErrors() {
        assertEquals("Encoder init failed", ErrorCode.getDescription(ErrorCode.ERR_ENCODER_INIT_FAILED));
        assertEquals("Decoder init failed", ErrorCode.getDescription(ErrorCode.ERR_DECODER_INIT_FAILED));
        assertEquals("Encoder timeout", ErrorCode.getDescription(ErrorCode.ERR_ENCODER_TIMEOUT));
        assertEquals("Decoder timeout", ErrorCode.getDescription(ErrorCode.ERR_DECODER_TIMEOUT));
        assertEquals("Codec not supported", ErrorCode.getDescription(ErrorCode.ERR_CODEC_NOT_SUPPORTED));
    }

    @Test
    public void testNetworkErrors() {
        assertEquals("Connection failed", ErrorCode.getDescription(ErrorCode.ERR_CONNECTION_FAILED));
        assertEquals("Connection lost", ErrorCode.getDescription(ErrorCode.ERR_CONNECTION_LOST));
        assertEquals("Connection timeout", ErrorCode.getDescription(ErrorCode.ERR_CONNECTION_TIMEOUT));
        assertEquals("UDP bind failed", ErrorCode.getDescription(ErrorCode.ERR_UDP_BIND_FAILED));
        assertEquals("Network start failed", ErrorCode.getDescription(ErrorCode.ERR_NETWORK_START_FAILED));
        assertEquals("RTP session failed", ErrorCode.getDescription(ErrorCode.ERR_RTP_SESSION_FAILED));
    }

    @Test
    public void testWifiP2PErrors() {
        assertEquals("WiFi P2P unavailable", ErrorCode.getDescription(ErrorCode.ERR_WIFI_P2P_UNAVAILABLE));
        assertEquals("WiFi P2P enable failed", ErrorCode.getDescription(ErrorCode.ERR_WIFI_P2P_ENABLE_FAILED));
        assertEquals("Service discovery failed", ErrorCode.getDescription(ErrorCode.ERR_SERVICE_DISCOVERY_FAILED));
        assertEquals("P2P connect failed", ErrorCode.getDescription(ErrorCode.ERR_P2P_CONNECT_FAILED));
        assertEquals("Discoverer start failed", ErrorCode.getDescription(ErrorCode.ERR_DISCOVERER_START_FAILED));
    }

    @Test
    public void testUnknownError() {
        assertEquals("Unknown error: 0", ErrorCode.getDescription(0));
        assertEquals("Unknown error: 12345", ErrorCode.getDescription(12345));
        assertEquals("Unknown error: -1", ErrorCode.getDescription(-1));
    }

    @Test
    public void testErrorCodeValues() {
        // Permission errors should be in 1xxx range
        assertTrue(ErrorCode.ERR_PERMISSION_DENIED >= 1001 && ErrorCode.ERR_PERMISSION_DENIED < 2000);
        assertTrue(ErrorCode.ERR_CONTEXT_NULL >= 1001 && ErrorCode.ERR_CONTEXT_NULL < 2000);
        
        // Capture errors should be in 2xxx range
        assertTrue(ErrorCode.ERR_CAPTURE_START_FAILED >= 2001 && ErrorCode.ERR_CAPTURE_START_FAILED < 3000);
        
        // Codec errors should be in 3xxx range
        assertTrue(ErrorCode.ERR_ENCODER_INIT_FAILED >= 3001 && ErrorCode.ERR_ENCODER_INIT_FAILED < 4000);
        
        // Network errors should be in 4xxx range
        assertTrue(ErrorCode.ERR_CONNECTION_FAILED >= 4001 && ErrorCode.ERR_CONNECTION_FAILED < 5000);
        
        // WiFi P2P errors should be in 5xxx range
        assertTrue(ErrorCode.ERR_WIFI_P2P_UNAVAILABLE >= 5001 && ErrorCode.ERR_WIFI_P2P_UNAVAILABLE < 6000);
    }
}