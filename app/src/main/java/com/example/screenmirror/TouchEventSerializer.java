package com.example.screenmirror;

import android.view.MotionEvent;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * 触摸事件序列化器
 * 将Android MotionEvent序列化为紧凑的二进制格式，支持网络传输
 * 关键优化：最小化数据大小，支持多指触控，时间戳对齐
 */
public class TouchEventSerializer {
    
    // 协议版本
    private static final int PROTOCOL_VERSION = 2;
    
    // 事件类型
    public static final int EVENT_DOWN = 0;
    public static final int EVENT_UP = 1;
    public static final int EVENT_MOVE = 2;
    public static final int EVENT_CANCEL = 3;
    public static final int EVENT_POINTER_DOWN = 4;
    public static final int EVENT_POINTER_UP = 5;
    
    // 最大支持的触摸点数
    private static final int MAX_TOUCH_POINTS = 5;
    
    // 数据包大小（字节）
    private static final int HEADER_SIZE = 20; // 协议头大小
    private static final int POINT_SIZE = 4;   // 每个触摸点大小（紧凑格式）
    
    // 屏幕分辨率（用于坐标归一化）
    private int screenWidth = 1920;
    private int screenHeight = 1080;
    
    /**
     * 设置屏幕分辨率（用于坐标归一化）
     */
    public void setScreenResolution(int width, int height) {
        this.screenWidth = Math.max(1, width);
        this.screenHeight = Math.max(1, height);
    }
    
    /**
     * 序列化单个触摸事件
     * 
     * @param event MotionEvent事件
     * @return 序列化的字节数组
     */
    public byte[] serialize(MotionEvent event) {
        // 获取触摸点数量
        int pointerCount = Math.min(event.getPointerCount(), MAX_TOUCH_POINTS);
        
        // 计算数据包大小
        int packetSize = HEADER_SIZE + (pointerCount * POINT_SIZE);
        ByteBuffer buffer = ByteBuffer.allocate(packetSize);
        buffer.order(ByteOrder.LITTLE_ENDIAN); // 使用小端序，兼容大部分设备
        
        // 写入协议头
        writeHeader(buffer, event, pointerCount);
        
        // 写入每个触摸点数据
        for (int i = 0; i < pointerCount; i++) {
            writeTouchPoint(buffer, event, i);
        }
        
        return buffer.array();
    }
    
    /**
     * 写入协议头
     */
    private void writeHeader(ByteBuffer buffer, MotionEvent event, int pointerCount) {
        // 协议版本和标志
        buffer.putShort((short) PROTOCOL_VERSION);
        
        // 事件类型
        int eventType = getEventType(event);
        buffer.put((byte) eventType);
        
        // 触摸点数量
        buffer.put((byte) pointerCount);
        
        // 时间戳（毫秒和纳秒部分）
        long eventTime = event.getEventTime();
        long nanoTime = System.nanoTime(); // 高精度时间戳用于延迟测量
        
        buffer.putLong(eventTime);
        buffer.putLong(nanoTime);
        
        // 保留字段（可用于扩展）
        buffer.putShort((short) 0);
    }
    
    /**
     * 写入单个触摸点数据（紧凑格式）
     * 使用2字节坐标 + 1字节压力 + 1字节指针ID = 4字节
     */
    private void writeTouchPoint(ByteBuffer buffer, MotionEvent event, int pointerIndex) {
        // 获取原始坐标
        float rawX = event.getX(pointerIndex);
        float rawY = event.getY(pointerIndex);
        
        // 归一化到0-65535范围（2字节精度）
        int normX = (int) ((rawX / screenWidth) * 65535);
        int normY = (int) ((rawY / screenHeight) * 65535);
        
        normX = Math.max(0, Math.min(65535, normX));
        normY = Math.max(0, Math.min(65535, normY));
        
        // 指针ID（0-255范围）
        int pointerId = event.getPointerId(pointerIndex) & 0xFF;
        
        // 压力值（0-255范围）
        float pressure = event.getPressure(pointerIndex);
        int normPressure = (int) (pressure * 255);
        normPressure = Math.max(0, Math.min(255, normPressure));
        
        // 写入紧凑数据（4字节）
        buffer.putShort((short) normX);
        buffer.putShort((short) normY);
        buffer.put((byte) normPressure);
        buffer.put((byte) pointerId);
    }
    
    /**
     * 获取事件类型
     */
    private int getEventType(MotionEvent event) {
        int action = event.getActionMasked();
        
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                return EVENT_DOWN;
                
            case MotionEvent.ACTION_UP:
                return EVENT_UP;
                
            case MotionEvent.ACTION_MOVE:
                return EVENT_MOVE;
                
            case MotionEvent.ACTION_CANCEL:
                return EVENT_CANCEL;
                
            case MotionEvent.ACTION_POINTER_DOWN:
                return EVENT_POINTER_DOWN;
                
            case MotionEvent.ACTION_POINTER_UP:
                return EVENT_POINTER_UP;
                
            default:
                return EVENT_MOVE; // 默认作为移动事件
        }
    }
    
    /**
     * 批量序列化触摸事件（用于减少网络开销）
     * 将多个触摸事件打包成一个数据包
     */
    public byte[] serializeBatch(MotionEvent[] events) {
        if (events == null || events.length == 0) {
            return new byte[0];
        }
        
        // 计算总大小
        int totalSize = HEADER_SIZE;
        for (MotionEvent event : events) {
            int pointerCount = Math.min(event.getPointerCount(), MAX_TOUCH_POINTS);
            totalSize += 4 + (pointerCount * POINT_SIZE); // 4字节事件头 + 触摸点数据
        }
        
        ByteBuffer buffer = ByteBuffer.allocate(totalSize);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        
        // 写入批量头
        buffer.putShort((short) PROTOCOL_VERSION);
        buffer.put((byte) 0xFF); // 批量标记
        buffer.put((byte) events.length); // 事件数量
        buffer.putLong(System.currentTimeMillis()); // 批次时间戳
        buffer.putInt(0); // 保留字段
        
        // 写入每个事件
        for (MotionEvent event : events) {
            serializeEventToBuffer(buffer, event);
        }
        
        return buffer.array();
    }
    
    /**
     * 将单个事件写入缓冲区（用于批量序列化）
     */
    private void serializeEventToBuffer(ByteBuffer buffer, MotionEvent event) {
        int pointerCount = Math.min(event.getPointerCount(), MAX_TOUCH_POINTS);
        
        // 事件头
        buffer.putInt(getEventType(event));
        buffer.putInt(pointerCount);
        buffer.putLong(event.getEventTime());
        
        // 触摸点数据
        for (int i = 0; i < pointerCount; i++) {
            writeTouchPointCompact(buffer, event, i);
        }
    }
    
    /**
     * 紧凑格式写入触摸点（用于批量序列化）
     */
    private void writeTouchPointCompact(ByteBuffer buffer, MotionEvent event, int pointerIndex) {
        // 使用更紧凑的格式：归一化坐标（2字节） + 压力（1字节） + 指针ID（1字节）
        
        float rawX = event.getX(pointerIndex);
        float rawY = event.getY(pointerIndex);
        
        // 归一化到0-65535范围（2字节精度）
        int normX = (int) ((rawX / screenWidth) * 65535);
        int normY = (int) ((rawY / screenHeight) * 65535);
        
        normX = Math.max(0, Math.min(65535, normX));
        normY = Math.max(0, Math.min(65535, normY));
        
        // 压力值（0-255范围）
        float pressure = event.getPressure(pointerIndex);
        int normPressure = (int) (pressure * 255);
        normPressure = Math.max(0, Math.min(255, normPressure));
        
        // 指针ID
        int pointerId = event.getPointerId(pointerIndex);
        
        // 写入紧凑数据（4字节）
        buffer.putShort((short) normX);
        buffer.putShort((short) normY);
        buffer.put((byte) normPressure);
        buffer.put((byte) pointerId);
    }
    
    /**
     * 反序列化触摸事件（接收端使用）
     */
    public TouchEventData deserialize(byte[] data) {
        if (data == null || data.length < HEADER_SIZE) {
            return null;
        }
        
        ByteBuffer buffer = ByteBuffer.wrap(data);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        
        // 读取协议头
        int version = buffer.getShort() & 0xFFFF;
        if (version != PROTOCOL_VERSION) {
            // 协议版本不兼容
            return null;
        }
        
        byte eventType = buffer.get();
        byte pointerCount = buffer.get();
        
        long eventTime = buffer.getLong();
        long nanoTime = buffer.getLong();
        
        short reserved = buffer.getShort();
        
        // 读取触摸点数据
        TouchPoint[] points = new TouchPoint[pointerCount];
        for (int i = 0; i < pointerCount; i++) {
            points[i] = readTouchPoint(buffer);
        }
        
        return new TouchEventData(eventType, pointerCount, eventTime, nanoTime, points);
    }
    
    /**
     * 读取触摸点数据（紧凑格式）
     */
    private TouchPoint readTouchPoint(ByteBuffer buffer) {
        // 读取紧凑格式数据（4字节）
        int normX = buffer.getShort() & 0xFFFF;
        int normY = buffer.getShort() & 0xFFFF;
        int normPressure = buffer.get() & 0xFF;
        int pointerId = buffer.get() & 0xFF;
        
        // 转换回浮点数
        float x = (float) normX / 65535.0f;
        float y = (float) normY / 65535.0f;
        float pressure = (float) normPressure / 255.0f;
        
        // 紧凑格式不支持触摸面积和方向，使用默认值
        return new TouchPoint(pointerId, x, y, pressure, 0.0f, 0.0f, 0.0f);
    }
    
    /**
     * 反序列化紧凑格式（用于批量数据）
     */
    public TouchEventData deserializeCompact(byte[] data, int offset) {
        ByteBuffer buffer = ByteBuffer.wrap(data, offset, data.length - offset);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        
        // 读取紧凑格式数据
        int normX = buffer.getShort() & 0xFFFF;
        int normY = buffer.getShort() & 0xFFFF;
        int normPressure = buffer.get() & 0xFF;
        int pointerId = buffer.get() & 0xFF;
        
        // 转换回浮点数
        float x = (float) normX / 65535.0f;
        float y = (float) normY / 65535.0f;
        float pressure = (float) normPressure / 255.0f;
        
        TouchPoint point = new TouchPoint(pointerId, x, y, pressure, 0, 0, 0);
        TouchPoint[] points = {point};
        
        return new TouchEventData(EVENT_MOVE, 1, System.currentTimeMillis(), 
                                 System.nanoTime(), points);
    }
    
    /**
     * 触摸事件数据类
     */
    public static class TouchEventData {
        public final int eventType;
        public final int pointerCount;
        public final long eventTime;
        public final long nanoTime;
        public final TouchPoint[] points;
        
        public TouchEventData(int eventType, int pointerCount, long eventTime, 
                             long nanoTime, TouchPoint[] points) {
            this.eventType = eventType;
            this.pointerCount = pointerCount;
            this.eventTime = eventTime;
            this.nanoTime = nanoTime;
            this.points = points;
        }
        
        /**
         * 计算端到端延迟（纳秒）
         */
        public long calculateLatency() {
            return System.nanoTime() - nanoTime;
        }
        
        /**
         * 获取主要触摸点（第一个点）
         */
        public TouchPoint getPrimaryPoint() {
            return points != null && points.length > 0 ? points[0] : null;
        }
    }
    
    /**
     * 触摸点数据类
     */
    public static class TouchPoint {
        public final int pointerId;
        public final float x;           // 归一化X坐标
        public final float y;           // 归一化Y坐标
        public final float pressure;    // 压力值
        public final float touchMajor;  // 触摸椭圆长轴
        public final float touchMinor;  // 触摸椭圆短轴
        public final float orientation; // 方向（弧度）
        
        public TouchPoint(int pointerId, float x, float y, float pressure,
                         float touchMajor, float touchMinor, float orientation) {
            this.pointerId = pointerId;
            this.x = x;
            this.y = y;
            this.pressure = pressure;
            this.touchMajor = touchMajor;
            this.touchMinor = touchMinor;
            this.orientation = orientation;
        }
        
        /**
         * 转换为实际屏幕坐标
         */
        public float getActualX(int screenWidth) {
            return x * screenWidth;
        }
        
        public float getActualY(int screenHeight) {
            return y * screenHeight;
        }
    }
    
    /**
     * 获取数据包大小信息
     */
    public static String getPacketInfo(byte[] data) {
        if (data == null) return "null";
        
        ByteBuffer buffer = ByteBuffer.wrap(data);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        
        try {
            int version = buffer.getShort() & 0xFFFF;
            byte eventType = buffer.get();
            byte pointerCount = buffer.get();
            
            return String.format("版本:%d 类型:%d 点数:%d 大小:%dB", 
                    version, eventType, pointerCount, data.length);
        } catch (Exception e) {
            return "无效数据包";
        }
    }
    
    /**
     * 验证数据包完整性
     */
    public static boolean validatePacket(byte[] data) {
        if (data == null || data.length < HEADER_SIZE) {
            return false;
        }
        
        ByteBuffer buffer = ByteBuffer.wrap(data);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        
        try {
            int version = buffer.getShort() & 0xFFFF;
            if (version != PROTOCOL_VERSION) {
                return false;
            }
            
            byte eventType = buffer.get();
            if (eventType < 0 || eventType > 5) {
                return false;
            }
            
            byte pointerCount = buffer.get();
            if (pointerCount < 0 || pointerCount > MAX_TOUCH_POINTS) {
                return false;
            }
            
            // 检查数据大小是否匹配
            int expectedSize = HEADER_SIZE + (pointerCount * POINT_SIZE);
            return data.length >= expectedSize;
            
        } catch (Exception e) {
            return false;
        }
    }
}