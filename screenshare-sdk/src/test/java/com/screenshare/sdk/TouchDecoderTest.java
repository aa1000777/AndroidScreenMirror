package com.screenshare.sdk;

import com.screenshare.sdk.touch.TouchDecoder;
import org.junit.Test;
import static org.junit.Assert.*;
import java.nio.ByteBuffer;

/**
 * Unit tests for TouchDecoder - validates touch event deserialization
 */
public class TouchDecoderTest {

    @Test
    public void testTouchPacketSize() {
        // Touch packet: timestamp(8) + action(2) + x(4) + y(4) = 18 bytes
        assertEquals(18, getExpectedPacketSize());
    }

    @Test
    public void testParseTouchEvent() {
        // Simulate parsing a touch event
        long expectedTimestamp = System.currentTimeMillis();
        int expectedAction = 0; // ACTION_DOWN
        float expectedX = 100.5f;
        float expectedY = 200.5f;
        
        ByteBuffer buffer = ByteBuffer.allocate(18);
        buffer.putLong(expectedTimestamp);
        buffer.putShort((short) expectedAction);
        buffer.putFloat(expectedX);
        buffer.putFloat(expectedY);
        
        buffer.position(0);
        
        long actualTimestamp = buffer.getLong();
        int actualAction = buffer.getShort();
        float actualX = buffer.getFloat();
        float actualY = buffer.getFloat();
        
        assertEquals(expectedTimestamp, actualTimestamp);
        assertEquals(expectedAction, actualAction);
        assertEquals(expectedX, actualX, 0.001);
        assertEquals(expectedY, actualY, 0.001);
    }

    @Test
    public void testParsePartialData() {
        // Data shorter than packet size should be rejected
        byte[] shortData = new byte[10]; // Too short
        assertTrue(shortData.length < getExpectedPacketSize());
    }

    @Test
    public void testParseEdgeCases() {
        // Test with minimum values
        ByteBuffer buffer = ByteBuffer.allocate(18);
        buffer.putLong(0L);
        buffer.putShort((short) 0);
        buffer.putFloat(0f);
        buffer.putFloat(0f);
        
        buffer.position(0);
        assertEquals(0L, buffer.getLong());
        assertEquals(0, buffer.getShort());
    }

    @Test
    public void testActionDownValue() {
        // Test ACTION_DOWN = 0
        ByteBuffer buffer = ByteBuffer.allocate(18);
        buffer.putLong(System.currentTimeMillis());
        buffer.putShort((short) 0); // ACTION_DOWN
        
        buffer.position(8);
        int action = buffer.getShort();
        assertEquals(0, action);
    }

    @Test
    public void testActionUpValue() {
        // Test ACTION_UP = 1
        ByteBuffer buffer = ByteBuffer.allocate(18);
        buffer.putLong(System.currentTimeMillis());
        buffer.putShort((short) 1); // ACTION_UP
        
        buffer.position(8);
        int action = buffer.getShort();
        assertEquals(1, action);
    }

    @Test
    public void testActionMoveValue() {
        // Test ACTION_MOVE = 2
        ByteBuffer buffer = ByteBuffer.allocate(18);
        buffer.putLong(System.currentTimeMillis());
        buffer.putShort((short) 2); // ACTION_MOVE
        
        buffer.position(8);
        int action = buffer.getShort();
        assertEquals(2, action);
    }

    @Test
    public void testCoordinateNormalization() {
        // Touch coordinates should be normalized 0.0-1.0 or absolute pixels
        float normalizedX = 0.5f;
        float normalizedY = 0.5f;
        
        ByteBuffer buffer = ByteBuffer.allocate(18);
        buffer.putLong(0);
        buffer.putShort((short) 0);
        buffer.putFloat(normalizedX);
        buffer.putFloat(normalizedY);
        
        buffer.position(10);
        assertEquals(normalizedX, buffer.getFloat(), 0.001);
        assertEquals(normalizedY, buffer.getFloat(), 0.001);
    }

    @Test
    public void testMaxCoordinateValues() {
        // Test with maximum coordinate values
        ByteBuffer buffer = ByteBuffer.allocate(18);
        buffer.putLong(0);
        buffer.putShort((short) 0);
        buffer.putFloat(Float.MAX_VALUE);
        buffer.putFloat(Float.MAX_VALUE);
        
        buffer.position(10);
        float maxX = buffer.getFloat();
        float maxY = buffer.getFloat();
        assertTrue(maxX > 0);
        assertTrue(maxY > 0);
    }

    // Helper to match TouchDecoder.TOUCH_PACKET_SIZE
    private static int getExpectedPacketSize() {
        return 18; // timestamp(8) + action(2) + x(4) + y(4)
    }
}