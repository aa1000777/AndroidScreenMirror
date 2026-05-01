package com.screenshare.sdk.Common;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 原子化预分配缓冲区
 *
 * 目标：消除运行时的 ByteBuffer 动态分配，减少 GC 压力
 *
 * 设计：
 * - 一次性分配大块内存，循环使用
 * - 通过 AtomicInteger/AtomicLong 实现无锁读写位置管理
 */
public class AtomicBuffer {

    private final ByteBuffer buffer;
    private final int capacity;
    private final AtomicLong writePosition = new AtomicLong(0);
    private final AtomicLong readPosition = new AtomicLong(0);
    private final AtomicInteger refCount = new AtomicInteger(0);

    /**
     * 创建指定大小的原子缓冲区
     *
     * @param size 缓冲区大小（字节）
     */
    public AtomicBuffer(int size) {
        // 使用直接内存（堆外内存），减少 GC 压力
        this.buffer = ByteBuffer.allocateDirect(size);
        this.capacity = size;
    }

    /**
     * 获取底层 ByteBuffer
     */
    public ByteBuffer getBuffer() {
        return buffer;
    }

    /**
     * 获取容量
     */
    public int capacity() {
        return capacity;
    }

    /**
     * 重置读写位置（缓冲区复用时调用）
     */
    public void reset() {
        writePosition.set(0);
        readPosition.set(0);
        buffer.clear();
    }

    /**
     * 获取当前可写空间
     */
    public int availableToWrite() {
        long wp = writePosition.get();
        long rp = readPosition.get();
        return (int) (capacity - wp + rp);
    }

    /**
     * 获取当前可读数据量
     */
    public int availableToRead() {
        long wp = writePosition.get();
        long rp = readPosition.get();
        return (int) (wp - rp);
    }

    /**
     * 写入数据（返回实际写入的字节数）
     */
    public int write(byte[] data, int offset, int length) {
        int available = availableToWrite();
        if (available < length) {
            // 空间不足，尝试压缩或返回实际可用大小
            compact();
            available = availableToWrite();
            if (available < length) {
                length = available;
            }
        }

        if (length <= 0) {
            return 0;
        }

        long wp = writePosition.getAndAdd(length);
        buffer.position((int) wp);
        buffer.put(data, offset, length);

        return length;
    }

    /**
     * 读取数据（返回实际读取的字节数）
     */
    public int read(byte[] dest, int offset, int length) {
        int available = availableToRead();
        if (available <= 0) {
            return 0;
        }

        if (length > available) {
            length = available;
        }

        long rp = readPosition.getAndAdd(length);
        buffer.position((int) rp);
        buffer.get(dest, offset, length);

        return length;
    }

    /**
     * 压缩缓冲区（将未读数据移到开头）
     */
    public void compact() {
        int available = availableToRead();
        if (available <= 0) {
            reset();
            return;
        }

        long rp = readPosition.get();
        if (rp > 0) {
            buffer.position((int) rp);
            byte[] temp = new byte[available];
            buffer.get(temp, 0, available);

            buffer.clear();
            buffer.put(temp);

            readPosition.set(0);
            writePosition.set(available);
        }
    }

    /**
     * 增加引用计数
     */
    public int retain() {
        return refCount.incrementAndGet();
    }

    /**
     * 减少引用计数
     */
    public int release() {
        int count = refCount.decrementAndGet();
        if (count <= 0) {
            reset();
        }
        return count;
    }

    /**
     * 获取引用计数
     */
    public int getRefCount() {
        return refCount.get();
    }

    /**
     * 安全释放（引用计数归零时清空）
     */
    public void free() {
        refCount.set(0);
        reset();
    }
}