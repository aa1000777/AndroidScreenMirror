package com.screenshare.sdk;

import com.screenshare.sdk.Common.AtomicBuffer;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for AtomicBuffer - tests buffer operations and thread safety
 */
public class AtomicBufferTest {

    @Test
    public void testInitialState() {
        AtomicBuffer buffer = new AtomicBuffer(1024);
        
        assertEquals(1024, buffer.capacity());
        assertEquals(1024, buffer.availableToWrite());
        assertEquals(0, buffer.availableToRead());
    }

    @Test
    public void testWriteAndRead() {
        AtomicBuffer buffer = new AtomicBuffer(1024);
        byte[] data = "Hello".getBytes();
        
        int written = buffer.write(data, 0, data.length);
        assertEquals(5, written);
        assertEquals(5, buffer.availableToRead());
        assertEquals(1024 - 5, buffer.availableToWrite());
        
        byte[] read = new byte[5];
        int readCount = buffer.read(read, 0, 5);
        assertEquals(5, readCount);
        assertArrayEquals(data, read);
    }

    @Test
    public void testWritePartialWhenBufferFull() {
        AtomicBuffer buffer = new AtomicBuffer(10);
        byte[] data = "Hello World!".getBytes(); // 12 bytes
        
        int written = buffer.write(data, 0, data.length);
        assertEquals(10, written); // Only 10 bytes fit
        assertEquals(10, buffer.availableToRead());
    }

    @Test
    public void testCompact() {
        AtomicBuffer buffer = new AtomicBuffer(20);
        byte[] data = "Hello".getBytes();
        
        buffer.write(data, 0, data.length);
        buffer.read(data, 0, 3); // Read 3 bytes
        
        assertEquals(2, buffer.availableToRead());
        assertEquals(18, buffer.availableToWrite()); // 20 - 2 written + 3 read
        
        buffer.compact();
        
        assertEquals(2, buffer.availableToRead());
        assertEquals(18, buffer.availableToWrite());
    }

    @Test
    public void testReset() {
        AtomicBuffer buffer = new AtomicBuffer(100);
        byte[] data = "Test".getBytes();
        
        buffer.write(data, 0, data.length);
        assertEquals(4, buffer.availableToRead());
        
        buffer.reset();
        
        assertEquals(0, buffer.availableToRead());
        assertEquals(100, buffer.availableToWrite());
    }

    @Test
    public void testRefCounting() {
        AtomicBuffer buffer = new AtomicBuffer(100);
        
        assertEquals(0, buffer.getRefCount());
        
        buffer.retain();
        assertEquals(1, buffer.getRefCount());
        
        buffer.retain();
        assertEquals(2, buffer.getRefCount());
        
        buffer.release();
        assertEquals(1, buffer.getRefCount());
        
        buffer.release();
        assertEquals(0, buffer.getRefCount());
    }

    @Test
    public void testFree() {
        AtomicBuffer buffer = new AtomicBuffer(100);
        byte[] data = "Test".getBytes();
        
        buffer.retain();
        buffer.retain();
        buffer.write(data, 0, data.length);
        
        buffer.free();
        
        assertEquals(0, buffer.getRefCount());
        assertEquals(0, buffer.availableToRead());
    }

    @Test
    public void testCircularWrite() {
        AtomicBuffer buffer = new AtomicBuffer(10);
        byte[] data = new byte[10];
        
        // Write 5 bytes
        for (int i = 0; i < 5; i++) data[i] = (byte) i;
        buffer.write(data, 0, 5);
        
        // Read 3 bytes
        buffer.read(data, 0, 3);
        
        // Write 5 more bytes (should trigger compact)
        for (int i = 0; i < 5; i++) data[i] = (byte) (i + 10);
        buffer.write(data, 0, 5);
        
        // Should have 7 bytes available to read (5 - 3 + 5)
        assertEquals(7, buffer.availableToRead());
    }

    @Test
    public void testZeroLengthWrite() {
        AtomicBuffer buffer = new AtomicBuffer(100);
        byte[] data = "Hello".getBytes();
        
        int written = buffer.write(data, 0, 0);
        assertEquals(0, written);
        assertEquals(0, buffer.availableToRead());
    }

    @Test
    public void testZeroLengthRead() {
        AtomicBuffer buffer = new AtomicBuffer(100);
        byte[] writeData = "Hello".getBytes();
        buffer.write(writeData, 0, writeData.length);
        
        byte[] readData = new byte[10];
        int read = buffer.read(readData, 0, 0);
        assertEquals(0, read);
        assertEquals(5, buffer.availableToRead()); // Data still there
    }
}