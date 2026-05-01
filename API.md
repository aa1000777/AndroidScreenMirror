# Screenshare SDK API 文档

> 版本: 1.0.0 | 最后更新: 2026-05-01

---

## 目录

1. [ScreenshareSDK](#1-screensharesdk)
2. [Sender](#2-sender)
3. [Receiver](#3-receiver)
4. [SenderConfig](#4-senderconfig)
5. [ReceiverConfig](#5-receiverconfig)
6. [状态机](#6-状态机)
7. [错误码](#7-错误码)

---

## 1. ScreenshareSDK

SDK 唯一入口类。

### 方法

| 方法 | 返回值 | 说明 |
|------|--------|------|
| `createSender(Context, SenderConfig)` | `Sender` | 创建发送端实例 |
| `createReceiver(Context, ReceiverConfig)` | `Receiver` | 创建接收端实例 |
| `getVersion()` | `String` | 获取版本名称 "1.0.0" |
| `getVersionCode()` | `int` | 获取版本码 100 |

### 示例

```java
// 创建发送端
SenderConfig config = new SenderConfig();
config.width = 1920;
config.height = 1080;
config.fps = 60;
Sender sender = ScreenshareSDK.createSender(context, config);

// 创建接收端
ReceiverConfig rc = new ReceiverConfig();
Receiver receiver = ScreenshareSDK.createReceiver(context, rc);
```

---

## 2. Sender

发送端，负责屏幕采集、编码、传输。

### 构造

```
Sender(Context context, SenderConfig config)
```

**注意**: 请通过 `ScreenshareSDK.createSender()` 创建实例。

### 方法

| 方法 | 返回值 | 说明 |
|------|--------|------|
| `startCapture()` | `void` | 开始屏幕采集和编码 |
| `stopCapture()` | `void` | 停止屏幕采集 |
| `release()` | `void` | 释放所有资源 |
| `connect(String peerAddress)` | `void` | 连接到对等设备 |
| `disconnect()` | `void` | 断开连接 |
| `sendTouchEvent(long, int, float, float)` | `void` | 发送触摸事件 |
| `getConfig()` | `SenderConfig` | 获取配置 |
| `getState()` | `SenderState` | 获取当前状态 |
| `setEventListener(EventListener)` | `void` | 设置事件监听 |

### 事件监听器

```java
public interface EventListener {
    void onStateChanged(SenderState state);    // 状态变化
    void onError(int errorCode, String msg);   // 错误
    void onConnected(String peerAddress);       // 连接成功
    void onDisconnected();                      // 连接断开
    void onFrameSent(long timestamp, int size); // 帧发送完成
}
```

### 状态转换

```
IDLE → startCapture() → CAPTURING
CAPTURING → 发现设备 → DISCOVERING
DISCOVERING → connect() → CONNECTING
CONNECTING → 连接成功 → STREAMING
STREAMING → 断开 → CONNECTION_LOST（自动重连）
CONNECTION_LOST → 重连成功 → STREAMING
任意状态 → stopCapture()/release() → IDLE
```

### 示例

```java
Sender sender = ScreenshareSDK.createSender(context, config);
sender.setEventListener(new Sender.EventListener() {
    @Override
    public void onConnected(String peerAddress) {
        Log.d(TAG, "已连接到 " + peerAddress);
    }
    
    @Override
    public void onFrameSent(long timestamp, int size) {
        // 可用于帧率统计
    }
});

sender.startCapture();
sender.connect("192.168.49.1");
```

---

## 3. Receiver

接收端，负责接收、解码、渲染、触摸注入。

### 构造

```
Receiver(Context context, ReceiverConfig config)
```

**注意**: 请通过 `ScreenshareSDK.createReceiver()` 创建实例。

### 方法

| 方法 | 返回值 | 说明 |
|------|--------|------|
| `startListening()` | `void` | 开始监听（作为服务器）|
| `stopListening()` | `void` | 停止监听 |
| `release()` | `void` | 释放所有资源 |
| `getConfig()` | `ReceiverConfig` | 获取配置 |
| `getState()` | `ReceiverState` | 获取当前状态 |
| `setEventListener(EventListener)` | `void` | 设置事件监听 |

### 事件监听器

```java
public interface EventListener {
    void onStateChanged(ReceiverState state);
    void onError(int errorCode, String msg);
    void onFrameReceived(long timestamp, int size);
    void onTouchEventReceived(long timestamp, int action, float x, float y);
    void onConnected(String senderAddress);
    void onDisconnected();
}
```

### 状态转换

```
IDLE → startListening() → LISTENING
LISTENING → 接受连接 → ACCEPTED
ACCEPTED → 开始接收 → STREAMING
STREAMING → 断开 → LISTENING
任意状态 → stopListening()/release() → IDLE
```

### 示例

```java
Receiver receiver = ScreenshareSDK.createReceiver(context, config);
receiver.setEventListener(new Receiver.EventListener() {
    @Override
    public void onTouchEventReceived(long timestamp, int action, float x, float y) {
        // 注入触摸事件到系统
        TouchInjectorService.injectTouch(timestamp, action, x, y, 1f, 1f);
    }
});

receiver.startListening();
```

---

## 4. SenderConfig

发送端配置类。

### 字段

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `width` | `int` | 1920 | 视频宽度 |
| `height` | `int` | 1080 | 视频高度 |
| `fps` | `int` | 60 | 帧率 |
| `videoBitrate` | `int` | 0 | 视频码率（0=自动）|
| `keyFrameInterval` | `int` | 2 | 关键帧间隔（秒）|
| `videoCodec` | `VideoCodecType` | H264_HARDWARE | 编码器类型 |
| `withAudio` | `boolean` | false | 是否包含音频 |
| `audioBitrate` | `int` | 128000 | 音频码率 |
| `videoPort` | `int` | 8888 | 视频端口（UDP）|
| `touchPort` | `int` | 8889 | 触摸端口（UDP）|
| `maxReconnect` | `int` | 3 | 最大重连次数 |
| `connectTimeout` | `int` | 5000 | 连接超时（毫秒）|
| `performanceMode` | `boolean` | false | 性能模式 |
| `performancePreset` | `PerformancePreset` | BALANCED | 性能预设 |
| `sendBufferSize` | `int` | 512*1024 | 发送缓冲区大小 |

### VideoCodecType 枚举

```java
public enum VideoCodecType {
    H264_HARDWARE,   // 硬件 H264（MediaCodec）
    H265_HARDWARE,   // 硬件 H265（MediaCodec）
    VP8,             // 软件 VP8
    VP9              // 软件 VP9
}
```

### PerformancePreset 枚举

```java
public enum PerformancePreset {
    LOW_LATENCY,     // 超低延迟（<50ms）
    BALANCED,        // 平衡（~100ms）
    HIGH_QUALITY     // 高质量（>200ms）
}
```

---

## 5. ReceiverConfig

接收端配置类。

### 字段

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `width` | `int` | 1920 | 期望接收的视频宽度 |
| `height` | `int` | 1080 | 期望接收的视频高度 |
| `videoCodec` | `VideoCodecType` | H264_HARDWARE | 视频解码器类型 |
| `withAudio` | `boolean` | false | 是否接收音频 |
| `listenVideoPort` | `int` | 8888 | 监听端口（UDP）|
| `listenTouchPort` | `int` | 8889 | 触摸监听端口（UDP）|
| `maxReconnect` | `int` | 3 | 最大重连次数 |
| `connectTimeout` | `int` | 5000 | 连接超时（毫秒）|
| `receiveBufferSize` | `int` | 512*1024 | 接收缓冲区大小 |
| `lowLatencyMode` | `boolean` | true | 低延迟模式 |
| `maxFrameQueue` | `int` | 2 | 帧队列最大长度 |

---

## 6. 状态机

### SenderState（发送端状态）

| 状态 | 说明 |
|------|------|
| `IDLE` | 空闲 |
| `CAPTURING` | 正在采集屏幕 |
| `DISCOVERING` | 正在发现设备 |
| `CONNECTING` | 正在连接 |
| `STREAMING` | 正在推流 |
| `CONNECTION_LOST` | 连接丢失（可重连）|

### ReceiverState（接收端状态）

| 状态 | 说明 |
|------|------|
| `LISTENING` | 监听中（等待连接）|
| `ACCEPTED` | 已接受连接 |
| `STREAMING` | 正在接收流 |

---

## 7. 错误码

| 范围 | 类别 | 说明 |
|------|------|------|
| 1001-1999 | 权限/配置 | 权限拒绝、配置无效、状态错误 |
| 2001-2999 | 屏幕采集 | 采集启动失败、MediaProjection 拒绝 |
| 3001-3999 | 编码/解码 | 编码器/解码器初始化失败、超时 |
| 4001-4999 | 网络传输 | 连接失败、网络断开、UDP 绑定失败 |
| 5001-5999 | WiFi P2P | P2P 不可用、发现失败、连接失败 |

### 常用错误码

| 错误码 | 常量 | 说明 |
|--------|------|------|
| 1001 | `ERR_PERMISSION_DENIED` | 权限被拒绝 |
| 1004 | `ERR_INVALID_STATE` | 状态无效（操作不允许）|
| 2001 | `ERR_CAPTURE_START_FAILED` | 屏幕采集启动失败 |
| 2002 | `ERR_MEDIA_PROJECTION_DENIED` | MediaProjection 权限被拒绝 |
| 3001 | `ERR_ENCODER_INIT_FAILED` | 编码器初始化失败 |
| 3002 | `ERR_DECODER_INIT_FAILED` | 解码器初始化失败 |
| 4001 | `ERR_CONNECTION_FAILED` | 网络连接失败 |
| 4004 | `ERR_UDP_BIND_FAILED` | UDP 端口绑定失败 |
| 5001 | `ERR_WIFI_P2P_UNAVAILABLE` | WiFi P2P 不可用 |

### 获取错误描述

```java
String desc = ErrorCode.getDescription(errorCode);
// "权限被拒绝"
```

---

## 工具类

### AtomicBuffer

GC 优化的预分配缓冲区。

```java
AtomicBuffer buffer = new AtomicBuffer(512 * 1024);

// 写入
byte[] data = ...;
buffer.write(data, 0, data.length);

// 读取
byte[] read = new byte[1024];
int len = buffer.read(read, 0, read.length);

// 压缩（移动未读数据到开头）
buffer.compact();

// 重置
buffer.reset();
```

### LatencyTester

延迟测试工具。

```java
LatencyTester tester = new LatencyTester();

// 记录发送时间
tester.recordSend(timestamp);

// 记录接收时间
tester.recordReceive(timestamp);

// 获取统计
long min = tester.getMinRtt();
long max = tester.getMaxRtt();
long avg = tester.getAvgRtt();
double loss = tester.getLossRate();  // 0.0 ~ 100.0
```

### HeartbeatMonitor

心跳监控。

```java
HeartbeatMonitor monitor = new HeartbeatMonitor(5000); // 5秒间隔
monitor.setEventListener(new HeartbeatMonitor.EventListener() {
    @Override
    public void onTimeout() {
        // 3次超时，触发重连
    }
});
monitor.start();
monitor.notifyAlive();  // 收到心跳时调用
monitor.stop();
```

### BandwidthAdapter

带宽自适应。

```java
BandwidthAdapter adapter = new BandwidthAdapter(8 * 1000 * 1000); // 8Mbps
adapter.setEventListener(new BandwidthAdapter.EventListener() {
    @Override
    public void onBitrateChanged(int newBitrate) {
        Log.d(TAG, "码率调整为: " + newBitrate);
    }
});

// 上报丢包率
adapter.reportLossRate(5.0);  // 5% 丢包

// 获取建议码率
int suggested = adapter.getSuggestedBitrate();
```

---

*文档版本: 1.0.0*
*最后更新: 2026-05-01*
