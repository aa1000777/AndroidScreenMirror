# Android 超低延时投屏 SDK

> 📱 < 50ms 端到端视频延迟 | WiFi P2P 直连 | SDK + App 双模块架构

---

## 项目概述

Android 超低延时投屏 SDK，旨在实现手机到 Pad 的实时投屏和触摸反控功能。目标延迟：视频 <50ms，触摸 <10ms。

### 核心特性

| 特性 | 说明 |
|------|------|
| **超低延迟** | 视频 <50ms，触摸 <10ms |
| **WiFi P2P** | 点对点直连，无需路由器 |
| **硬件编码** | MediaCodec H.264/H.265 |
| **触摸反控** | AccessibilityService 注入 |
| **SDK 模块化** | screenshare-sdk（AAR）+ screenshare-app |
| **GC 优化** | 原子化预分配缓冲区 |

---

## 项目结构

```
AndroidScreenMirror/
├── screenshare-sdk/                      # SDK 模块（AAR 发布）
│   ├── build.gradle
│   └── src/main/
│       ├── java/com/screenshare/sdk/
│       │   ├── ScreenshareSDK.java      # SDK 唯一入口
│       │   ├── Sender.java              # 发送端
│       │   ├── Receiver.java            # 接收端
│       │   ├── SenderConfig.java        # 发送端配置
│       │   ├── ReceiverConfig.java      # 接收端配置
│       │   ├── Common/
│       │   │   ├── AtomicBuffer.java    # GC 优化缓冲区
│       │   │   ├── BandwidthAdapter.java # 带宽自适应
│       │   │   ├── ConnectionStateMachine.java
│       │   │   ├── ErrorCode.java       # 错误码
│       │   │   ├── HeartbeatMonitor.java
│       │   │   ├── LatencyTester.java
│       │   │   ├── ReconnectionManager.java
│       │   │   ├── SenderState.java
│       │   │   └── ReceiverState.java
│       │   ├── capture/
│       │   │   └── ScreenCapturer.java
│       │   ├── codec/
│       │   │   ├── VideoEncoder.java
│       │   │   ├── VideoDecoder.java
│       │   │   ├── VideoDecoderIntegrator.java
│       │   │   ├── AudioCapture.java
│       │   │   ├── AudioEncoder.java
│       │   │   ├── AudioDecoder.java
│       │   │   └── AudioPlayer.java
│       │   ├── network/
│       │   │   ├── UdpChannel.java
│       │   │   └── RtpSession.java
│       │   ├── service/
│       │   │   ├── ScreenCaptureService.java
│       │   │   └── ScreenMirroringService.java
│       │   ├── touch/
│       │   │   ├── TouchEncoder.java
│       │   │   ├── TouchDecoder.java
│       │   │   └── TouchInjectorService.java
│       │   └── wifi/
│       │       ├── P2pConnectionManager.java
│       │       ├── P2pBroadcastReceiver.java
│       │       └── ServiceDiscovery.java
│       ├── res/
│       └── AndroidManifest.xml
│
├── screenshare-app/                      # App 模块
│   ├── build.gradle
│   └── src/main/
│       ├── java/com/screenshare/app/
│       │   ├── ui/
│       │   │   ├── MainActivity.java
│       │   │   ├── SenderActivity.java
│       │   │   ├── ReceiverActivity.java
│       │   │   ├── SettingsActivity.java
│       │   │   └── dialog/ErrorRecoveryDialog.java
│       └── res/
│
├── build.gradle                          # 根项目配置（AGP 8.2.0）
├── settings.gradle                      # 模块包含
├── gradle.properties                     # Gradle 配置
├── gradlew / gradlew.bat               # Gradle Wrapper（8.5）
└── README.md
```

---

## SDK API 概览

### 入口类

```java
// ScreenshareSDK.java - SDK 唯一入口
public class ScreenshareSDK {
    // 创建发送端
    public static Sender createSender(Context context, SenderConfig config);
    
    // 创建接收端
    public static Receiver createReceiver(Context context, ReceiverConfig config);
    
    // 版本信息
    public static String getVersion();     // "1.0.0"
    public static int getVersionCode();     // 100
}
```

### 发送端 API

```java
public class Sender {
    // 生命周期
    public void startCapture();          // 开始屏幕采集
    public void stopCapture();           // 停止采集
    public void release();               // 释放资源
    
    // 连接管理
    public void connect(String peerAddress);  // 连接到对等设备
    public void disconnect();            // 断开连接
    
    // 触摸发送
    public void sendTouchEvent(long timestamp, int action, float x, float y);
    
    // 事件监听
    public void setEventListener(EventListener listener);
}

public interface Sender.EventListener {
    void onStateChanged(SenderState state);
    void onError(int errorCode, String message);
    void onConnected(String peerAddress);
    void onDisconnected();
    void onFrameSent(long timestamp, int size);
}
```

### 接收端 API

```java
public class Receiver {
    public void startListening();        // 开始监听
    public void stopListening();         // 停止监听
    public void release();               // 释放资源
    
    public void setEventListener(EventListener listener);
}

public interface Receiver.EventListener {
    void onStateChanged(ReceiverState state);
    void onError(int errorCode, String message);
    void onFrameReceived(long timestamp, int size);
    void onTouchEventReceived(long timestamp, int action, float x, float y);
    void onConnected(String senderAddress);
    void onDisconnected();
}
```

### 配置类

```java
// 发送端配置
public class SenderConfig {
    public int width = 1920;            // 视频宽度
    public int height = 1080;           // 视频高度
    public int fps = 60;                // 帧率
    public int videoBitrate = 0;        // 码率（0=自动）
    public int videoPort = 8888;        // 视频端口（UDP）
    public int touchPort = 8889;        // 触摸端口（UDP）
    public VideoCodecType videoCodec = VideoCodecType.H264_HARDWARE;
    public PerformancePreset performancePreset = PerformancePreset.BALANCED;
}

// 接收端配置
public class ReceiverConfig {
    public int width = 1920;
    public int height = 1080;
    public int listenVideoPort = 8888;
    public int listenTouchPort = 8889;
    public boolean lowLatencyMode = true;
    public VideoCodecType videoCodec = VideoCodecType.H264_HARDWARE;
}
```

---

## 状态机

### 发送端状态机

```
IDLE → CAPTURING → DISCOVERING → CONNECTING ↔ STREAMING
                                    ↓
                            CONNECTION_LOST（可重连）
```

### 接收端状态机

```
LISTENING → ACCEPTED → STREAMING
```

---

## 错误码体系

| 范围 | 类别 |
|------|------|
| 1xxx | 权限/配置错误 |
| 2xxx | 屏幕采集错误 |
| 3xxx | 编码/解码错误 |
| 4xxx | 网络传输错误 |
| 5xxx | WiFi P2P 错误 |

---

## 编译要求

| 配置 | 版本 |
|------|------|
| AGP | 8.2.0 |
| Gradle | 8.5 |
| minSdk | 26（Android 8.0）|
| targetSdk | 34 |
| compileSdk | 34 |

### 编译命令

```bash
# 编译 SDK
./gradlew :screenshare-sdk:assembleDebug

# 编译 App
./gradlew :screenshare-app:assembleDebug

# 同时编译
./gradlew :screenshare-sdk:assembleDebug :screenshare-app:assembleDebug
```

---

## 使用示例

### 发送端

```java
// 1. 创建配置
SenderConfig config = new SenderConfig();
config.width = 1920;
config.height = 1080;
config.fps = 60;
config.videoPort = 8888;
config.touchPort = 8889;

// 2. 创建发送端
Sender sender = ScreenshareSDK.createSender(context, config);
sender.setEventListener(new Sender.EventListener() {
    @Override
    public void onConnected(String peerAddress) {
        Log.d(TAG, "已连接到: " + peerAddress);
    }
    
    @Override
    public void onDisconnected() {
        Log.d(TAG, "连接断开");
    }
});

// 3. 开始采集
sender.startCapture();

// 4. 连接到接收端
sender.connect("192.168.x.x");

// 5. 发送触摸事件
sender.sendTouchEvent(System.currentTimeMillis(), ACTION_DOWN, 100, 200);

// 6. 停止
sender.stopCapture();
sender.release();
```

### 接收端

```java
// 1. 创建配置
ReceiverConfig config = new ReceiverConfig();
config.listenVideoPort = 8888;
config.listenTouchPort = 8889;

// 2. 创建接收端
Receiver receiver = ScreenshareSDK.createReceiver(context, config);
receiver.setEventListener(new Receiver.EventListener() {
    @Override
    public void onTouchEventReceived(long timestamp, int action, float x, float y) {
        // 注入触摸事件
        TouchInjectorService.injectTouch(timestamp, action, x, y, 1f, 1f);
    }
});

// 3. 开始监听
receiver.startListening();

// 4. 停止
receiver.stopListening();
receiver.release();
```

---

## 技术指标

| 指标 | 目标 |
|------|------|
| 视频延迟 | < 50ms |
| 触摸延迟 | < 10ms |
| 帧率 | 60fps |
| 分辨率 | 最高 1920x1080 |
| 编码 | H.264/H.265 硬件 |
| 连接 | WiFi P2P / WiFi 局域网 |

---

## 文件统计

| 模块 | 文件数 | 备注 |
|------|--------|------|
| SDK Java | 32 个 | 完整实现 |
| App Java | 5 个 | UI + Dialog |
| 布局 | 8 个 | Activity + Dialog |
| 资源 | 多个 | strings/colors/arrays |

---

## GitHub

- **仓库**: https://github.com/aa1000777/AndroidScreenMirror
- **提交历史**: 每周迭代开发

---

*最后更新: 2026-05-01*
