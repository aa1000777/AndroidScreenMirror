# AndroidScreenMirror 开发维护手册

> 📅 版本: 1.0.0 | 更新: 2026-05-01
> 维护者: AI Assistant (OpenClaw)

---

## 一、项目架构总览

### 1.1 模块结构

```
AndroidScreenMirror/
├── screenshare-sdk/          # SDK 库模块（AAR）
│   └── src/main/java/com/screenshare/sdk/
│       ├── ScreenshareSDK.java    # 唯一入口
│       ├── Sender.java / Receiver.java  # 主类
│       ├── SenderConfig.java / ReceiverConfig.java  # 配置
│       ├── Common/               # 通用组件
│       ├── capture/              # 屏幕采集
│       ├── codec/                # 音视频编解码
│       ├── network/             # 网络传输
│       ├── service/              # 前台服务
│       ├── touch/                # 触摸注入
│       └── wifi/                 # WiFi P2P
│
├── screenshare-app/           # App 主程序
│   └── src/main/java/com/screenshare/app/
│       └── ui/                  # Activity + Dialog
│
├── screenshare-sdk/src/test/   # 单元测试（13 个文件）
│
├── build.gradle               # AGP 8.2.0
├── settings.gradle            # 模块配置
├── gradlew / gradle/          # Gradle 8.5
└── API.md                    # API 文档
```

### 1.2 组件关系图

```
Sender                               Receiver
   │                                   │
   ├─ ScreenCapturer ──────────────┼─ VideoDecoder
   │         │                          │
   ├─ VideoEncoder ──→ UDP ──→ VideoDecoder
   │         │                          │
   ├─ TouchEncoder ──→ UDP ──→ TouchDecoder
   │         │                          │
   │         └─ P2pConnectionManager ──┘
   │                        │
   └─ HeartbeatMonitor ←──────→ HeartbeatMonitor
```

### 1.3 核心设计模式

| 模式 | 实现 |
|------|------|
| 工厂模式 | `ScreenshareSDK.createSender/createReceiver` |
| 状态机 | `SenderState` / `ReceiverState` / `ConnectionStateMachine` |
| 观察者 | `EventListener` 接口回调 |
| 对象池 | `AtomicBuffer` 预分配复用 |
| 心跳 | `HeartbeatMonitor` |
| 自适应 | `BandwidthAdapter` |

---

## 二、开发工作流

### 2.1 Git 工作流

```bash
# 1. 开始新功能/修复
git checkout master
git pull origin master

# 2. 创建分支
git checkout -b feature/xxx

# 3. 开发 + 测试
./gradlew :screenshare-sdk:assembleDebug :screenshare-app:assembleDebug
./gradlew :screenshare-sdk:testDebugUnitTest

# 4. 提交
git add -A
git commit -m "描述"

# 5. 合并回 master
git checkout master
git merge feature/xxx
git push origin master
```

### 2.2 提交信息规范

```
<类型>: <简短描述>

可选的详细说明

Types:
- Feature: 新功能
- Fix: Bug 修复
- Optimize: 性能优化
- Refactor: 重构
- Docs: 文档更新
- Test: 测试相关
```

### 2.3 构建命令

```bash
# 编译
./gradlew :screenshare-sdk:assembleDebug       # SDK
./gradlew :screenshare-app:assembleDebug      # App
./gradlew :screenshare-sdk:assembleDebug :screenshare-app:assembleDebug  # 同时

# 测试
./gradlew :screenshare-sdk:testDebugUnitTest  # 单元测试
./gradlew :screenshare-app:testDebugUnitTest

# 清理
./gradlew clean

# 查看依赖
./gradlew :screenshare-app:dependencies
```

---

## 三、组件详细说明

### 3.1 核心类

#### ScreenshareSDK（入口）

```java
// 创建发送端
Sender createSender(Context context, SenderConfig config)

// 创建接收端
Receiver createReceiver(Context context, ReceiverConfig config)

// 版本
String getVersion()   // "1.0.0"
int getVersionCode()   // 100
```

#### Sender（发送端）

状态：`IDLE → CAPTURING → DISCOVERING → CONNECTING ↔ STREAMING`

关键方法：
- `startCapture()` - 开始采集
- `connect(peerAddress)` - 连接对等端
- `sendTouchEvent(timestamp, action, x, y)` - 发送触摸

#### Receiver（接收端）

状态：`LISTENING → ACCEPTED → STREAMING`

关键方法：
- `startListening()` - 开始监听
- `setEventListener()` - 设置触摸回调

### 3.2 Common 模块

| 类 | 功能 | 关键方法 |
|-----|------|----------|
| `AtomicBuffer` | GC-free 缓冲区 | `write()`, `read()`, `compact()` |
| `BandwidthAdapter` | 带宽自适应 | `reportLossRate()`, `getSuggestedBitrate()` |
| `ConnectionStateMachine` | 统一状态机 | `getState()`, `forceState()` |
| `ErrorCode` | 错误码 | `getDescription()` |
| `HeartbeatMonitor` | 心跳 | `notifyAlive()`, `start()`, `stop()` |
| `LatencyTester` | 延迟测试 | `recordSend()`, `recordReceive()` |
| `ReconnectionManager` | 重连 | `shouldRetry()`, `onConnectionLost()` |

### 3.3 Codec 模块

| 类 | 功能 |
|-----|------|
| `VideoEncoder` | 硬件 H.264/H.265 编码 |
| `VideoDecoder` | 硬件 H.264/H.265 解码 |
| `VideoDecoderIntegrator` | TextureView 集成 |
| `AudioCapture` | 麦克风采集 |
| `AudioEncoder` | PCM→AAC |
| `AudioDecoder` | AAC→PCM |
| `AudioPlayer` | PCM 播放 |

### 3.4 Network 模块

| 类 | 协议 |
|-----|------|
| `UdpChannel` | UDP 单播 |
| `RtpSession` | RTP 打包/解包（支持 FU-A 分片）|

### 3.5 WiFi P2P 模块

| 类 | 功能 |
|-----|------|
| `P2pConnectionManager` | 发现/连接管理 |
| `P2pBroadcastReceiver` | 广播接收器 |
| `ServiceDiscovery` | 服务公告（IP:Port）|

### 3.6 Touch 模块

| 类 | 功能 |
|-----|------|
| `TouchEncoder` | 触摸序列化（18 bytes/事件）|
| `TouchDecoder` | 触摸反序列化 |
| `TouchInjectorService` | AccessibilityService 注入 |

---

## 四、测试策略

### 4.1 单元测试（当前）

- 位置：`screenshare-sdk/src/test/java/com/screenshare/sdk/`
- 框架：JUnit 4
- 测试数：91 个
- 运行：`./gradlew :screenshare-sdk:testDebugUnitTest`

**已测试：**
- `AtomicBufferTest` - 缓冲区操作、线程安全
- `ErrorCodeTest` - 错误码描述
- `SenderConfigTest` / `ReceiverConfigTest` - 配置验证
- `RtpSessionTest` - RTP 打包
- `TouchEncoderTest` / `TouchDecoderTest` - 触摸序列化
- 其他配置/枚举测试

### 4.2 Android 相关测试限制

以下组件需要 Robolectric 才能完整测试（当前为占位测试）：
- `HeartbeatMonitor` - `android.os.Handler` 依赖
- `LatencyTester` - `SystemClock` 依赖
- `ReconnectionManager` - `Handler` 依赖
- `ConnectionStateMachine` - `Handler` 依赖

### 4.3 手动测试清单

| 功能 | 测试方法 |
|------|----------|
| WiFi P2P 发现 | 两台设备，开启 WiFi P2P，搜索 |
| 屏幕采集 | 发送端授权 MediaProjection |
| 视频传输 | 接收端 TextureView 渲染 |
| 触摸反控 | 接收端触摸，发送端响应 |
| 重连 | 断开连接，观察自动重连 |
| 心跳 | 断开心跳，观察超时 |

---

## 五、已知问题与限制

### 5.1 设计限制

| 问题 | 原因 | 状态 |
|------|------|------|
| `AtomicBuffer.compact()` 分配 temp byte[] | 简化实现 | 已知，可优化 |
| `getLossRate()` 永远返回 0 | 无实际丢包统计 | 已知 |
| `RtpSession` 不重组 FU-A | 简化实现 | 可改进 |
| `AudioPipeline` 未完整集成 | 占位实现 | 待完善 |

### 5.2 测试限制

| 组件 | 限制 |
|------|------|
| `TouchInjectorService` | 需要 AccessibilityService 开启 |
| `ScreenCaptureService` | 需要 MediaProjection 权限 |
| WiFi P2P | 需要两台真机 |

### 5.3 已修复的 Bug

| Bug | 文件 | 修复 |
|-----|------|------|
| ByteBuffer 跨线程重用 | `TouchEncoder.java` | ✅ 2026-05-01 |
| pendingSendTimestamp 不清除 | `HeartbeatMonitor.java` | ✅ 2026-05-01 |
| pendingSendTimestamp 不清除 | `LatencyTester.java` | ✅ 2026-05-01 |
| ConcurrentModificationException | `ServiceDiscovery.java` | ✅ 2026-05-01 |

---

## 六、文件编码规范

### 6.1 包结构

```java
package com.screenshare.sdk.<module>;

// 模块: Common, capture, codec, network, service, touch, wifi
```

### 6.2 命名规范

| 类型 | 规范 | 示例 |
|------|------|------|
| 类名 | UpperCamelCase | `ScreenCapturer` |
| 方法名 | lowerCamelCase | `startCapture` |
| 常量 | UPPER_SNAKE_CASE | `ERR_PERMISSION_DENIED` |
| 枚举 | UpperCamelCase | `SenderState.IDLE` |
| 包名 | lowercase | `com.screenshare.sdk` |

### 6.3 文档要求

每个公开类/方法需要 Javadoc：
```java
/**
 * 类描述
 *
 * @param paramName 参数说明
 * @return 返回值说明
 * @throws Exception 可能抛出的异常
 */
```

---

## 七、版本更新记录

| 版本 | 日期 | 更新内容 |
|------|------|----------|
| 1.0.0 | 2026-05-01 | 初始完成：SDK + App + 测试 |

---

## 八、紧急情况处理

### 8.1 构建失败

```bash
# 1. 清理 Gradle 缓存
./gradlew clean --refresh-dependencies

# 2. 删除 .gradle 目录
rm -rf .gradle build

# 3. 重新构建
./gradlew :screenshare-sdk:assembleDebug :screenshare-app:assembleDebug
```

### 8.2 Git 冲突

```bash
# 1. 保存本地更改
git stash

# 2. 拉取远程
git pull origin master

# 3. 恢复更改
git stash pop

# 4. 手动解决冲突后
git add -A
git commit
```

### 8.3 测试失败

```bash
# 运行单个测试类
./gradlew :screenshare-sdk:testDebugUnitTest --tests "com.screenshare.sdk.AtomicBufferTest"

# 查看详细输出
./gradlew :screenshare-sdk:testDebugUnitTest --info
```

---

*此文档为维护者内部使用*
