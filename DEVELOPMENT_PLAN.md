# Android 超低延时投屏 SDK - 开发计划

## 1. 项目概述

### 目标
实现手机到 Pad 的 WiFi P2P 近场投屏，**视频延迟 < 50ms**

### 核心指标
| 指标 | 目标值 | 说明 |
|------|--------|------|
| 视频延迟 | < 50ms | 端到端延迟 |
| 触摸延迟 | < 10ms | 手指到屏幕响应 |
| 帧率 | 60fps | 流畅度保障 |
| 分辨率 | 1080P | 高清显示 |
| WiFi P2P 连接 | < 3s | 搜索到建立连接 |

### 模块划分
```
project/
├── screenshare-sdk/          # SDK 模块（核心功能）
│   └── src/main/java/
│       └── com/ screenshare/sdk/
│           ├── capture/       # 屏幕采集
│           ├── codec/        # 编解码
│           ├── network/      # 网络传输
│           └── touch/        # 触摸反控
│
└── screenshare-app/         # App 模块（界面业务）
    └── src/main/java/
        └── com/ screenshare/app/
            ├── ui/           # 界面
            ├── service/      # 业务服务
            └── wifi/         # WiFi P2P 管理
```

---

## 2. 技术架构

### 2.1 延迟链路分析

```
发送端（手机）：
[屏幕] → [MediaProjection] → [VirtualDisplay] → [Surface]
                                              ↓
                                    [MediaCodec 编码] (10-15ms)
                                              ↓
                                    [RTP 打包] (1-2ms)
                                              ↓
                                    [UDP 发送] (1ms)

接收端（Pad）：
[UDP 接收] → [RTP 解包] (1ms)
                    ↓
          [MediaCodec 解码] (5-10ms) → [Surface]
                                          ↓
                                  [SurfaceView 渲染] (5ms)

触摸链路：
[触摸事件] → [序列化] → [UDP 发送] → [反序列化] → [注入事件]
           (1ms)     (1ms)    (1ms)     (1ms)       (5ms)
```

**总计：约 30-40ms（留有 10-20ms 余量）**

### 2.2 核心组件

| 组件 | 技术选型 | 关键配置 |
|------|----------|----------|
| 屏幕采集 | MediaProjection + VirtualDisplay | 1080P@60fps |
| 视频编码 | MediaCodec (H.264) | Low-Latency 模式 |
| 视频解码 | MediaCodec (H.264) | Surface 输出 |
| 网络传输 | UDP + RTP | 小包优先策略 |
| WiFi P2P | WifiP2pManager | 直连接收端 |
| 触摸反控 | Instrumentation / 注入服务 | 最高优先级 |

---

## 3. SDK 模块设计

### 3.1 包结构

```
com.screenshare.sdk
├── ScreenShare SDK 核心
├── capture/
│   ├── ScreenCapturer          # 屏幕采集器
│   ├── CaptureConfig           # 采集配置
│   └── CaptureCallback         # 采集回调
├── codec/
│   ├── VideoEncoder            # 视频编码器（发送端用）
│   ├── VideoDecoder            # 视频解码器（接收端用）
│   ├── EncoderConfig           # 编码配置
│   └── DecoderConfig          # 解码配置
├── network/
│   ├── RtpSession              # RTP 会话管理
│   ├── RtpPacketizer           # RTP 打包器
│   ├── UdpTransport            # UDP 传输
│   └── TransportCallback       # 传输回调
├── touch/
│   ├── TouchEncoder            # 触摸编码
│   ├── TouchDecoder            # 触摸解码（接收端）
│   └── TouchCallback           # 触摸回调
└── common/
    ├── MediaFrame              # 媒体帧
    ├── TouchEvent              # 触摸事件
    └── LogUtil                 # 日志工具
```

### 3.2 核心类设计

#### ScreenCapturer（屏幕采集）

```java
public class ScreenCapturer {
    // 配置
    public static class Config {
        public int width = 1920;
        public int height = 1080;
        public int fps = 60;
        public int bitrate = 8_000_000; // 8Mbps
        public int dpi = 1;
    }
    
    // 回调：每一帧采集完成
    public interface Callback {
        void onFrameAvailable(Surface inputSurface, long timestamp);
    }
    
    // 方法
    public void start(Config config, Callback callback);
    public void stop();
    public boolean isCapturing();
}
```

#### VideoEncoder（视频编码）

```java
public class VideoEncoder {
    // 低延迟配置关键参数
    // - KEY_LATENCY: 1 (极低延迟模式，Android 10+)
    // - KEY_PRIORITY: 0 (最高优先级)
    // - KEY_OPERATING_RATE: 60 (高帧率)
    // - I_FRAME_INTERVAL: 1 (关键帧间隔，确保低延迟)
    // - BIT_RATE: 8Mbps (高质量)
    
    public interface Callback {
        void onEncodedFrame(ByteBuffer buffer, int size, long timestamp, boolean isKeyFrame);
    }
    
    public void encode(Surface inputSurface, long presentationTimeUs);
    public void start(EncoderConfig config, Callback callback);
    public void stop();
}
```

#### RtpSession（RTP 会话）

```java
public class RtpSession {
    // 包大小限制：1400 bytes (避免分片)
    // 时间戳单位：90kHz (RTP 标准)
    // 序列号：递增
    
    public void sendVideoPacket(byte[] nalUnit, long timestamp, boolean isKeyFrame);
    public void sendTouchPacket(byte[] touchData, long timestamp);
}
```

### 3.3 WiFi P2P 连接流程

```
发送端（手机）：
1. 创建 WifiP2pManager.Channel
2. 注册广播接收器
3. 发现附近设备
4. 连接接收端（Pad）
5. 获取连接信息（IP、端口）

接收端（Pad）：
1. 创建 WifiP2pManager.Channel
2. 注册广播接收器
3. 等待连接
4. 作为 UDP 服务器等待
```

---

## 4. App 模块设计

### 4.1 包结构

```
com.screenshare.app
├── ui/
│   ├── MainActivity            # 主界面（选择角色）
│   ├── SenderActivity          # 发送端界面
│   ├── ReceiverActivity        # 接收端界面
│   └── SettingsActivity         # 设置界面
├── service/
│   ├── SenderService           # 发送端服务
│   ├── ReceiverService         # 接收端服务
│   └── WifiP2pManager          # WiFi P2P 管理
└── wifi/
    ├── WifiDirectCallback      # WiFi P2P 回调
    └── DeviceInfo              # 设备信息
```

### 4.2 界面流程

```
MainActivity
├── 选择"作为发送端" → SenderActivity
│   ├── 申请权限
│   ├── 初始化 SDK
│   ├── 搜索设备列表
│   ├── 连接 Pad
│   └── 开始投屏
│
└── 选择"作为接收端" → ReceiverActivity
    ├── 申请权限
    ├── 初始化 SDK
    ├── 等待连接
    └── 显示视频流
```

---

## 5. 开发阶段规划

### Phase 1: SDK 核心实现（Week 1-2）

**目标**：完成 SDK 所有模块的接口定义和基础实现

| 任务 | 时间 | 产出 |
|------|------|------|
| 项目结构搭建 | 2天 | 两个模块的 Gradle 项目 |
| ScreenCapturer 实现 | 3天 | 屏幕采集功能 |
| VideoEncoder 实现 | 3天 | H.264 低延迟编码 |
| VideoDecoder 实现 | 3天 | H.264 解码渲染 |
| RtpSession 实现 | 2天 | RTP 打包发送 |
| UdpTransport 实现 | 2天 | UDP 传输 |

### Phase 2: WiFi P2P 集成（Week 3）

**目标**：实现设备发现和连接

| 任务 | 时间 | 产出 |
|------|------|------|
| WiFi P2P 管理器 | 2天 | 设备发现/连接 |
| 连接状态管理 | 2天 | 连接状态回调 |
| 服务发现协议 | 3天 | 端发现端 |

### Phase 3: 触摸反控（Week 4）

**目标**：实现<10ms 的触摸响应

| 任务 | 时间 | 产出 |
|------|------|------|
| TouchEncoder | 1天 | 触摸事件序列化 |
| TouchDecoder | 1天 | 触摸事件反序列化 |
| 触摸注入 | 2天 | 事件注入到系统 |
| 优先级队列 | 2天 | 触摸优先传输 |

### Phase 4: 优化与测试（Week 5-6）

**目标**：达到<50ms 延迟目标

| 任务 | 时间 | 产出 |
|------|------|------|
| 延迟测量工具 | 2天 | 延迟监控 |
| Buffer 调优 | 3天 | 减少 Buffer 积压 |
| 编码参数优化 | 3天 | 找到最佳编码参数 |
| 压力测试 | 3天 | 稳定性测试 |
| 性能调优 | 3天 | CPU/内存优化 |

### Phase 5: App 完善（Week 7）

**目标**：完成业务界面

| 任务 | 时间 | 产出 |
|------|------|------|
| Sender UI | 2天 | 发送端界面 |
| Receiver UI | 2天 | 接收端界面 |
| 设置页面 | 1天 | 参数配置 |
| 错误处理 | 1天 | 异常情况处理 |
| 打包测试 | 1天 | APK 打包 |

---

## 6. 关键技术点

### 6.1 低延迟编码配置

```java
MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height);
format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
format.setInteger(MediaFormat.KEY_BIT_RATE, 8_000_000);  // 8Mbps
format.setInteger(MediaFormat.KEY_FRAME_RATE, 60);         // 60fps
format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);    // 1秒间隔

// Android 10+ 低延迟关键
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
    format.setInteger(MediaFormat.KEY_LATENCY, 1);          // 低延迟模式
    format.setInteger(MediaFormat.KEY_PRIORITY, 0);         // 最高优先级
}
```

### 6.2 RTP 包设计

- **MTU**: 1400 bytes（避免 IP 分片）
- **时间戳**: 90kHz 时钟
- **序列号**: 递增
- **NAL 类型处理**: 
  - 单包：直接打包
  - 分片：FU-A 模式

### 6.3 触摸优先级

```
优先级队列处理顺序：
1. 触摸事件 (最高) - 立即发送
2. 关键帧 (高) - 优先发送
3. 普通帧 (中) - 普通队列
4. 非关键数据 (低) - 可丢弃
```

---

## 7. 测试验证

### 延迟测试方法

```java
// 发送端：记录编码完成时间
long encodeTime = System.nanoTime();

// 接收端：记录渲染时间
long renderTime = System.nanoTime();

// 延迟 = renderTime - encodeTime
long latencyMs = (renderTime - encodeTime) / 1_000_000;
```

### 测试场景

| 场景 | 预期延迟 |
|------|----------|
| 静态画面 | < 30ms |
| 滑动界面 | < 40ms |
| 视频播放 | < 45ms |
| 游戏画面 | < 50ms |

---

## 8. 风险评估

| 风险 | 缓解措施 |
|------|----------|
| WiFi P2P 不稳定 | 添加重连机制 |
| 编码延迟过高 | 调整编码参数，降低质量 |
| 高帧率 CPU 占用 | 优化代码，预分配 Buffer |
| 不同设备兼容性 | 多设备测试，适配 |

---

## 9. 下一步行动

1. **确认方案**：你是否认可这个架构设计？
2. **SDK 目录结构**：是否按 `screenshare-sdk` 和 `screenshare-app` 两个模块划分？
3. **优先级**：是否先从 SDK 核心（capture + codec）开始实现？

请告诉我你的想法，我可以先开始实现 SDK 的核心模块。