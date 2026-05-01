# Android 超低延时投屏 SDK - 完整项目开发计划（v3）

> **项目类型**：完整可商用的 Android 投屏 SDK + App
> **目标**：< 50ms 端到端视频延迟，WiFi P2P 直连，完整 App 可安装运行
> **版本**：v3.0（新增：验收标准、Gradle 依赖冲突检查、发布流程）

---

## 一、现状分析

### 1.1 现有代码规模

| 模块 | 文件数 | 代码行数 | 备注 |
|------|--------|----------|------|
| Java 源码 | 10 个 | ~4000 行 | 基础功能已有 |
| 布局文件 | 3 个 | - | 仅 3 个 Activity |
| 配置 | Gradle 8.0.0 | - | AGP 8.x 需适配 |

### 1.2 现有架构问题（代码审查发现）

| 问题 | 级别 | 说明 |
|------|------|------|
| 所有逻辑在 Activity | [MAJOR] | 应抽取 SDK，Activity 只做 UI |
| 无错误恢复机制 | [MAJOR] | 断开即崩溃，无重连 |
| GC 压力 | [MAJOR] | ByteBuffer 动态分配 |
| WiFi P2P 无服务发现 | [MINOR] | 无法动态公告 IP/Port |
| 无权限拒绝处理 | [MINOR] | 用户拒绝权限后无提示 |
| minSdk=24 过低 | [NIT] | 低版本设备兼容性差，建议 minSdk=26 |

---

## 二、项目架构

### 2.1 双模块结构

```
screenshare/
├── build.gradle                          # 根项目配置
├── settings.gradle                       # 包含 screenshare-sdk 和 screenshare-app
│
├── screenshare-sdk/                     # ★ 核心 SDK（AAR 发布）
│   ├── build.gradle
│   └── src/main/
│       ├── java/com/screenshare/sdk/
│       │   ├── Capture/                 # 屏幕采集
│       │   ├── Codec/                   # 编解码
│       │   ├── Network/                 # 网络传输（RTP/UDP）
│       │   ├── Touch/                   # 触摸反控
│       │   └── Common/                 # 工具类
│       └── AndroidManifest.xml          # SDK 清单（无 Activity）
│
└── screenshare-app/                     # ★ App 主程序
    ├── build.gradle                     # 依赖 screenshare-sdk
    └── src/main/
        ├── java/com/screenshare/app/
        │   ├── ui/                     # Activity + Fragment
        │   ├── service/                # Foreground Service
        │   └── wifi/                   # WiFi P2P
        ├── res/                         # 布局 + 资源
        └── AndroidManifest.xml
```

### 2.2 SDK 对外 API 设计

```java
// ScreenshareSDK.java - SDK 唯一入口
public class ScreenshareSDK {
    
    // 创建发送端实例
    public static Sender createSender(Context context, SenderConfig config);
    
    // 创建接收端实例
    public static Receiver createReceiver(Context context, ReceiverConfig config);
    
    // 版本信息
    public static String getVersion();  // "1.0.0"
    public static int getVersionCode(); // 100
}

// Sender API
public class Sender {
    public void startScreenCapture();        // 开始录屏
    public void stopScreenCapture();         // 停止录屏
    public void discoverPeers();            // 发现设备
    public void connect(Device device);     // 连接设备
    public void disconnect();                // 断开连接
    public void release();                   // 释放资源
    
    public void setEventListener(SenderEventListener listener);
}

// Receiver API
public class Receiver {
    public void startListening();            // 开始监听（作为服务器）
    public void stopListening();            // 停止监听
    public void disconnect();               // 断开连接
    public void release();                  // 释放资源
    
    public void setEventListener(ReceiverEventListener listener);
}
```

---

## 三、技术规格

### 3.1 兼容性要求

| 项目 | 要求 |
|------|------|
| minSdk | 26（Android 8.0，2017年后设备） |
| targetSdk | 34（Android 14） |
| compileSdk | 34 |
| Java | 1.8（Lambda + 方法引用） |
| Kotlin | 可选（先用 Java 快速开发） |

### 3.2 Gradle 依赖

```groovy
// screenshare-sdk/build.gradle
dependencies {
    implementation 'androidx.annotation:annotation:1.7.0'
    implementation 'androidx.core:core:1.12.0'
    
    // 如果使用 Java 8+ API（desktop.jar 兼容）
    coreLibraryDesugaring 'com.android.tools:desugar_jdk_libs:2.0.4'
}

// screenshare-app/build.gradle
dependencies {
    implementation project(':screenshare-sdk')
    
    // AndroidX
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation 'com.google.android.material:material:1.11.0'
    implementation 'androidx.constraintlayout:constraintlayout:2.1.4'
    implementation 'androidx.lifecycle:lifecycle-service:2.7.0'
    
    // WiFi P2P
    implementation 'androidx.legacy:legacy-support-v4:1.0.0'
    
    // 测试
    testImplementation 'junit:junit:4.13.2'
    androidTestImplementation 'androidx.test.ext:junit:1.1.5'
    androidTestImplementation 'androidx.test.espresso:espresso-core:3.5.1'
}
```

### 3.3 AndroidManifest 权限清单

```xml
<!-- 必须权限（拒绝则无法运行） -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION" />
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

<!-- WiFi P2P 权限（拒绝可降级到局域网） -->
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
<uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.NEARBY_WIFI_DEVICES" 
    android:usesPermissionFlags="neverForLocation" />

<!-- 触摸注入权限（接收端 Root 刷入，非 Root 可用 AccessibilityService） -->
<uses-permission android:name="android.permission.INJECT_EVENTS" 
    tools:ignore="ProtectedPermissions" />

<!-- 可选权限（用于发现设备） -->
<uses-permission android:name="android.permission.RECORD_AUDIO" />
```

---

## 四、延迟指标与测量

### 4.1 硬性指标（必须达标）

| 指标 | 目标 | 测量方法 |
|------|------|----------|
| 视频延迟 | < 50ms | 高速相机（1000fps）拍摄手指触控到画面响应 |
| 触摸延迟 | < 10ms | 发送端打 log(touch_send)，接收端打 log(touch_recv)，计算差值 |
| 连接建立 | < 3s | 计时从点击"连接"到首帧显示 |
| 帧率 | 持续 60fps | adb shell dumpsys SurfaceFlinger |
| CPU 占用 | < 20%（持续投屏） | Android Studio Profiler |
| 内存峰值 | < 200MB | Android Studio Profiler |

### 4.2 测量工具

```bash
# 1. LatencyTester.java - 内置延迟测试
adb shell am start -n com.screenshare.app/.LatencyTester
# 输出：avg=38ms, min=31ms, max=52ms, p95=45ms

# 2. DebugOverlay - 实时显示延迟（开发者选项）
# 设置 → 开发者选项 → 显示触摸 → 开启
# 配合高速相机回放测量

# 3. systrace 完整链路追踪
python systrace.py -o trace.html sched freq idle am wm gfx view webkit
```

---

## 五、6 周详细计划（含验收标准）

### Week 1：项目骨架（必须通过编译）

**目标**：两个 Gradle 模块建立，SDK 可被 App 依赖

| Day | 任务 | 验收标准 |
|-----|------|----------|
| 1 | 建立 `screenshare-sdk` + `screenshare-app` 模块 | `./gradlew :screenshare-sdk:assembleDebug` 成功 |
| 2 | 配置 `settings.gradle` 多模块包含 | 无编译错误 |
| 3 | 迁移现有 Java 代码到 SDK | SDK 可编译 |
| 4 | Activity 代码迁移到 App 模块 | App 可编译运行 |
| 5 | 配置 `coreLibraryDesugaring`（Java 8 API） | minSdk=24 也能用 Java 8 |

**产出**：`build.gradle`, `settings.gradle`, SDK 模块结构

---

### Week 2：屏幕采集 + 编码（必须跑通 Demo）

**目标**：单设备 loopback 测试：录屏 → 编码 → 解码 → 显示

| Day | 任务 | 验收标准 |
|-----|------|----------|
| 6-7 | `ScreenCapturer`：VirtualDisplay 采集 | Surface 可正常输出帧 |
| 8-9 | `VideoEncoder`：低延迟编码（KEY_LATENCY=1） | 编码延迟 < 15ms |
| 10 | `VideoDecoder`：解码到 SurfaceView | 解码延迟 < 10ms |
| 11 | 本地 loopback 测试 | 端到端 < 40ms |

**产出**：`ScreenCapturer.java`, `VideoEncoder.java`, `VideoDecoder.java`

**关键技术**：MediaCodec InputSurface + OutputSurface 配对使用

---

### Week 3：网络传输（RTP + UDP）

**目标**：两台设备之间视频流传输正常

| Day | 任务 | 验收标准 |
|-----|------|----------|
| 12 | `RtpPacketizer`：NAL → RTP 打包 | 单包 + FU-A 分片正常 |
| 13 | `UdpChannel`：发送 / 接收通道 | UDP 包不丢包 |
| 14 | `PriorityQueue`：触摸优先队列 | 触摸事件先于普通帧发出 |
| 15 | 两台设备 UDP 传输测试 | 视频流正常显示 |
| 16 | **延迟测试**（使用 screen-record-frames） | 各环节延迟数据 |

**产出**：`RtpPacketizer.java`, `UdpChannel.java`, `PriorityQueue.java`

---

### Week 4：WiFi P2P 集成

**目标**：设备发现 → 连接 → 建立视频通道 < 3s

| Day | 任务 | 验收标准 |
|-----|------|----------|
| 17-18 | `WifiP2pManager`：发现 + 连接 | 发现到连接 < 3s |
| 19 | `ServiceDiscovery`：公告 IP:Port | 接收端正确获取发送端地址 |
| 20 | `ConnectionStateMachine`：状态机 | 6 个状态切换正确 |
| 21 | `ReconnectionManager`：断开重连 | 断开 3s 内自动重连 |

**产出**：`WifiP2pManager.java`, `ServiceDiscovery.java`, `ReconnectionManager.java`

---

### Week 5：触摸反控

**目标**：触摸延迟 < 10ms，多指触控

| Day | 任务 | 验收标准 |
|-----|------|----------|
| 22 | `TouchEncoder`：序列化 | < 0.5ms 完成 |
| 23 | `TouchDecoder`：反序列化 + 注入 | 支持 5 指触控 |
| 24 | 两种注入方案：AccessibilityService（默认）+ Root（可选） | 无 Root 时也能用 |
| 25 | **触摸延迟测试** | < 10ms |

**产出**：`TouchEncoder.java`, `TouchDecoder.java`, `TouchInjector.java`

---

### Week 6：App 界面

**目标**：完整可安装运行的 App

| Day | 任务 | 验收标准 |
|-----|------|----------|
| 26 | `MainActivity`：角色选择 | 界面美观 |
| 27 | `SenderActivity`：发送端界面（设备列表 + 状态） | 设备列表展示 |
| 28 | `ReceiverActivity`：接收端界面（视频显示） | 视频流正常渲染 |
| 29 | `ForegroundService`：后台投屏服务 |通知栏显示，投屏不被系统杀死 |
| 30 | 异常处理 + 用户提示 | 权限拒绝、连接失败有友好提示 |

**产出**：完整的 App UI + Service

---

### Week 7：优化 + 集成测试

**目标**：达到 < 50ms 延迟，App 可发布

| Day | 任务 | 验收标准 |
|-----|------|----------|
| 31-32 | Buffer 全部预分配，移除 GC | Profiler 无 GC 调用 |
| 33 | TextureView vs SurfaceView 对比测试 | 选择最优渲染方案 |
| 34 | 端到端延迟测试（必须 < 50ms） | 实测数据记录 |
| 35 | 多设备兼容性测试（至少 3 台） | 通过 |
| 36 | 打包 AAR（SDK）+ APK（App） | 均可安装运行 |

**产出**：可发布的 AAR 和 APK

---

## 六、SDK API 详细设计

### 6.1 SenderConfig

```java
public class SenderConfig {
    public int width = 1920;           // 采集分辨率
    public int height = 1080;
    public int fps = 60;                // 帧率
    public int bitrate = 8_000_000;    // 8Mbps
    public int iFrameInterval = 1;     // 关键帧间隔（秒）
    
    // 低延迟模式（默认开启，Android 10+）
    public boolean lowLatencyMode = true;
    
    // 编码优先级（默认速度优先）
    public int encoderComplexity = 1;   // Android 11+
    
    // 网络
    public int videoUdpPort = 8888;     // 视频流端口
    public int touchUdpPort = 8889;    // 触摸端口
    
    // WiFi P2P
    public boolean useWifiP2p = true;  // false 则用局域网
}
```

### 6.2 ReceiverConfig

```java
public class ReceiverConfig {
    public boolean useSurfaceView = false;  // false = TextureView（更低延迟）
    public int maxBufferCount = 2;          // 双 Buffer（减少卡顿）
    
    // 触摸注入方式
    public enum TouchMode {
        ACCESSIBILITY_SERVICE,  // 默认，兼容所有设备
        ROOT_INJECTION,          // 需要 Root，更低延迟
    }
    public TouchMode touchMode = TouchMode.ACCESSIBILITY_SERVICE;
}
```

---

## 七、错误处理规范

### 7.1 错误码定义

```java
// ScreenshareSDK.ErrorCode
public class ErrorCode {
    public static final int SUCCESS = 0;
    
    // 权限相关（1xxx）
    public static final int PERMISSION_DENIED = 1001;
    public static final int PERMISSION_SYSTEM_ALERT_WINDOW = 1002;
    
    // 屏幕采集相关（2xxx）
    public static final int CAPTURE_NOT_SUPPORTED = 2001;
    public static final int CAPTURE_ALREADY_RUNNING = 2002;
    
    // 编码器相关（3xxx）
    public static final int ENCODER_NOT_FOUND = 3001;
    public static final int ENCODER_INIT_FAILED = 3002;
    public static final int ENCODER_BITSTREAM_ERROR = 3003;
    
    // 网络相关（4xxx）
    public static final int NETWORK_DISCONNECTED = 4001;
    public static final int NETWORK_TIMEOUT = 4002;
    public static final int NETWORK_PORT_IN_USE = 4003;
    
    // WiFi P2P 相关（5xxx）
    public static final int P2P_NOT_SUPPORTED = 5001;
    public static final int P2P_CONNECTION_FAILED = 5002;
    public static final int P2P_PEER_NOT_FOUND = 5003;
}
```

### 7.2 状态机

```
Sender 状态机：
  IDLE
    ↓ startScreenCapture()
  CAPTURING
    ↓ discoverPeers()
  DISCOVERING
    ↓ connect(peer)
  CONNECTING
    ↓ 连接成功
  STREAMING ←→ CONNECTION_LOST（自动重连）
    ↓ stopScreenCapture()
  IDLE

Receiver 状态机：
  LISTENING
    ↓ acceptConnection()
  ACCEPTED
    ↓ 开始接收视频
  STREAMING
    ↓ disconnect()
  LISTENING
```

---

## 八、发布流程

### 8.1 SDK 发布（AAR）

```bash
# 1. 版本号更新
# build.gradle: versionName = "1.0.0", versionCode = 100

# 2. 打包 AAR
./gradlew :screenshare-sdk:assembleRelease

# 3. 生成 Maven POM
./gradlew :screenshare-sdk:publishReleasePublicationToMavenRepository

# 4. 输出目录
screenshare-sdk/build/outputs/aar/
```

### 8.2 App 发布（APK）

```bash
# 1. 签名配置（local.properties）
key.store=keystore.jks
key.alias=screenshare
key.store.password=******
key.alias.password=******

# 2. 构建 Release APK
./gradlew :screenshare-app:assembleRelease

# 3. 输出目录
screenshare-app/build/outputs/apk/release/
```

### 8.3 SDK 集成方式（App 使用 SDK）

```groovy
// settings.gradle
include ':screenshare-sdk'
project(':screenshare-sdk').projectDir = new File(rootProject.projectDir, '../screenshare-sdk')

// screenshare-app/build.gradle
dependencies {
    implementation project(':screenshare-sdk')
}
```

---

## 九、风险与备选方案

| 风险 | 缓解方案 | 降级策略 |
|------|----------|----------|
| WiFi P2P 不稳定 | `ReconnectionManager` 3次重连 | 降级到 WiFi 局域网 TCP |
| 设备不支持低延迟编码 | 检测 `KEY_LATENCY` 支持 | 关闭低延迟模式 |
| 触摸注入需要 Root | AccessibilityService 作为默认 | 提供"高级模式"开关 |
| 编码 CPU 占用过高 | 降比特率到 4Mbps 或降帧率到 30fps | 降低质量保流畅 |
| 不同芯片兼容性问题 | 多设备测试（至少 3 台） | 软解码 H.264 作为 Fallback |

---

## 十、下一步行动清单

```
□ 1. 确认方案 → 开始 Week 1 项目骨架搭建
□ 2. 申请 Tavily API Key（联网搜索需要）
□ 3. 准备测试设备（至少 2 台 Android 设备）
□ 4. 配置 keystore（签名用）
□ 5. 确认 Gradle 和 AGP 版本（推荐 AGP 8.2 + Gradle 8.4）
```

---

*文档版本：v3.0*
*最后更新：2026-05-01*
*状态：等待确认后启动开发*
---

## 十一、补充：当前项目缺失清单

> 以下内容在现有代码中不存在，但在完整项目中必须实现

### 11.1 必须新增的组件

| 组件 | 计划中位置 | 现状 | 优先级 |
|------|------------|------|--------|
| Gradle Wrapper | Week 1 Day 1 | 不存在 | 🔴 必须 |
| ForegroundService | Week 6 Day 29 | 占位符 | 🔴 必须 |
| AccessibilityService（触摸） | Week 5 Day 24 | 不存在 | 🔴 必须 |
| ReconnectionManager | Week 4 Day 21 | 不存在 | 🔴 必须 |
| ServiceDiscovery | Week 4 Day 19 | 不存在 | 🟡 高 |
| ConnectionStateMachine | Week 4 Day 20 | 无状态机 | 🟡 高 |
| HeartbeatMonitor | Week 4 | 无心跳 | 🟡 高 |
| LatencyTester | Week 7 Day 34 | 无内置工具 | 🟡 中 |

### 11.2 包名重命名

```
com.example.screenmirror → com.screenshare.app
com.example.screenmirror → com.screenshare.sdk  (SDK模块)
```

涉及：
- AndroidManifest.xml (namespace + package)
- 所有 Java 文件的 package 声明
- build.gradle 的 applicationId 和 namespace
- 所有 import 语句

### 11.3 完整布局文件清单

现有 3 个 → 需要至少 8 个：

```
res/layout/
├── activity_main.xml        # ✅ 已有（角色选择）
├── activity_sender.xml      # ✅ 已有（发送端）
├── activity_receiver.xml    # ✅ 已有（接收端）
├── activity_settings.xml    # ⬜ 新增（设置页面）
├── activity_latency_test.xml # ⬜ 新增（延迟测试工具）
├── dialog_loading.xml       # ⬜ 新增（连接中对话框）
├── item_device.xml          # ⬜ 新增（设备列表项）
└── item_log.xml             # ⬜ 新增（日志列表项）
```

### 11.4 测试框架配置

```groovy
// 测试用例示例
// test/java/com/screenshare/app/
// ├── SenderTest.java       # 发送端单元测试
// └── ReceiverTest.java     # 接收端单元测试
// androidTest/java/com/screenshare/app/
// └── SenderActivityTest.java  # UI 测试
```

---

*文档版本：v3.1*
*新增：当前项目缺失清单、包名重命名、完整布局文件清单、测试框架配置*
