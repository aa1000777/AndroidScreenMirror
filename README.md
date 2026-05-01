# Android屏幕镜像项目

## 项目概述
这是一个完整的Android工程，实现手机投屏到Pad功能。支持两种连接方式：WiFi P2P（点对点直连）和局域网TCP/UDP。

## 功能特性
1. **屏幕采集**：使用MediaProjection API捕获屏幕内容
2. **视频编码**：使用MediaCodec进行H.264硬编码
3. **网络传输**：支持TCP和UDP协议传输视频流
4. **解码渲染**：接收端解码并显示视频流
5. **触摸反控**：Pad端触摸控制手机端操作
6. **双连接模式**：WiFi P2P直连和局域网连接

## 项目结构
```
AndroidScreenMirror/
├── app/
│   ├── src/main/
│   │   ├── java/com/example/screenmirror/
│   │   │   ├── MainActivity.java          # 主界面，选择设备角色
│   │   │   ├── SenderActivity.java        # 发送端Activity
│   │   │   ├── ReceiverActivity.java      # 接收端Activity
│   │   │   ├── ScreenCaptureService.java  # 屏幕录制服务
│   │   │   ├── WifiP2pService.java        # WiFi P2P服务
│   │   │   └── NetworkService.java        # 网络传输服务
│   │   ├── res/
│   │   │   ├── layout/                    # 布局文件
│   │   │   ├── values/                    # 资源文件
│   │   │   └── drawable/                  # 图像资源
│   │   └── AndroidManifest.xml            # 应用清单
│   └── build.gradle                       # 模块构建配置
├── build.gradle                           # 项目构建配置
├── settings.gradle                        # 项目设置
└── README.md                              # 项目说明
```

## 编译和运行说明

### 环境要求
- Android Studio 2022.3.1 或更高版本
- Android SDK 33 或更高版本
- Java 8 或更高版本
- 两台Android设备（一台作为发送端，一台作为接收端）
  - 发送端：Android 5.0 (API 21) 或更高版本
  - 接收端：Android 5.0 (API 21) 或更高版本

### 编译步骤
1. **导入项目**
   - 打开Android Studio
   - 选择"Open"或"Import Project"
   - 导航到`AndroidScreenMirror`目录并打开

2. **配置SDK**
   - 确保已安装Android SDK 33
   - 确保已安装Build Tools 33.0.0或更高版本

3. **构建项目**
   - 点击菜单栏的"Build" → "Make Project"
   - 或使用快捷键Ctrl+F9 (Windows/Linux) / Cmd+F9 (Mac)

4. **安装到设备**
   - 连接Android设备并启用USB调试
   - 点击"Run" → "Run 'app'"
   - 选择目标设备并点击"OK"

### 运行步骤
1. **发送端设置（手机）**
   - 安装应用后打开
   - 点击"作为发送端（手机）"
   - 选择连接方式：
     - WiFi P2P：点对点直连，无需路由器
     - TCP：稳定的局域网连接
     - UDP：低延迟的局域网连接
   - 点击"开始投屏"
   - 授予屏幕录制权限

2. **接收端设置（Pad）**
   - 在另一台设备上安装应用
   - 点击"作为接收端（Pad）"
   - 点击"搜索设备"发现附近设备
   - 从列表中选择发送端设备
   - 点击"开始接收"

3. **触摸反控**
   - 在接收端Pad上触摸屏幕
   - 触摸事件将发送到发送端手机
   - 手机将响应触摸操作

### 调试说明
1. **日志查看**
   - 使用Android Studio的Logcat查看日志
   - 过滤标签：SenderActivity、ReceiverActivity

2. **常见问题**
   - **权限问题**：确保授予所有请求的权限
   - **连接失败**：确保设备在同一网络或WiFi P2P范围内
   - **视频卡顿**：调整编码比特率或降低帧率

3. **测试建议**
   - 先在同一设备上测试基础功能
   - 再使用两台设备进行实际投屏测试
   - 测试不同网络环境下的表现

## 技术实现细节

### 屏幕采集模块
- 使用`MediaProjectionManager`请求屏幕录制权限
- 创建`VirtualDisplay`捕获屏幕内容
- 通过`Surface`将视频帧送入编码器

### 视频编码模块
- 使用`MediaCodec`进行H.264硬编码
- 配置编码参数：分辨率、比特率、帧率
- 处理编码器回调，获取编码后的NAL单元

### 网络传输模块
- **TCP模式**：可靠的面向连接传输，适合稳定网络
- **UDP模式**：无连接的快速传输，适合低延迟场景
- **WiFi P2P模式**：设备直连，无需中间网络

### 解码渲染模块
- 使用`MediaCodec`进行H.264硬解码
- 通过`SurfaceView`显示解码后的视频帧
- 处理解码器状态和格式变化

### 触摸反控模块
- 在接收端捕获触摸事件
- 将触摸坐标归一化后发送到发送端
- 发送端通过`Instrumentation`或无障碍服务模拟触摸

## 注意事项
1. **权限要求**：需要屏幕录制、网络、位置等敏感权限
2. **设备兼容性**：某些设备可能对MediaProjection有特殊限制
3. **性能考虑**：高分辨率屏幕录制可能消耗大量CPU和网络资源
4. **安全考虑**：确保只在可信网络环境中使用

## 代码注释
- 所有Java代码都有详细的中文注释
- 注释内容包括：功能说明、参数解释、实现原理
- 关键代码段有额外的实现细节说明

## 许可证
本项目仅供学习和研究使用，请遵守相关法律法规。

## 更新日志
- 2026-03-08：初始版本发布
- 包含完整的屏幕镜像功能
- 支持WiFi P2P和局域网连接
- 实现触摸事件反控