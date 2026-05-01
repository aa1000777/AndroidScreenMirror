# AndroidScreenMirror 技术设计文档

> 📅 版本: 1.0.0 | 更新: 2026-05-01

---

## 一、系统架构设计

### 1.1 整体架构

```
┌─────────────────────────────────────────────────────────────────┐
│                      AndroidScreenMirror                        │
├─────────────────────────────┬───────────────────────────────────┤
│     screenshare-sdk         │         screenshare-app           │
│     (AAR Library)           │         (Main Application)        │
├─────────────────────────────┼───────────────────────────────────┤
│  ┌───────────────────────┐  │  ┌─────────────────────────────┐  │
│  │    ScreenshareSDK     │  │  │       MainActivity          │  │
│  │    (Entry Point)      │  │  │       SenderActivity        │  │
│  └───────────┬───────────┘  │  │       ReceiverActivity      │  │
│              │              │  │       SettingsActivity       │  │
│  ┌───────────┴───────────┐  │  └──────────────┬──────────────┘  │
│  │                       │  │                 │                  │
│  ▼                       ▼  │                 ▼                  │
│ ┌────────┐         ┌────────┐│           ┌─────────┐             │
│ │ Sender │         │Receiver││           │  UI     │             │
│ └────┬───┘         └───┬────┘│           │ Layer   │             │
│      │                 │    │           └─────────┘             │
│ ┌────┴─────────────────┴────┐                                   │
│ │      Shared Components     │                                   │
│ │  Common | Codec | Network   │                                   │
│ │  Touch  | WiFi  | Capture  │                                   │
│ └────────────────────────────┘                                   │
└─────────────────────────────────────────────────────────────────┘
```

### 1.2 模块划分

| 模块 | 路径 | 职责 |
|------|------|------|
| **SDK Core** | `ScreenshareSDK.java` | 唯一入口，工厂方法 |
| **Sender** | `Sender.java` | 发送端状态机 |
| **Receiver** | `Receiver.java` | 接收端状态机 |
| **Common** | `Common/*.java` | 通用组件（9个）|
| **Codec** | `codec/*.java` | 音视频编解码（7个）|
| **Network** | `network/*.java` | UDP/RTP 传输（2个）|
| **Capture** | `capture/*.java` | 屏幕采集（1个）|
| **Service** | `service/*.java` | 前台服务（2个）|
| **Touch** | `touch/*.java` | 触摸注入（3个）|
| **WiFi** | `wifi/*.java` | P2P 管理（3个）|

### 1.3 SDK 与 App 关系

```
App (screenshare-app)
    │
    ├── imports ScreenshareSDK (AAR)
    │
    ▼
ScreenshareSDK.createSender(context, config) / createReceiver(...)
    │
    ▼
┌───────────────────────────────────────┐
│         screenshare-sdk               │
│                                       │
│  Sender ←──→ Receiver                 │
│     │              │                  │
│     └── Common ────┘                  │
│     └── Codec ────┘                  │
│     └── Network ──┘                   │
│     └── Touch ──┘                     │
│     └── WiFi ───┘                     │
└───────────────────────────────────────┘
```

---

## 二、核心模块设计

### 2.1 屏幕采集模块

**类**: `ScreenCapturer.java`

**职责**: 通过 MediaProjection API 采集屏幕内容

**架构**:
```
MediaProjectionManager
        ↓ requestProjection
Intent (user grant)
        ↓
MediaProjection
        ↓ createVirtualDisplay
VirtualDisplay (mirrored screen)
        ↓
ImageReader (Surface)
        ↓
VideoEncoder (input Surface)
```

**关键实现**:
```java
// 1. 请求权限
Intent intent = mediaProjectionManager.createScreenCaptureIntent();
activity.startActivityForResult(intent, REQUEST_CODE);

// 2. 创建 VirtualDisplay
VirtualDisplay virtualDisplay = mediaProjection.createVirtualDisplay(
    "ScreenMirror",
    width, height, density,
    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
    surface,  // VideoEncoder's input surface
    null, handler
);

// 3. 释放资源
virtualDisplay.release();
mediaProjection.stop();
```

**API**:
```java
public class ScreenCapturer {
    public void startCapture(int width, int height, int fps);  // 启动采集
    public void stopCapture();                                  // 停止采集
    public Surface getInputSurface();                          // 获取编码器输入 Surface
    public boolean isCapturing();                              // 采集状态
}
```

---

### 2.2 视频编码模块

**类**: `VideoEncoder.java`

**职责**: MediaCodec 硬件 H.264/H.265 编码

**编码参数**:
| 参数 | 值 | 说明 |
|------|-----|------|
| MIME | video/avc 或 video/hevc | H.264 / H.265 |
| BITRATE_MODE | CBR | 恒定码率 |
| KEY_LATENCY | 0 | 低延迟模式（Android Q+）|
| PROFILE | HIGH | H.264 High Profile |
| LEVEL | 4.1 | |
| I-FRAME INTERVAL | 2s | 关键帧间隔 |

**关键实现**:
```java
MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, width, height);
format.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
format.setInteger(MediaFormat.KEY_FRAME_RATE, fps);
format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2);

// 低延迟（Android 10+）
format.setInteger(MediaFormat.KEY_LATENCY, 0);
format.setInteger(MediaFormat.KEY_PRIORITY, 0);  // 实时优先

encoder = MediaCodec.createEncoderByType(MIME_TYPE);
encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
encoder.start();

// 编码回调
encoder.setCallback(new MediaCodec.Callback() {
    @Override
    public void onOutputBufferAvailable(MediaCodec mc, int idx,
            BufferInfo info) {
        // 处理编码后的 NAL Unit
        ByteBuffer buffer = mc.getOutputBuffer(idx);
        // ... 送到 RTP Session
    }
}, handler);
```

**FU-A 分片**:
- RTP 最大负载: 1400 bytes
- 大于阈值的 NAL 单元使用 FU-A 分片
- 每个分片: 2 bytes header + payload

---

### 2.3 RTP 传输设计

**类**: `RtpSession.java`

**协议格式**:
```
RTP Header (12 bytes)
├── V (2 bits): RTP version
├── P (1 bit): Padding
├── X (1 bit): Extension
├── CC (4 bits): CSRC count
├── M (1 bit): Marker
├── PT (7 bits): Payload type (96 = H.264)
└── Sequence (16 bits): 包序号

NAL Unit Structure:
├── Single NAL: [NAL Header] [payload]           (< 1400 bytes)
└── FU-A Fragment: [FU Indicator] [FU Header] [payload]
    ├── FU Indicator: [F=0, NRI, Type=28]
    └── FU Header: [S, E, R, Type]
```

**关键实现**:
```java
public class RtpSession {
    // 发送端：打包 NAL
    public void sendNal(byte[] nal, int offset, int length);
    
    // 接收端：解析 RTP 包
    public RtpPacket parseRtpPacket(byte[] data, int length);
    
    // 获取并清理缓存的完整帧
    public byte[] assembleFrame();
}
```

**端口分配**:
| 通道 | 端口 | 协议 |
|------|------|------|
| 视频 | 8888 | UDP + RTP |
| 触摸 | 8889 | UDP (二进制) |

---

### 2.4 触摸事件设计

**类**: `TouchEncoder.java` / `TouchDecoder.java`

**二进制协议** (18 bytes/事件):
```
┌─────────────────────────────────────────────────┐
│ Field      │ Size  │ Type   │ Description       │
├─────────────────────────────────────────────────┤
│ timestamp  │ 8     │ long   │ 毫秒级时间戳       │
│ action     │ 2     │ short  │ MotionEvent action│
│ x          │ 4     │ float  │ X 坐标（归一化）   │
│ y          │ 4     │ float  │ Y 坐标（归一化）   │
└─────────────────────────────────────────────────┘
```

**触摸类型映射**:
| MotionEvent | Value |
|-------------|-------|
| ACTION_DOWN | 0 |
| ACTION_UP | 1 |
| ACTION_MOVE | 2 |
| ACTION_CANCEL | 3 |

**TouchEncoder**:
```java
public class TouchEncoder {
    private final ByteBuffer buffer = ByteBuffer.allocate(18);
    
    public byte[] encode(long timestamp, int action, float x, float y) {
        buffer.clear();
        buffer.putLong(timestamp);
        buffer.putShort((short) action);
        buffer.putFloat(x);
        buffer.putFloat(y);
        return buffer.array();
    }
}
```

**TouchInjectorService**:
```java
// AccessibilityService 注入
public class TouchInjectorService extends AccessibilityService {
    public static void injectTouch(long timestamp, int action, 
            float x, float y, float pressure, float size) {
        MotionEvent event = MotionEvent.obtain(
            timestamp, timestamp, action,
            x * screenWidth, y * screenHeight,
            pressure, size, 0, 1, 1, 0, 0
        );
        injectInputEvent(event, 0);
        event.recycle();
    }
}
```

---

### 2.5 状态机设计

**SenderState** (发送端):
```
        ┌──────────────────────────────────────────┐
        │                                          │
        ▼                                          │
    IDLE ──→ CAPTURING ──→ DISCOVERING ──→ CONNECTING
        │         │              │              │
        │         └──────────────┴──────────────┤
        │                                      ▼
        │                              CONNECTION_LOST
        │                                      │
        └──────────────────────────────────────┴──→ STREAMING
                                                        │
                                                    disconnect / stopCapture
                                                        │
                                                        ▼
                                                       IDLE
```

| 状态 | 说明 |
|------|------|
| IDLE | 空闲，未开始 |
| CAPTURING | 正在采集屏幕 |
| DISCOVERING | 正在发现 WiFi P2P 设备 |
| CONNECTING | 正在连接对等设备 |
| STREAMING | 正在推流 |
| CONNECTION_LOST | 连接丢失（自动重连）|

**ReceiverState** (接收端):
```
IDLE → LISTENING → ACCEPTED → STREAMING
  ↑        │                      │
  └────────┴──────────────────────┘
          stopListening / release
```

| 状态 | 说明 |
|------|------|
| IDLE | 空闲 |
| LISTENING | 监听中（等待连接）|
| ACCEPTED | 已接受连接 |
| STREAMING | 正在接收流 |

---

## 三、数据流设计

### 3.1 视频数据流

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│   屏幕      │     │  Virtual    │     │    Media    │
│   Display   │────▶│   Display   │────▶│  Projection │
└─────────────┘     └─────────────┘     └──────┬──────┘
                                               │
                                               ▼
                                      ┌────────────────┐
                                      │  ImageReader   │
                                      │   (Surface)    │
                                      └────────┬───────┘
                                               │
                                               ▼
                                      ┌────────────────┐
                                      │   VideoEncoder │
                                      │  (MediaCodec)  │
                                      └────────┬───────┘
                                               │ NAL Units
                                               ▼
                                      ┌────────────────┐
                                      │   RtpSession   │
                                      │   (FU-A 分片)  │
                                      └────────┬───────┘
                                               │ RTP Packets
                                               ▼
                                      ┌────────────────┐
                                      │   UdpChannel   │
                                      │   (port 8888)  │
                                      └────────┬───────┘
                                               │ UDP
                                               ▼
                                      ┌────────────────┐
                                      │   UdpChannel   │
                                      │   (receiver)   │
                                      └────────┬───────┘
                                               │
                                               ▼
                                      ┌────────────────┐
                                      │   RtpSession   │
                                      │   (重组)       │
                                      └────────┬───────┘
                                               │ NAL Units
                                               ▼
                                      ┌────────────────┐
                                      │   VideoDecoder │
                                      │  (MediaCodec)  │
                                      └────────┬───────┘
                                               │ Decoded Frame
                                               ▼
                                      ┌────────────────┐
                                      │   TextureView  │
                                      │   (Render)     │
                                      └────────────────┘
```

### 3.2 触摸数据流

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│   触摸事件   │     │  Touch      │     │   UDP       │
│   (Receiver)│────▶│  Encoder    │────▶│  Channel    │
└─────────────┘     └─────────────┘     └──────┬──────┘
                                               │ port 8889
                                               ▼
                                      ┌────────────────┐
                                      │   UDP          │
                                      │   (Sender)     │
                                      └────────┬───────┘
                                               │
                                               ▼
                                      ┌────────────────┐
                                      │   Touch        │
                                      │   Decoder      │
                                      └────────┬───────┘
                                               │
                                               ▼
                                      ┌────────────────┐
                                      │  TouchInjector │
                                      │  Service       │
                                      └────────┬───────┘
                                               │
                                               ▼
                                      ┌────────────────┐
                                      │   系统         │
                                      │   InputManager │
                                      └────────────────┘
```

### 3.3 控制数据流

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│  WiFi P2P   │     │   P2p       │     │   Service   │
│  Discover   │────▶│   Connection│────▶│   Discovery │
│  Request    │     │   Manager   │     │             │
└─────────────┘     └─────────────┘     └──────────────┘

┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│  Heartbeat  │     │   UDP       │     │   Heartbeat │
│  Monitor    │────▶│   Channel   │────▶│   Monitor   │
│  (Sender)   │     │  (port 8889)│     │  (Receiver) │
└─────────────┘     └─────────────┘     └─────────────┘
```

---

## 四、接口设计

### 4.1 SDK 对外 API

**入口类**: `ScreenshareSDK`

```java
public class ScreenshareSDK {
    // 创建发送端
    public static Sender createSender(Context context, SenderConfig config);
    
    // 创建接收端
    public static Receiver createReceiver(Context context, ReceiverConfig config);
    
    // 版本
    public static String getVersion();      // "1.0.0"
    public static int getVersionCode();       // 100
}
```

**发送端**: `Sender`

```java
public class Sender {
    // 生命周期
    public void startCapture();                              // 开始采集
    public void stopCapture();                               // 停止采集
    public void release();                                   // 释放资源
    
    // 连接
    public void connect(String peerAddress);                 // 连接对等设备
    public void disconnect();                                // 断开连接
    
    // 触摸
    public void sendTouchEvent(long timestamp, int action, float x, float y);
    
    // 状态
    public SenderState getState();
    public SenderConfig getConfig();
    public void setEventListener(EventListener listener);
}

public interface EventListener {
    void onStateChanged(SenderState state);
    void onError(int errorCode, String message);
    void onConnected(String peerAddress);
    void onDisconnected();
    void onFrameSent(long timestamp, int size);
}
```

**接收端**: `Receiver`

```java
public class Receiver {
    public void startListening();                           // 开始监听
    public void stopListening();                            // 停止监听
    public void release();                                  // 释放资源
    
    public ReceiverState getState();
    public ReceiverConfig getConfig();
    public void setEventListener(EventListener listener);
}

public interface EventListener {
    void onStateChanged(ReceiverState state);
    void onError(int errorCode, String message);
    void onFrameReceived(long timestamp, int size);
    void onTouchEventReceived(long timestamp, int action, float x, float y);
    void onConnected(String senderAddress);
    void onDisconnected();
}
```

### 4.2 配置类

**SenderConfig**:
```java
public class SenderConfig {
    public int width = 1920;
    public int height = 1080;
    public int fps = 60;
    public int videoBitrate = 0;          // 0=auto
    public int videoPort = 8888;
    public int touchPort = 8889;
    public VideoCodecType videoCodec = VideoCodecType.H264_HARDWARE;
    public PerformancePreset performancePreset = PerformancePreset.BALANCED;
    public boolean withAudio = false;
    public int maxReconnect = 3;
}
```

**ReceiverConfig**:
```java
public class ReceiverConfig {
    public int width = 1920;
    public int height = 1080;
    public int listenVideoPort = 8888;
    public int listenTouchPort = 8889;
    public boolean lowLatencyMode = true;
    public VideoCodecType videoCodec = VideoCodecType.H264_HARDWARE;
    public boolean withAudio = false;
}
```

---

## 五、性能优化设计

### 5.1 GC 优化

**AtomicBuffer**: 预分配 DirectByteBuffer，消除 GC 压力

```java
public class AtomicBuffer {
    private final ByteBuffer buffer;
    
    public AtomicBuffer(int capacity) {
        // DirectByteBuffer 分配在堆外，不受 GC 管理
        this.buffer = ByteBuffer.allocateDirect(capacity);
    }
    
    public void write(byte[] data, int offset, int length) {
        buffer.put(data, offset, length);
    }
    
    public int read(byte[] dest, int offset, int length) {
        buffer.get(dest, offset, length);
        return length;
    }
    
    public void compact() {
        // 移动未读数据到 buffer 开头
        buffer.compact();
        buffer.clear();
    }
}
```

### 5.2 延迟优化

**KEY_LATENCY = 0**: Android Q+ 低延迟编码

```java
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
    format.setInteger(MediaFormat.KEY_LATENCY, 0);
    format.setInteger(MediaFormat.KEY_PRIORITY, 0);  // 实时优先
}
```

**触摸独立通道**: 触摸事件不走视频帧队列

```java
// 触摸走独立 UDP 通道（port 8889），绕过视频编码队列
udpChannel.sendTouch(touchData);  // 独立发送，低优先级
```

### 5.3 带宽自适应

**BandwidthAdapter**: 根据丢包率动态调整码率

```java
public class BandwidthAdapter {
    private int currentBitrate;
    
    public void reportLossRate(double lossRate) {
        if (lossRate > 10.0) {
            currentBitrate = Math.max(currentBitrate / 2, MIN_BITRATE);
            notifyBitrateChanged(currentBitrate);
        } else if (lossRate < 2.0) {
            currentBitrate = Math.min((int)(currentBitrate * 1.2), MAX_BITRATE);
            notifyBitrateChanged(currentBitrate);
        }
    }
}
```

---

## 六、安全性设计

### 6.1 权限管理

| 权限 | 用途 | 危险级别 |
|------|------|----------|
| ACCESS_FINE_LOCATION | WiFi P2P 发现 | 中 |
| ACCESS_WIFI_STATE | WiFi 状态 | 低 |
| CHANGE_WIFI_STATE | P2P 操作 | 高 |
| INTERNET | 网络通信 | 低 |
| FOREGROUND_SERVICE | 前台服务 | 中 |
| FOREGROUND_SERVICE_MEDIA_PROJECTION | 屏幕采集服务 | 高 |
| SYSTEM_ALERT_WINDOW | 悬浮窗 | 高 |
| BIND_ACCESSIBILITY_SERVICE | 触摸注入 | 高 |

### 6.2 隐私保护

- 屏幕内容端到端传输，不经过服务器
- WiFi P2P 直连，无中间人
- 不上传用户数据到云端
- 心跳仅传输连接状态，不含用户内容

---

## 七、兼容性设计

### 7.1 Android 版本兼容性

| 特性 | minSdk | 说明 |
|------|--------|------|
| WiFi P2P | 26 | Android 8.0+ |
| MediaProjection | 21 | Android 5.0+ |
| MediaCodec | 21 | Android 5.0+ |
| KEY_LATENCY | 29 | Android 10+ |
| AccessibilityService | 4 | Android 4.0+ |

### 7.2 设备兼容性

- **编码器**: 尝试 H.264 → H.265 → VP8 (软解) fallback
- **分辨率**: 根据设备性能动态调整（1920x1080 / 1280x720 / 854x480）
- **GPU**: 使用 `Surface` 而非 `SurfaceTexture` 确保硬件加速

---

## 八、部署架构

### 8.1 SDK 发布（AAR）

```
screenshare-sdk/build/outputs/aar/
└── screenshare-sdk-debug.aar

内容：
├── classes.jar           # 编译后的类
├── R.txt                  # 资源 ID
├── res/                   # 资源（通常为空，SDK 不含 UI）
└── AndroidManifest.xml    # SDK 声明的权限
```

**发布配置** (build.gradle):
```groovy
uploadArchives {
    repositories {
        maven {
            url "https://jitpack.io"
        }
    }
}
```

### 8.2 App 发布（APK）

```
screenshare-app/build/outputs/apk/debug/
└── screenshare-app-debug.apk

screenshare-app/build/outputs/apk/release/
└── screenshare-app-release-unsigned.apk
```

**签名配置**:
```groovy
android {
    signingConfigs {
        release {
            keyAlias 'android'
            keyPassword 'xxx'
            storeFile file('keystore.jks')
            storePassword 'xxx'
        }
    }
    buildTypes {
        release {
            signingConfig signingConfigs.release
        }
    }
}
```

---

## 九、错误处理

### 9.1 错误码体系

| 范围 | 类别 |
|------|------|
| 1xxx | 权限/配置错误 |
| 2xxx | 屏幕采集错误 |
| 3xxx | 编码/解码错误 |
| 4xxx | 网络传输错误 |
| 5xxx | WiFi P2P 错误 |

### 9.2 恢复策略

| 错误 | 恢复策略 |
|------|----------|
| 编码器失败 | 重启编码器，降低码率 |
| 连接断开 | 自动重连（3 次，指数退避）|
| 心跳超时 | 触发重连 |
| UDP 绑定失败 | 尝试其他端口 |

---

*技术设计文档版本: 1.0.0*
*最后更新: 2026-05-01*