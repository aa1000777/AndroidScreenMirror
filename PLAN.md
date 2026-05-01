# Android 超低延时投屏项目策划文档

> 📅 版本: 1.0.0 | 更新: 2026-05-01

---

## 一、项目背景与目标

### 1.1 项目背景

随着移动设备多屏协作需求的增长，手机投屏到平板/电脑的需求日益强烈。现有投屏方案存在以下问题：

| 问题 | 现有方案痛点 |
|------|-------------|
| 延迟高 | 视频延迟 >200ms，触摸延迟 >50ms |
| WiFi P2P 不稳定 | 连接成功率低，切换复杂 |
| 依赖网络 | 需要局域网或互联网连接 |
| 触摸反控缺失 | 仅支持单向显示，无法反向控制 |

### 1.2 项目目标

开发一套完整可商用的 Android 投屏 SDK + App，实现：

- **视频延迟**：< 50ms 端到端
- **触摸延迟**：< 10ms
- **WiFi P2P**：点对点直连，无需路由器
- **触摸反控**：接收端可控制发送端
- **SDK 模块化**：可集成到其他 App

### 1.3 成功标准

| 指标 | 目标 | 验收标准 |
|------|------|----------|
| 视频延迟 | < 50ms | 接收端画面与发送端同步 |
| 触摸延迟 | < 10ms | 触摸到响应 < 10ms |
| WiFi P2P 连接 | 成功率 >95% | 10 次尝试成功 ≥9 次 |
| 功耗 | < 5W | 连续投屏 30 分钟耗电 < 10% |
| SDK 体积 | < 5MB | AAR 文件 < 5MB |

---

## 二、产品功能规划

### 2.1 MVP 功能（必须）

| 功能 | 优先级 | 说明 |
|------|--------|------|
| 屏幕采集 | P0 | MediaProjection API |
| 硬件编码 | P0 | H.264 硬件编码 |
| UDP 传输 | P0 | 视频 + 触摸双通道 |
| WiFi P2P | P0 | 设备发现与连接 |
| 触摸反控 | P0 | AccessibilityService |
| 重连机制 | P0 | 断开自动重连 |
| 心跳保活 | P1 | 3 次超时断开 |

### 2.2 增强功能（规划）

| 功能 | 优先级 | 说明 |
|------|--------|------|
| 音频同步 | P2 | 麦克风 + 扬声器 |
| H.265 编码 | P2 | 更高压缩率 |
| 带宽自适应 | P2 | 根据网络调整码率 |
| 分辨率切换 | P2 | 动态调整 |
| 截图功能 | P3 | 接收端截图 |

### 2.3 功能矩阵

| 功能 | v1.0 MVP | v1.1 | v2.0 |
|------|----------|------|------|
| WiFi P2P | ✅ | - | - |
| 屏幕采集 | ✅ | - | - |
| H.264 编码 | ✅ | - | - |
| 触摸反控 | ✅ | - | - |
| 重连机制 | ✅ | - | - |
| 音频同步 | - | ✅ | - |
| H.265 编码 | - | ✅ | - |
| 带宽自适应 | - | - | ✅ |

---

## 三、技术方案

### 3.1 系统架构

```
┌─────────────────┐                    ┌─────────────────┐
│   发送端 (Sender) │                    │ 接收端 (Receiver) │
├─────────────────┤                    ├─────────────────┤
│ ScreenCapturer │                    │ VideoDecoder    │
│       ↓        │                    │       ↑        │
│ VideoEncoder   │←── UDP ──→         │ VideoDecoder    │
│       ↓        │   RTP 视频          │ Integrator      │
│ TouchEncoder   │←── UDP ──→         │ TouchDecoder    │
│       ↑        │   触摸              │       ↓        │
│ TouchInjector  │                    │ 触摸注入        │
├─────────────────┤                    ├─────────────────┤
│ P2pConnection  │                    │ P2pConnection   │
│ Manager       │←── P2P ──→         │ Manager         │
├─────────────────┤                    ├─────────────────┤
│ Heartbeat      │                    │ Heartbeat       │
│ Monitor        │←── UDP ──→         │ Monitor         │
└─────────────────┘                    └─────────────────┘
```

### 3.2 模块划分

| 模块 | 职责 | 关键技术 |
|------|------|----------|
| screenshare-sdk | 核心 SDK | Java/Kotlin |
| screenshare-app | 主程序 | Android Activity |
| capture | 屏幕采集 | MediaProjection |
| codec | 编解码 | MediaCodec |
| network | 传输 | UDP/RTP |
| touch | 触摸 | AccessibilityService |
| wifi | P2P | WifiP2pManager |

### 3.3 数据流设计

#### 视频数据流

```
发送端:                           接收端:
屏幕 → VirtualDisplay
         ↓
    ImageReader
         ↓
    Surface (VideoEncoder 输入)
         ↓
    MediaCodec (H.264)
         ↓
    NAL Unit
         ↓
    RTP Session (FU-A 分片)
         ↓
    UDP Channel (port 8888)
         ↓
    UDP Channel
         ↓
    RTP Session (重组)
         ↓
    NAL Unit
         ↓
    MediaCodec (H.264 解码)
         ↓
    Surface (TextureView)
         ↓
    渲染显示
```

#### 触摸数据流

```
接收端触摸 → TouchEvent → TouchEncoder → UDP (port 8889)
                                        ↓
                              UDP (port 8889)
                                        ↓
                              TouchDecoder → TouchInjector → 系统注入
```

### 3.4 网络协议

| 通道 | 端口 | 协议 | 说明 |
|------|------|------|------|
| 视频 | 8888 | UDP + RTP | H.264 视频流 |
| 触摸 | 8889 | UDP | 触摸事件（18 bytes/包）|
| 服务发现 | 8765 | TCP | WiFi P2P 服务公告 |

### 3.5 关键算法

#### 延迟优化

1. **KEY_LATENCY = 0**：Android Q+ 低延迟编码
2. **AtomicBuffer**：预分配，减少 GC
3. **触摸优先**：触摸事件独立通道，绕过帧队列

#### 重连机制

```
连接断开 → 重试 1 (1s) → 重试 2 (2s) → 重试 3 (4s) → 放弃
         ↓
    心跳超时 3 次触发重连
```

---

## 四、项目计划

### 4.1 开发阶段

| 阶段 | 周次 | 主要任务 | 交付物 |
|------|------|----------|--------|
| 阶段一 | Week 1 | 项目骨架 | SDK + App 结构，Gradle Wrapper |
| 阶段二 | Week 2 | WiFi P2P | 服务发现，设备连接 |
| 阶段三 | Week 3 | 屏幕采集 | MediaProjection，编码器 |
| 阶段四 | Week 4 | 网络传输 | UDP/RTP，视频流传输 |
| 阶段五 | Week 5 | 触摸反控 | AccessibilityService |
| 阶段六 | Week 6 | 完善功能 | 心跳，重连，音频 |
| 阶段七 | Week 7 | 优化测试 | 延迟测试，性能优化 |

### 4.2 里程碑

| 里程碑 | 目标日期 | 验收标准 |
|--------|----------|----------|
| M1: 骨架完成 | Week 1 末 | SDK 可编译，App 可运行 |
| M2: P2P 连接 | Week 2 末 | WiFi P2P 发现 + 连接成功 |
| M3: 视频传输 | Week 4 末 | 端到端视频延迟 < 100ms |
| M4: 触摸反控 | Week 5 末 | 触摸延迟 < 20ms |
| M5: 完整功能 | Week 7 末 | 所有 MVP 功能完成 |

### 4.3 资源需求

| 资源 | 数量 | 用途 |
|------|------|------|
| Android 开发 | 1 人 | 开发 |
| 测试设备 | 2 台 | 真机测试 |
| 服务器 | 1 台 | 文档网站 |

---

## 五、团队与分工

### 5.1 角色

| 角色 | 职责 | 人员 |
|------|------|------|
| 产品经理 | 需求管理，进度跟踪 | 用户 |
| 开发工程师 | 开发实现 | AI Assistant |
| 测试工程师 | 测试验证 | AI Assistant |

### 5.2 沟通机制

| 场景 | 方式 |
|------|------|
| 日常沟通 | WeChat |
| 文档同步 | tech-blog 网站 |
| 代码管理 | GitHub |

---

## 六、风险与对策

| 风险 | 影响 | 概率 | 对策 |
|------|------|------|------|
| WiFi P2P 不稳定 | 高 | 中 | 降级到 WiFi 局域网 |
| 编码器兼容性 | 中 | 低 | 多设备测试，软解 Fallback |
| 触摸注入权限 | 高 | 中 | 提供 Root 模式可选 |
| 延迟不达标 | 高 | 中 | 降分辨率/帧率保流畅 |

---

## 七、收益分析

### 7.1 商业价值

- **SDK 授权**：可授权给其他 App
- **品牌效应**：展示技术实力
- **生态扩展**：多设备协同能力

### 7.2 成本估算

| 成本项 | 估算 |
|--------|------|
| 开发人力 | 7 周 |
| 测试设备 | ¥2000 |
| 服务器 | ¥500/月 |

---

## 八、附录

### 8.1 参考资料

- Android MediaProjection API
- Android MediaCodec API
- WiFi P2P Developer Guide
- RTP/RTCP 协议规范

### 8.2 术语表

| 术语 | 说明 |
|------|------|
| SDK | Software Development Kit |
| MediaProjection | Android 屏幕采集 API |
| MediaCodec | Android 硬件编解码 API |
| RTP | Real-time Transport Protocol |
| FU-A | H.264 分片单元 |
| AccessibilityService | Android 无障碍服务 |

---

*策划文档版本: 1.0.0*
*最后更新: 2026-05-01*