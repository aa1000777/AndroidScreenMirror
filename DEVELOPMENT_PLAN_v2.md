# Android 超低延时投屏 SDK - 详细开发计划（v2）

> 基于代码审查标准 + MediaCodec 低延迟技术研究，深度优化版本

---

## 1. 项目愿景与核心目标

**一句话目标**：在 WiFi P2P 直连下，实现手机到 Pad 的 **< 50ms 端到端延迟**实时投屏。

### 硬性指标

| 指标 | 目标值 | 验收标准 |
|------|--------|----------|
| 视频延迟 | < 50ms | 高速相机拍摄，手指触控到画面响应 |
| 触摸延迟 | < 10ms | 手指按下到事件发送到编码器 |
| 连接建立 | < 3s | 从搜索到 P2P 连接完成 |
| 帧率 | 60fps | 连续 30 秒无丢帧 |
| 功耗 | < 15% CPU | 持续投屏 5 分钟平均 |
| 兼容性 | Android 8+ | 覆盖 95%+ 设备 |

### 软性目标
- SDK 体积 < 3MB（AAR）
- 支持横竖屏自适应
- 支持 720P / 1080P / 2K 分辨率切换
- 弱网情况下不崩溃，支持自动重连

---

## 2. 延迟链路深度分析

### 2.1 端到端延迟分解

```
发送端（手机）                        接收端（Pad）
───────────────────────────────────   ─────────────────────────────────
[屏幕帧产生]
    ↓ Display HAL (16.67ms @60fps)
[VirtualDisplay 采集] (1-2ms)
    ↓
[Surface 输入] → [MediaCodec 编码] (8-12ms, KEY_LATENCY=1)
    ↓
[RTP 打包] (0.5ms)
    ↓
[UDP Socket 发送] (0.5ms)
    ↓ ──────────────────── 网络传输 ──────────────────── ↓
[UDP Socket 接收] (0.5ms)
    ↓
[RTP 解包] (0.5ms)
    ↓
[Surface 输入] → [MediaCodec 解码] (5-8ms)
    ↓
[SurfaceView 渲染] (3-5ms)
    ↓
[屏幕显示]
    ↓
[触摸事件产生]
    ↓
[TouchEvent 序列化] (0.2ms)
    ↓
[UDP 发送] (0.5ms)
    ↓ ──────────────────── 网络传输 ──────────────────── ↓
[UDP 接收] (0.5ms)
    ↓
[TouchEvent 反序列化] (0.2ms)
    ↓
[触摸注入] (1-3ms)

───────────────────────────────────   ─────────────────────────────────
总视频延迟：约 25-35ms                 总触摸延迟：约 8-13ms
```

**瓶颈识别：**
- `MediaCodec 编码` 是最大的延迟来源（8-12ms）
- `VirtualDisplay 采集` 在部分设备上有 5-10ms 延迟
- `SurfaceView 渲染` 在部分设备上超过 10ms（可考虑 TextureView）

### 2.2 延迟优化策略

| 阶段 | 优化手段 | 预期降低 |
|------|----------|----------|
| 编码 | KEY_LATENCY=1, BUFFER_SIZE=0 | -5ms |
| 渲染 | TextureView + setPreserveExternalSurface | -3ms |
| 传输 | 启用 Socket Zero-Copy | -1ms |
| 触摸 | 触摸队列优先级化 | -2ms |
| Buffer | 全部预分配，无 GC 接触 | -3ms |

---

## 3. 模块架构（SDK + App 双模块）

### 3.1 目录结构

```
screenshare/
├── screenshare-sdk/                    # SDK 核心（纯 Java，无 Android UI 依赖）
│   ├── build.gradle
│   └── src/main/java/com/screenshare/sdk/
│       ├── Capture/                    # 屏幕采集（MediaProjection 封装）
│       │   ├── ScreenCapturer.java
│       │   ├── CaptureConfig.java
│       │   └── CaptureCallback.java
│       ├── Codec/                      # 编解码
│       │   ├── VideoEncoder.java       # 发送端：H.264 编码
│       │   ├── VideoDecoder.java       # 接收端：H.264 解码
│       │   └── CodecConfig.java        # 编码参数常量
│       ├── Network/                    # 网络传输
│       │   ├── RtpSession.java         # RTP 会话管理
│       │   ├── RtpPacketizer.java      # NAL → RTP 打包
│       │   └── UdpChannel.java        # UDP 通道（发送/接收）
│       ├── Touch/                      # 触摸反控
│       │   ├── TouchEncoder.java       # 触摸序列化
│       │   ├── TouchDecoder.java        # 触摸反序列化 + 注入
│       │   └── TouchConfig.java
│       ├── Common/                     # 公共组件
│       │   ├── MediaFrame.java         # 媒体帧封装
│       │   ├── PriorityQueue.java      # 优先级队列
│       │   ├── AtomicBuffer.java       # 无 GC Buffer
│       │   └── LogUtil.java
│       └── ScreenshareSDK.java         # SDK 入口
│
└── screenshare-app/                     # App 壳（依赖 SDK）
    ├── build.gradle
    └── src/main/java/com/screenshare/app/
        ├── ui/
        │   ├── MainActivity.java
        │   ├── SenderActivity.java
        │   └── ReceiverActivity.java
        ├── wifi/                        # WiFi P2P 管理
        │   ├── WifiP2pManager.java
        │   ├── P2pDevice.java
        │   └── P2pCallbacks.java
        └── service/
            ├── SenderService.java
            └── ReceiverService.java
```

---

## 4. 核心技术点详解

### 4.1 MediaCodec 低延迟编码配置

```java
// VideoEncoder.java 核心配置
public static MediaFormat createLowLatencyFormat(int width, int height) {
    MediaFormat format = MediaFormat.createVideoFormat(
        MediaFormat.MIMETYPE_VIDEO_AVC, width, height);
    
    // 颜色格式：Surface 输入
    format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
        MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
    
    // 编码质量与速度平衡
    format.setInteger(MediaFormat.KEY_BIT_RATE, 8_000_000);  // 8Mbps
    format.setInteger(MediaFormat.KEY_FRAME_RATE, 60);
    
    // 关键帧间隔：1秒（保证低延迟切换）
    format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
    
    // ★ 低延迟核心配置（Android 10+）
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        format.setInteger(MediaFormat.KEY_LATENCY, 1);        // 零延迟模式
        format.setInteger(MediaFormat.KEY_PRIORITY, 0);      // 最高优先级
    }
    
    // ★ Android 11+ 新增优化
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        format.setInteger(MediaFormat.KEY_VIDEO_ENCODER_COMPLEXITY, 1); // 速度优先
    }
    
    return format;
}
```

### 4.2 无 GC Buffer 设计

```java
// AtomicBuffer.java - 使用预分配 ByteBuffer，避免 GC
public class AtomicBuffer {
    private final ByteBuffer buffer;
    private final byte[] array;  // 直接操作数组，无 GC
    
    public AtomicBuffer(int capacity) {
        this.array = new byte[capacity];
        this.buffer = ByteBuffer.wrap(array).order(ByteOrder.nativeOrder());
    }
    
    // 写入数据后，通过 Handler 发送到编码线程
    public void publish(Handler handler, Message msg) {
        msg.obj = array;  // 复用数组，不分配新对象
        handler.sendMessage(msg);
    }
}
```

### 4.3 优先级传输队列

```java
// PriorityPacketQueue.java
public class PriorityPacketQueue {
    // 优先级：触摸 > 关键帧 > 普通帧 > 控制消息
    private final PriorityBlockingQueue<Packet> queue = 
        new PriorityBlockingQueue<>(100, (a, b) -> {
            return Integer.compare(a.priority, b.priority);
        });
    
    // 触摸事件走独立通道，绕过队列
    public void sendTouchUrgent(byte[] data) {
        // 直接发送，不排队
        udpChannel.sendUrgent(data);
    }
}
```

### 4.4 WiFi P2P 服务发现协议

```
发送端                            接收端
  │                                │
  │ ──── discoverPeers() ────────→ │
  │ ←─── onPeersAvailable() ────── │
  │                                │
  │ ──── connect() ──────────────→ │
  │                                │
  │ ←─── onConnectionSuccess() ────│
  │                                │
  │ ──── 请求设备信息 ───────────→ │
  │ ←─── 返回 IP:Port ──────────── │
  │                                │
  │        建立 UDP 视频通道       │
  │        建立 UDP 触摸通道       │
```

---

## 5. 现有代码审查问题（基于 code-review 技能）

对现有 `AndroidScreenMirror` 代码的审查发现以下问题：

### 5.1 安全性
- **[MINOR]** `client.conf` 中 PrivateKey 明文存储在配置文件中 → 需要运行时从 KeyChain 获取
- **[MINOR]** WiFi P2P 连接未验证设备证书 → 中间人攻击风险

### 5.2 性能
- **[MAJOR]** `LowLatencyNetwork.java` 中 `videoQueue.poll(10, TimeUnit.MILLISECONDS)` 轮询浪费 CPU
- **[MAJOR]** `TouchEventSerializer` 每次 `serialize()` 分配新 ByteBuffer → GC 压力
- **[MINOR]** `RtpPacketizer` 中字符串拼接用于日志 → 应使用 Log.d + isLoggable 检查

### 5.3 正确性
- **[MAJOR]** 无重连机制：WiFi P2P 断开后不自动重连
- **[MAJOR]** 无心跳检测：无法发现对端崩溃
- **[MINOR]** `SurfaceHolder.Callback` 未处理配置变更（屏幕旋转）

### 5.4 可维护性
- **[NIT]** `Constants.java` 使用中文注释但部分常量命名不一致
- **[MINOR]** 所有核心逻辑在 Activity 中 → 应抽取到 SDK 层

---

## 6. 6 周详细开发计划

### Week 1：项目骨架 + SDK 核心架构

**目标**：搭建两个 Gradle 模块，完成 SDK 接口定义

| Day | 任务 | 产出 |
|-----|------|------|
| 1-2 | 创建 `screenshare-sdk` 和 `screenshare-app` 模块结构 | Gradle 项目 |
| 3 | 实现 `CodecConfig`：定义所有编码参数常量 | `CodecConfig.java` |
| 4 | 实现 `AtomicBuffer`：无 GC Buffer 封装 | `AtomicBuffer.java` |
| 5 | 实现 `LogUtil`：区分 Debug/Release 日志 | `LogUtil.java` |

**验收标准**：
- `./gradlew assembleDebug` 成功
- SDK 可被 App 模块依赖
- 无编译警告

### Week 2：ScreenCapture + VideoEncoder

**目标**：完成屏幕采集和 H.264 低延迟编码

| Day | 任务 | 产出 |
|-----|------|------|
| 6-7 | `ScreenCapturer`：MediaProjection + VirtualDisplay | `ScreenCapturer.java` |
| 8-9 | `VideoEncoder`：MediaCodec 编码器 + 低延迟配置 | `VideoEncoder.java` |
| 10 | `VideoDecoder`：MediaCodec 解码器 | `VideoDecoder.java` |
| 11 | 编码/解码联调测试 | 基准延迟测试 |

**验收标准**：
- 本地录制测试：编码到解码端到端 < 30ms（单设备 loopback）
- 支持 `KEY_LATENCY=1` 模式（Android 10+）
- 兼容 Android 8.0+（无 KEY_LATENCY 时降级）

### Week 3：网络传输

**目标**：RTP 打包 + UDP 传输完成

| Day | 任务 | 产出 |
|-----|------|------|
| 12 | `RtpPacketizer`：NAL → RTP 打包（单包 + FU-A） | `RtpPacketizer.java` |
| 13 | `RtpSession`：会话管理 + 时间戳同步 | `RtpSession.java` |
| 14 | `UdpChannel`：发送/接收通道 + Zero-Copy | `UdpChannel.java` |
| 15 | `PriorityPacketQueue`：触摸优先队列 | `PriorityPacketQueue.java` |
| 16 | 延迟测试：测量各环节延迟 | 延迟分析报告 |

**验收标准**：
- 1400 bytes MTU 限制正常工作
- 触摸数据在 1ms 内发出
- 关键帧 / 普通帧优先级正确

### Week 4：WiFi P2P 集成

**目标**：设备发现、连接、服务发现

| Day | 任务 | 产出 |
|-----|------|------|
| 17-18 | `WifiP2pManager`：设备发现和连接 | `WifiP2pManager.java` |
| 19 | 服务发现协议：公告和查询 IP:Port | `ServiceDiscovery.java` |
| 20 | 连接状态机：IDLE → DISCOVERING → CONNECTING → CONNECTED | 状态机实现 |
| 21 | 重连机制：断开自动重连 3 次 | `ReconnectionManager.java` |

**验收标准**：
- 端到端连接 < 3s
- 断开后自动重连成功
- 支持一台发送端对一台接收端

### Week 5：触摸反控

**目标**：触摸事件 < 10ms 延迟

| Day | 任务 | 产出 |
|-----|------|------|
| 22 | `TouchEncoder`：触摸事件序列化 | `TouchEncoder.java` |
| 23 | `TouchDecoder`：触摸事件反序列化 | `TouchDecoder.java` |
| 24 | 触摸注入：`Instrumentation` 或 Root 注入 | 触摸注入模块 |
| 25 | 触摸延迟测试 | 延迟数据 |

**验收标准**：
- 触摸事件序列化 < 0.5ms
- 端到端触摸延迟 < 10ms
- 多指触控支持（最多 5 指）

### Week 6-7：优化 + 集成测试

**目标**：达到 < 50ms 延迟目标，App 界面完成

| Day | 任务 | 产出 |
|-----|------|------|
| 26-27 | Buffer 调优：全部预分配，移除所有临时对象 | 内存优化 |
| 28 | 渲染优化：TextureView vs SurfaceView 对比 | 渲染选型 |
| 29 | App 界面：SenderActivity + ReceiverActivity | 完整 UI |
| 30 | 集成测试 + 延迟测量 | 实测报告 |
| 31 | WiFi P2P + 编码参数调优 | 最终参数 |
| 32-35 | 压力测试 + 多设备兼容性测试 | 兼容性报告 |
| 36 | 打包 AAR + 文档 | SDK 发布包 |

---

## 7. 测试计划

### 7.1 延迟测量方法

```bash
# 工具：screen-record-frames 技能 + 高速相机
# 1. 发送端屏幕录制（scrcpy）
scrcpy -t -r input.mp4

# 2. 提取关键帧
ffmpeg -i input.mp4 -vf "select=eq(pict_type\,I)" -vsync vfr keyframes_%03d.png

# 3. 分析手指按下 → 画面响应 时间差
```

### 7.2 测试矩阵

| 设备组合 | 分辨率 | 预期延迟 | 实际测试 |
|----------|--------|----------|----------|
| Pixel → Pixel | 1080P | < 40ms |  |
| Pixel → Tab S7 | 1080P | < 45ms |  |
| Xiaomi → Huawei | 720P | < 50ms |  |
| Samsung → Samsung | 1080P | < 45ms |  |

### 7.3 自动化测试
- 单元测试：编码器/解码器（JUnit）
- 集成测试：发送端 ↔ 接收端（Espresso）
- 压力测试：持续投屏 30 分钟，检查内存泄漏

---

## 8. 风险评估与缓解

| 风险 | 概率 | 影响 | 缓解方案 |
|------|------|------|----------|
| 部分设备 MediaCodec 不支持 LOW_LATENCY | 中 | 高 | 降级到普通延迟模式 |
| WiFi P2P 在部分设备上不稳定 | 高 | 高 | 添加 TCP fallback |
| 高分辨率编码 CPU 占用过高 | 中 | 中 | 降比特率到 4Mbps |
| 不同芯片解码器兼容性问题 | 中 | 中 | 使用软解码作为 fallback |
| 触摸注入需要 Root 权限 | 高 | 中 | 提供 Root / 无 Root 两种方案 |

---

## 9. 代码审查规范（基于 code-review 技能）

**每个 PR 必须包含：**

```markdown
## PR 检查清单

### 安全性
- [ ] 无硬编码密钥或 credentials
- [ ] 输入数据已验证（null、边界值）
- [ ] WiFi P2P 连接已加密

### 性能
- [ ] 无在主线程的 I/O 操作
- [ ] 无运行时 ByteBuffer 分配（检查 `allocate()`）
- [ ] 已使用 `PriorityBlockingQueue` 处理触摸优先级

### 正确性
- [ ] 编码器/解码器已正确 release()
- [ ] WiFi P2P 断开后有重连逻辑
- [ ] 屏幕旋转时正确处理配置变更

### 可维护性
- [ ] 关键代码段有中文注释
- [ ] 方法不超过 50 行
- [ ] 常量已提取到 CodecConfig
```

---

## 10. 已安装的相关技能

| 技能 | 用途 |
|------|------|
| **code-review** | PR 审查规范执行 |
| **android-studio** | Android Studio 调试和优化 |
| **screen-record-frames** | 录屏提取关键帧测试延迟 |
| **adb-claw** | 设备交互、UI 自动化测试 |
| **github** | 代码版本管理 |

---

## 11. 下一步行动

1. ✅ 开发计划 v2 完成
2. ⬜ 你确认计划后 → 开始 Week 1 项目骨架搭建
3. ⬜ 同时：申请 Tavily API Key（联网搜索功能需要）

---

*文档更新时间：2026-05-01*
*版本：v2.0*