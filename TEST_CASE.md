# AndroidScreenMirror 测试用例文档

| 版本 | 日期 | 作者 | 描述 |
|------|------|------|------|
| 1.0 | 2026-05-01 | SDK Team | 初始版本，覆盖单元/集成/性能/压力/兼容性测试 |

---

## 1. 测试概述

### 1.1 测试目标

本测试用例文档针对 **AndroidScreenMirror** 超低延迟屏幕镜像 SDK，制定完整的测试策略和用例，确保：

- **功能正确性**：所有 SDK 模块（编码/解码/传输/触摸/网络）按预期工作
- **性能达标**：端到端延迟 < 50ms，触摸延迟 < 10ms
- **稳定性**：长时间运行、压力条件下无崩溃、无内存泄漏
- **兼容性**：覆盖主流 Android 版本、屏幕分辨率、设备硬件

### 1.2 测试范围

| 模块 | 测试内容 |
|------|---------|
| **Common** | AtomicBuffer、ErrorCode、SenderState、ReceiverState、ConnectionStateMachine、HeartbeatMonitor、LatencyTester、ReconnectionManager、BandwidthAdapter |
| **Codec** | VideoEncoder、VideoDecoder、VideoDecoderIntegrator、AudioCapture、AudioEncoder、AudioDecoder、AudioPlayer |
| **Network** | UdpChannel、RtpSession |
| **Capture** | ScreenCapturer |
| **Service** | ScreenCaptureService、ScreenMirroringService |
| **Touch** | TouchEncoder、TouchDecoder、TouchInjectorService |
| **WiFi** | P2pConnectionManager、P2pBroadcastReceiver、ServiceDiscovery |
| **Config** | SenderConfig、ReceiverConfig |

### 1.3 测试策略

```
┌─────────────────────────────────────────────────────────────┐
│                     测试分层策略                              │
├─────────────────────────────────────────────────────────────┤
│  第1层：单元测试 (Unit Test) - 开发阶段持续执行              │
│    → Java/JUnit，对每个公开方法做路径覆盖                    │
│    → Mock 依赖，保持测试快速可重复                           │
│                                                             │
│  第2层：集成测试 (Integration Test) - 功能完成后执行         │
│    → 真实组件串接，验证模块间协议正确性                      │
│    → 使用 LocalUnitTest + InstrumentedTest                  │
│                                                             │
│  第3层：性能测试 (Performance Test) - 里程碑节点执行         │
│    → 延迟、带宽、CPU/内存专项测量                           │
│    → 目标：视频 <50ms，触摸 <10ms                           │
│                                                             │
│  第4层：压力测试 (Stress Test) - 发布前执行                  │
│    → 长时间运行、网络切换、多设备并发                        │
│                                                             │
│  第5层：兼容性测试 (Compatibility Test) - 多设备矩阵执行     │
│    → Android 版本 × 屏幕分辨率 × 设备型号                   │
└─────────────────────────────────────────────────────────────┘
```

---

## 2. 单元测试用例

> 已在 `screenshare-sdk/src/test/java/com/screenshare/sdk/` 下有 **13 个测试文件，共 91 个测试**，覆盖 10 个核心组件。已实现部分持续标注。

---

### 2.1 AtomicBuffer 测试

**类**：`com.screenshare.sdk.Common.AtomicBuffer`
**已有**：`AtomicBufferTest.java` (152 行)
**覆盖**：✅ 已覆盖

| 用例 ID | 描述 | 输入 | 预期结果 |
|---------|------|------|---------|
| AB-001 | 构造函数分配直接内存 | size=1MB | ByteBuffer.allocateDirect 分配成功，capacity == 1048576 |
| AB-002 | write 正常写入 | data[100B]，offset=0，length=100 | writePosition == 100，availableToWrite 减少 100 |
| AB-003 | write 超出容量 | data[2MB]，容量 1MB | 写入 1MB，返回实际写入量 |
| AB-004 | read 正常读取 | 写入 200B 后读取 | availableToRead == 200，读出数据一致 |
| AB-005 | 环形缓冲区 wrap | 写入到容量上限后重置 | writePosition/readPosition 归零，buffer clear |
| AB-006 | 线程安全并发写入 | 4 线程同时写入 | 无数据丢失，无竞争异常 |
| AB-007 | 线程安全并发读写 | 2 写 2 读线程 | 读写位置原子操作正确，最终数据完整 |
| AB-008 | availableToWrite 计算 | 填充 50% 后 | 可写空间 = capacity × 50% |
| AB-009 | availableToRead 计算 | 写入 300B | availableToRead == 300 |
| AB-010 | 引用计数 refCount | 增加/减少引用 | refCount 原子增减为 0 |

---

### 2.2 ErrorCode 测试

**类**：`com.screenshare.sdk.Common.ErrorCode`
**已有**：`ErrorCodeTest.java` (80 行)
**覆盖**：✅ 已覆盖

| 用例 ID | 描述 | 输入 | 预期结果 |
|---------|------|------|---------|
| EC-001 | 所有错误码有描述 | 所有 ErrorCode 枚举值 | getDescription() 均返回非空字符串 |
| EC-002 | 错误码分类完整 | 检查 NETWORK/CODEC/TOUCH/WIFI 分类 | 每个分类至少有一个错误码 |
| EC-003 | 网络类错误码 | TIMEOUT/CONNECTION_REFUSED/UNREACHABLE | 描述包含 "network" 或 "连接" 关键词 |
| EC-004 | 编解码类错误码 | ENCODER_INIT_FAIL/DECODER_INIT_FAIL | 描述包含 "codec" 或 "编码" 关键词 |
| EC-005 | 触摸类错误码 | INJECT_FAIL/PERMISSION_DENIED | 描述包含 "touch" 或 "触摸" 关键词 |

---

### 2.3 SenderConfig / ReceiverConfig 测试

**类**：
- `com.screenshare.sdk.SenderConfig`
- `com.screenshare.sdk.ReceiverConfig`

**已有**：
- `SenderConfigTest.java` (91 行) ✅
- `ReceiverConfigTest.java` (90 行) ✅

| 用例 ID | 描述 | 输入 | 预期结果 |
|---------|------|------|---------|
| SC-001 | 默认值初始化 | new SenderConfig() | width=1920, height=1080, fps=60 |
| SC-002 | 设置视频分辨率 | width=1280, height=720 | 分辨率正确保存 |
| SC-003 | 设置帧率 | fps=30 | fps == 30 |
| SC-004 | 设置码率 | videoBitrate=5000000 | videoBitrate == 5000000 |
| SC-005 | 设置编码器类型 | videoCodec=H265_HARDWARE | videoCodec == H265_HARDWARE |
| SC-006 | 端口配置 | videoPort=9999, touchPort=9998 | 端口号正确 |
| SC-007 | 性能预设验证 | performancePreset=LOW_LATENCY | preset == LOW_LATENCY |
| SC-008 | 配置复制/克隆 | clone 一个 SenderConfig | 属性完全一致 |
| RC-001 | 默认值初始化 | new ReceiverConfig() | 接收端默认参数正确初始化 |
| RC-002 | 解码器类型 | videoCodec=H264_HARDWARE | 与发送端协商后匹配 |
| RC-003 | 音频使能 | withAudio=true | 音频解码器正确初始化 |

---

### 2.4 RtpSession 测试

**类**：`com.screenshare.sdk.network.RtpSession`
**已有**：`RtpSessionTest.java` (172 行)
**覆盖**：✅ 已覆盖

| 用例 ID | 描述 | 输入 | 预期结果 |
|---------|------|------|---------|
| RS-001 | 小包（<MTU）RTP 打包 | NAL=1200B | 产生单个 RTP 包，序列号递增 |
| RS-002 | 大包（>MTU）分片 | NAL=3000B | 产生 3 个 FU-A 分片，start/end marker 正确 |
| RS-003 | RTP 头字段验证 | 发送后抓包 | V=2, PT=96, SSRC 固定, timestamp 递增 |
| RS-004 | RTP 包解析（接收端） | 收到 RTP 包 | parseRtpPacket 返回 NAL 单元 |
| RS-005 | FU-A 重组 | 分片序列到达 | 重组为完整 NAL |
| RS-006 | 单 NAL 类型解析 | NAL type 1-23 | 直接返回 NAL 数据 |
| RS-007 | 时间戳计算 | fps=60, timestamp increment | increment = 90000/60 = 1500 |
| RS-008 | session 启动/停止 | start() → stop() | 线程正常创建和销毁 |
| RS-009 | 发送循环 | 编码器队列有帧 | 帧正确出队并发送 |
| RS-010 | receiveMode 切换 | setReceiveMode(true/false) | 路由到正确的循环 |

---

### 2.5 TouchEncoder / TouchDecoder 测试

**类**：
- `com.screenshare.sdk.touch.TouchEncoder`
- `com.screenshare.sdk.touch.TouchDecoder`

**已有**：
- `TouchEncoderTest.java` (114 行) ✅
- `TouchDecoderTest.java` (139 行) ✅

| 用例 ID | 描述 | 输入 | 预期结果 |
|---------|------|------|---------|
| TE-001 | 触摸按下事件编码 | ACTION_DOWN, x=100, y=200 | byte[] 长度正确，包含坐标 |
| TE-002 | 触摸移动事件编码 | ACTION_MOVE, 连续多个点 | 多个坐标点序列化无遗漏 |
| TE-003 | 触摸抬起事件编码 | ACTION_UP | 编码正确 |
| TE-004 | 多指触摸编码 | 2 指同时按下 | 每个手指数据独立编码 |
| TE-005 | 特殊手势编码 | pinch zoom / scroll | 手势参数正确序列化 |
| TE-006 | 编码边界值 | 坐标超出屏幕范围 | 裁剪到有效范围内 |
| TE-007 | 编码空队列 | 无触摸事件时 | 不产生输出，不阻塞 |
| TD-001 | 解码按下事件 | byte[] with ACTION_DOWN | TouchEvent.action == ACTION_DOWN, 坐标正确 |
| TD-002 | 解码移动事件 | byte[] with ACTION_MOVE | 事件序列完整 |
| TD-003 | 解码抬起事件 | byte[] with ACTION_UP | 事件正确 |
| TD-004 | 多指触摸解码 | 2 指数据 | 两路事件独立还原 |
| TD-005 | 解码损坏数据 | 格式错误 byte[] | 跳过错误数据，不崩溃 |
| TD-006 | 解码边界值 | 超大坐标 | 正确还原 |
| TE-008 | 编码后解码一致性 | encode → decode | 还原的 TouchEvent 与原始一致 |
| TD-007 | 时间戳编码解码 | 带 timestamp | 时间戳端到端保持 |

---

### 2.6 UdpChannel 测试

**类**：`com.screenshare.sdk.network.UdpChannel`
**已有**：`UdpChannelTest.java` (90 行)
**覆盖**：✅ 已覆盖

| 用例 ID | 描述 | 输入 | 预期结果 |
|---------|------|------|---------|
| UC-001 | 绑定端口 | port=8888 | Socket 绑定成功，无异常 |
| UC-002 | 发送数据 | send(packet, 0, len) | 数据到达目标地址 |
| UC-003 | 接收数据 | 对端 send 数据 | onReceived 回调触发，数据一致 |
| UC-004 | 通道关闭 | close() | 资源释放，socket 关闭 |
| UC-005 | 发送超时 | 目标不可达 | onError 回调触发 |
| UC-006 | 事件监听器注册 | setEventListener | 回调正确触发 |
| UC-007 | 多 socket 管理 | 多个 UdpChannel | 端口不冲突，隔离正常 |
| UC-008 | 数据截断处理 | 收到超长包 | 只处理有效长度数据 |

---

### 2.7 BandwidthAdapter 测试

**类**：`com.screenshare.sdk.Common.BandwidthAdapter`
**已有**：`BandwidthAdapterTest.java` (64 行)
**覆盖**：✅ 已覆盖

| 用例 ID | 描述 | 输入 | 预期结果 |
|---------|------|------|---------|
| BA-001 | 初始码率设置 | initialBitrate=5Mbps | 初始码率为 5000000 |
| BA-002 | 带宽估计上调 | 网络畅通，RTT 低 | 码率逐步上调 |
| BA-003 | 带宽估计下调 | 丢包率上升 | 码率下调 |
| BA-004 | 码率下限 | 极端低带宽 | 不低于配置的最小码率 |
| BA-005 | 码率上限 | 高带宽稳定 | 不超过配置的 最大码率 |
| BA-006 | 平滑调整 | 带宽波动 | 码率变化逐步推进，无剧烈抖动 |
| BA-007 | 调整回调 | 码率变化 | onBitrateChanged 回调触发 |
| BA-008 | 节流模式 | 开启节流 | 调整间隔拉长 |

---

### 2.8 LatencyTester 测试

**类**：`com.screenshare.sdk.Common.LatencyTester`
**已有**：`LatencyTesterTest.java` (100 行)
**覆盖**：✅ 已覆盖

| 用例 ID | 描述 | 输入 | 预期结果 |
|---------|------|------|---------|
| LT-001 | 单次延迟测量 | 发送 ping，收到 pong | 返回延迟值（毫秒） |
| LT-002 | 统计平均延迟 | 连续 10 次测量 | getAverage() 返回合理均值 |
| LT-003 | 统计最大延迟 | 含 1 次高延迟 | getMax() 返回峰值 |
| LT-004 | 统计最小延迟 | 含 1 次低延迟 | getMin() 返回谷值 |
| LT-005 | 抖动计算 | 延迟序列波动 | getJitter() 反映波动程度 |
| LT-006 | 延迟阈值告警 | 延迟 > threshold | onLatencyWarning 触发 |
| LT-007 | 测量间隔 | 配置 interval=100ms | 按间隔执行，不堆积 |
| LT-008 | 启动/停止控制 | start() → stop() | 线程正确创建和停止 |

---

### 2.9 ReconnectionManager 测试

**类**：`com.screenshare.sdk.Common.ReconnectionManager`
**已有**：`ReconnectionManagerTest.java` (26 行)
**覆盖**：✅ 已覆盖

| 用例 ID | 描述 | 输入 | 预期结果 |
|---------|------|------|---------|
| RM-001 | 触发重连 | connectionLost() | 最多重连 maxReconnect 次 |
| RM-002 | 重连成功 | 第 2 次重连成功 | onReconnected 回调，停止重试 |
| RM-003 | 重连失败 | 全部重连失败 | onReconnectFailed 回调，报告最终失败 |
| RM-004 | 重连间隔退避 | 第 1/2/3 次 | 间隔逐步增大（指数退避） |
| RM-005 | 重连上限 | maxReconnect=3 | 第 4 次不重试，直接失败 |
| RM-006 | 重连过程中断开 | 正在重连时再次断开 | 取消当前重连，重新计数 |

---

### 2.10 ConnectionStateMachine 测试

**类**：`com.screenshare.sdk.Common.ConnectionStateMachine`
**已有**：`ConnectionStateMachineTest.java` (82 行)
**覆盖**：✅ 已覆盖

| 用例 ID | 描述 | 输入 | 预期结果 |
|---------|------|------|---------|
| CS-001 | 初始状态 | new ConnectionStateMachine() | 状态为 DISCONNECTED |
| CS-002 | 转换到 CONNECTING | triggerConnect() | 状态 = CONNECTING |
| CS-003 | 转换到 CONNECTED | 收到连接成功事件 | 状态 = CONNECTED |
| CS-004 | 转换到 DISCONNECTED | 连接断开 | 状态 = DISCONNECTED |
| CS-005 | 非法转换拒绝 | CONNECTING → DISCONNECTED（未完成） | 状态不变，抛出异常 |
| CS-006 | 状态监听器 | 注册 listener | 所有状态变化触发 onStateChanged |
| CS-007 | 重连状态 | CONNECTED → RECONNECTING | 状态正确 |
| CS-008 | 错误状态 | 发生错误 | 状态 = ERROR |

---

## 3. 集成测试用例

> 集成测试使用 Android Instrumented Test，在真实设备或模拟器上执行。

### 3.1 WiFi P2P 发现与连接

| 用例 ID | 描述 | 步骤 | 预期结果 |
|---------|------|------|---------|
| IT-P2P-001 | P2P 发现设备 | 1. 开启 WiFi<br>2. 调用 startDiscovery()<br>3. 另一设备进入可被发现状态 | 发现对方设备，回调 onDeviceFound() |
| IT-P2P-002 | P2P 连接建立 | 调用 connect(deviceAddress) | 完成 Go 协商，状态转为 CONNECTED |
| IT-P2P-003 | P2P 断开 | 调用 disconnect() | 连接关闭，状态转为 DISCONNECTED |
| IT-P2P-004 | 服务注册 | 调用 addLocalService() | 其他设备能通过服务名发现 |
| IT-P2P-005 | 服务发现 | 调用 discoverServices() | 获取对方服务详情 |
| IT-P2P-006 | 连接失败处理 | 连接不存在的设备 | onConnectionFailed()，正确报错 |
| IT-P2P-007 | 多设备发现 | 范围内有 3+ 设备 | 返回所有设备列表 |
| IT-P2P-008 | 发现超时 | 30s 无响应 | 超时回调，不无限等待 |

### 3.2 屏幕采集与编码

| 用例 ID | 描述 | 步骤 | 预期结果 |
|---------|------|------|---------|
| IT-CAP-001 | 启动屏幕采集 | startCapture() | Surface 出帧回调正常触发 |
| IT-CAP-002 | 分辨率适配 | 不同分辨率配置 | 采集输出匹配配置 |
| IT-CAP-003 | 编码器初始化 | 初始化 H264_HARDWARE | MediaCodec 创建成功，配置正确 |
| IT-CAP-004 | 编码输出 | 连续 10 帧 | 每帧输出 NAL 单元，无阻塞 |
| IT-CAP-005 | 关键帧请求 | 调用 requestKeyFrame() | 下一输出帧为 IDR |
| IT-CAP-006 | 编码码率控制 | 配置 videoBitrate=2Mbps | 码率稳定在 2Mbps 附近 |
| IT-CAP-007 | H265 编码 | videoCodec=H265_HARDWARE | 正确输出 HEVC 流 |
| IT-CAP-008 | 编码器释放 | release() | MediaCodec 资源释放，无泄漏 |
| IT-CAP-009 | 编码错误恢复 | 模拟编码错误 | 自动重试，编解码不中断 |
| IT-CAP-010 | 帧率控制 | fps=30 | 输出帧率稳定在 30fps |

### 3.3 视频传输与解码

| 用例 ID | 描述 | 步骤 | 预期结果 |
|---------|------|------|---------|
| IT-VID-001 | RTP 打包发送 | 编码帧输入 → RTP 打包 | 符合 RFC 3550，可解析 |
| IT-VID-002 | UDP 发送接收 | 发送端 → 接收端 | 数据到达，延迟 < 20ms |
| IT-VID-003 | RTP 解包 | 收到 RTP 包 | 输出 NAL 单元给解码器 |
| IT-VID-004 | 解码初始化 | 初始化 H264 解码器 | Surface 创建成功 |
| IT-CAP-005 | 解码输出一致性 | 发送分辨率 WxH | 解码输出相同分辨率 |
| IT-VID-006 | 解码延迟 | 收到帧 → Surface 显示 | < 30ms |
| IT-VID-007 | 丢包容忍 | 丢弃 5% 包 | 解码器不崩溃，画面轻微花块 |
| IT-VID-008 | 音视频同步 | 同时传输音频 | 音画同步，差距 < 50ms |

### 3.4 触摸事件传输与注入

| 用例 ID | 描述 | 步骤 | 预期结果 |
|---------|------|------|---------|
| IT-TOUCH-001 | 触摸编码传输 | 模拟 ACTION_DOWN 触摸 | 接收端收到 TouchEvent byte[] |
| IT-TOUCH-002 | 触摸解码还原 | 发送 TouchEvent byte[] | 还原为完整 TouchEvent |
| IT-TOUCH-003 | 触摸注入权限 | 调用 TouchInjectorService | 权限正确申请 |
| IT-TOUCH-004 | 注入点击事件 | 注入 ACTION_DOWN/UP | 屏幕响应点击 |
| IT-TOUCH-005 | 注入滑动事件 | 注入 ACTION_MOVE 序列 | 屏幕响应滑动 |
| IT-TOUCH-006 | 注入多点触控 | 注入双指触摸 | 两指同时响应 |
| IT-TOUCH-007 | 触摸延迟端到端 | 发送 → 接收 → 注入 | < 10ms |
| IT-TOUCH-008 | 坐标系映射 | 发送端(1280x720) → 接收端(1920x1080) | 坐标按比例映射 |
| IT-TOUCH-009 | 注入失败处理 | 无 ROOT/Accessibility | 优雅降级，不崩溃 |

---

## 4. 性能测试用例

### 4.1 视频延迟测试

| 用例 ID | 指标 | 测试方法 | 目标值 | 阈值 |
|---------|------|---------|-------|------|
| PF-VID-001 | Capture-to-Encode 延迟 | 采集时刻到编码完成时间戳差 | < 8ms | < 15ms |
| PF-VID-002 | Encode-to-Packet 延迟 | 编码完成到 RTP 打包完成 | < 2ms | < 5ms |
| PF-VID-003 | Packet-to-Network 延迟 | 打包到 UDP send 返回 | < 1ms | < 3ms |
| PF-VID-004 | Network 传输延迟 | UDP 传输测试（同一局域网） | < 5ms | < 10ms |
| PF-VID-005 | Receive-to-Decode 延迟 | 收到 RTP 包到解码输出 | < 5ms | < 10ms |
| PF-VID-006 | Decode-to-Render 延迟 | 解码输出到 Surface 显示 | < 5ms | < 10ms |
| PF-VID-007 | **端到端总延迟** | 采集帧 → 显示帧 | **< 50ms** | < 70ms |
| PF-VID-008 | 关键帧延迟 | I/P 帧端到端延迟差异 | < 80ms | < 100ms |

### 4.2 触摸延迟测试

| 用例 ID | 指标 | 测试方法 | 目标值 | 阈值 |
|---------|------|---------|-------|------|
| PF-TOUCH-001 | 触摸编码延迟 | ACTION_DOWN → 编码完成 | < 1ms | < 3ms |
| PF-TOUCH-002 | 触摸传输延迟 | 编码完成 → 接收端收到 | < 2ms | < 5ms |
| PF-TOUCH-003 | 触摸注入延迟 | 接收 → 注入完成 | < 2ms | < 5ms |
| PF-TOUCH-004 | **端到端触摸延迟** | 手指按下 → 屏幕响应 | **< 10ms** | < 15ms |
| PF-TOUCH-005 | 连续滑动延迟 | MotionEvent 序列 | < 8ms/帧 | < 12ms/帧 |

### 4.3 带宽占用测试

| 用例 ID | 场景 | 分辨率/码率 | 预期带宽 |
|---------|------|------------|---------|
| PF-BW-001 | 1080p 60fps 高质量 | 1920x1080, 8Mbps | 6~10 Mbps |
| PF-BW-002 | 1080p 30fps 均衡 | 1920x1080, 4Mbps | 3~5 Mbps |
| PF-BW-003 | 720p 60fps 低延迟 | 1280x720, 3Mbps | 2~4 Mbps |
| PF-BW-004 | 480p 30fps 节省 | 854x480, 1Mbps | 0.8~1.5 Mbps |
| PF-BW-005 | H265 vs H264 节省 | 同质量下对比 | H265 比 H264 节省 30~50% |
| PF-BW-006 | 触摸控制带宽 | 正常操作 | < 50 Kbps（几乎无影响） |

### 4.4 CPU / 内存占用测试

| 用例 ID | 指标 | 测试场景 | 目标值 |
|---------|------|---------|-------|
| PF-CPU-001 | 发送端 CPU | 1080p 60fps 编码传输 | < 35% (单核) |
| PF-CPU-002 | 接收端 CPU | 1080p 60fps 解码渲染 | < 25% (单核) |
| PF-CPU-003 | 整体 CPU | 同时编码+传输 | < 50% |
| PF-MEM-001 | 发送端内存 | 稳定传输 5 分钟 | < 80 MB |
| PF-MEM-002 | 接收端内存 | 稳定传输 5 分钟 | < 100 MB |
| PF-MEM-003 | 内存泄漏检测 | 连续运行 30 分钟 | 无持续增长 |
| PF-MEM-004 | AtomicBuffer 内存 | 分配 10MB 环形缓冲 | 无额外堆外泄漏 |

---

## 5. 压力测试用例

### 5.1 长时间运行测试

| 用例 ID | 场景 | 时长 | 验证项 |
|---------|------|------|-------|
| ST-LONG-001 | 连续镜像传输 | 1 小时 | 无崩溃，延迟稳定，内存不增长 |
| ST-LONG-002 | 连续镜像传输 | 4 小时 | 同上，CPU 温度可控 |
| ST-LONG-003 | 反复连接断开 | 50 次 connect/disconnect | 无资源泄漏，状态机正常 |
| ST-LONG-004 | 夜间静默运行 | 8 小时待机 | 电池消耗合理，WiFi P2P 保持 |
| ST-LONG-005 | 内存稳定测试 | 30 分钟每 10 秒采样 | 无 OOM，GC 不频繁 |

### 5.2 网络切换测试

| 用例 ID | 场景 | 操作 | 预期结果 |
|---------|------|------|---------|
| ST-NET-001 | WiFi → P2P 切换 | 断开 WiFi，切到 P2P | 自动重连，镜像不中断超过 3s |
| ST-NET-002 | P2P → WiFi 切换 | P2P 断开，连接同一 WiFi | 可重新建立 P2P 连接 |
| ST-NET-003 | 信号强度变化 | 模拟 P2P 距离变化 | 码率自适应，画面不卡死 |
| ST-NET-004 | 网络干扰 | 模拟丢包 10% | 画面轻微花块，不崩溃 |
| ST-NET-005 | 切换后重连 | 切换网络后 | 自动重连，不需手动干预 |

### 5.3 多设备测试

| 用例 ID | 场景 | 规模 | 预期结果 |
|---------|------|------|---------|
| ST-MULTI-001 | 一发多收 | 1 Sender + 3 Receiver | 每个接收端延迟 < 60ms |
| ST-MULTI-002 | 轮流切换 | 1 Sender + N Receiver 轮流切换 | 切换延迟 < 500ms |
| ST-MULTI-003 | 多对多 | 2 Sender + 2 Receiver 同时 | 各自独立，无串扰 |
| ST-MULTI-004 | 设备发现风暴 | 5 设备同时 discover | 发现列表完整，无遗漏 |

---

## 6. 兼容性测试用例

### 6.1 Android 版本兼容性

| 用例 ID | Android 版本 | API Level | 验证项 |
|---------|-------------|-----------|-------|
| COMP-AND-001 | Android 8.0 | API 26 (minSdk) | 全部核心功能正常 |
| COMP-AND-002 | Android 9.0 | API 28 | WiFi P2P 行为一致 |
| COMP-AND-003 | Android 10 | API 29 | Scoped Storage 无影响 |
| COMP-AND-004 | Android 11 | API 30 | 权限请求变化适配 |
| COMP-AND-005 | Android 12 | API 31 | WiFi P2P 新增限制适配 |
| COMP-AND-006 | Android 13 | API 33 | 通知权限适配 |
| COMP-AND-007 | Android 14 | API 34 | 无破坏性变更 |

### 6.2 屏幕分辨率兼容性

| 用例 ID | 分辨率 | 比例 | 验证项 |
|---------|-------|------|-------|
| COMP-SCR-001 | 1920x1080 (FHD) | 16:9 | 基准测试，延迟最优 |
| COMP-SCR-002 | 1280x720 (HD) | 16:9 | 码率降低，性能更好 |
| COMP-SCR-003 | 2560x1440 (QHD) | 16:9 | 码率增加，延迟可控 |
| COMP-SCR-004 | 3840x2160 (4K) | 16:9 | 硬件编码要求高，测试极限 |
| COMP-SCR-005 | 800x480 | 5:3 | 低端设备兼容 |
| COMP-SCR-006 | 2340x1080 (长屏) | 19.5:9 | 异形屏 / 全面屏适配 |
| COMP-SCR-007 | 1792x828 | 2:1 | iPhone 尺寸兼容 |

### 6.3 设备硬件兼容性

| 用例 ID | 设备类型 | SoC 示例 | 验证项 |
|---------|---------|---------|-------|
| COMP-DEV-001 | 旗舰机 | Snapdragon 865+ | 1080p 60fps 无压力 |
| COMP-DEV-002 | 中端机 | Snapdragon 730 | 720p 60fps 达标 |
| COMP-DEV-003 | 低端机 | MediaTek MT6750 | 480p 30fps 可用 |
| COMP-DEV-004 | 平板 | 多核 ARM | 大屏采集性能测试 |
| COMP-DEV-005 | 不同编码器 | Qualcomm / MediaTek / Exynos | 硬件编码器兼容性 |

---

## 7. 测试环境要求

### 7.1 硬件要求

| 类型 | 要求 | 说明 |
|------|------|------|
| **开发/测试工作站** | CPU ≥ 4 核，RAM ≥ 8GB | 编译和测试运行 |
| **Android 设备（发送端）** | ≥ 3 台，覆盖高/中/低配置 | 包括 API 26 和 API 34 设备 |
| **Android 设备（接收端）** | ≥ 3 台 | 用于多设备测试 |
| **网络环境** | 支持 5GHz WiFi，隔离测试用 AP | 避免公共网络干扰 |
| **USB 调试设备** | 支持adb调试 | 用于日志收集 |

### 7.2 软件要求

| 类型 | 要求 |
|------|------|
| **Android Studio** | 2023.1.1+ |
| **Gradle** | 8.x |
| **Android SDK** | API 26~34（minSdk=26, targetSdk=34）|
| **JUnit** | 4.13+ |
| **Espresso** | 用于 Instrumented Test |
| **Mockito** | 单元测试 Mock |
| **LeakCanary** | 内存泄漏检测 |

### 7.3 测试工具

| 工具 | 用途 |
|------|------|
| `adb shell dumpsys` | SurfaceFlinger 帧时间分析 |
| `adb shell cat /proc/<pid>/sched` | CPU 调度分析 |
| `Perfetto` | 系统级性能 trace |
| `Wireshark` | RTP/UDP 包分析 |
| `Android Profiler` | CPU/Memory/Network 分析 |
| `GameBench` | 可选：游戏性能基准 |

---

## 8. 测试流程

### 8.1 测试准备

```
1. 代码审查
   ├── 静态分析（Lint, SpotBugs）
   └── 覆盖率检查（Jacoco，目标 > 80%）

2. 环境搭建
   ├── 编译 debug APK
   ├── 安装到测试设备
   ├── 确认 WiFi P2P 可用
   └── 确认 USB 调试正常

3. 测试数据准备
   ├── 测试视频源（固定分辨率测试图）
   ├── 测试触摸轨迹文件
   └── 基准延迟数据（已知设备）
```

### 8.2 测试执行

| 阶段 | 执行内容 | 通过标准 |
|------|---------|---------|
| **Phase 1: 单元测试** | `./gradlew test` | 91+ 测试全部通过 |
| **Phase 2: 集成测试** | `./gradlew connectedAndroidTest` | 所有 Instrumented Test 通过 |
| **Phase 3: 性能测试** | 手动执行性能测试用例，收集数据 | 延迟/带宽/CPU 达标（见第4节） |
| **Phase 4: 压力测试** | 运行长时间/压力用例 | 无崩溃，内存稳定 |
| **Phase 5: 兼容性测试** | 在多台设备矩阵上执行 | 全部设备通过 |

### 8.3 测试报告

| 报告项 | 内容 |
|--------|------|
| **执行摘要** | 通过/失败/跳过用例数 |
| **覆盖率报告** | Jacoco 生成的 HTML 覆盖率 |
| **性能数据** | 延迟/带宽/CPU 实测值 vs 目标 |
| **缺陷列表** | 失败用例的 Bug 链接 |
| **兼容性矩阵** | Android 版本 × 设备型号通过情况 |
| **附录** | 原始日志、性能 trace 文件 |

---

## 附录 A：已有测试文件清单

| 文件 | 行数 | 覆盖组件 |
|------|------|---------|
| `AtomicBufferTest.java` | 152 | AtomicBuffer |
| `ErrorCodeTest.java` | 80 | ErrorCode |
| `SenderConfigTest.java` | 91 | SenderConfig |
| `ReceiverConfigTest.java` | 90 | ReceiverConfig |
| `RtpSessionTest.java` | 172 | RtpSession |
| `TouchEncoderTest.java` | 114 | TouchEncoder |
| `TouchDecoderTest.java` | 139 | TouchDecoder |
| `UdpChannelTest.java` | 90 | UdpChannel |
| `BandwidthAdapterTest.java` | 64 | BandwidthAdapter |
| `LatencyTesterTest.java` | 100 | LatencyTester |
| `ReconnectionManagerTest.java` | 26 | ReconnectionManager |
| `ConnectionStateMachineTest.java` | 82 | ConnectionStateMachine |
| `StateEnumsTest.java` | 57 | SenderState, ReceiverState |
| **合计** | **1257 行** | **13 个测试文件，91 个测试用例** |

---

## 附录 B：关键性能指标速查

| 指标 | 目标 | 阈值（警告） |
|------|------|------------|
| 端到端视频延迟 | < 50ms | > 70ms |
| 端到端触摸延迟 | < 10ms | > 15ms |
| 带宽占用（1080p60fps） | 6~10 Mbps | > 12 Mbps |
| 发送端 CPU | < 35% | > 50% |
| 接收端 CPU | < 25% | > 40% |
| 内存占用（发送端） | < 80 MB | > 120 MB |
| 内存占用（接收端） | < 100 MB | > 150 MB |
| 丢包容忍 | < 5% | > 10%（明显卡顿）|