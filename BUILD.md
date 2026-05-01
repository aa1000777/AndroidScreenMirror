# 构建说明

## 快速开始

### 使用Android Studio（推荐）
1. 安装Android Studio（2022.3.1或更高版本）
2. 打开Android Studio
3. 选择"Open" → 选择本项目目录
4. 等待Gradle同步完成
5. 连接Android设备或启动模拟器
6. 点击"Run"按钮（绿色三角形）

### 使用命令行
```bash
# 确保已安装Android SDK和Gradle
./gradlew assembleDebug
# 安装到设备
./gradlew installDebug
```

## 依赖项
- Android SDK 33+
- Java 8+
- Gradle 7.5+

## 配置说明

### 修改应用ID
在`app/build.gradle`中修改：
```gradle
defaultConfig {
    applicationId "com.example.screenmirror" # 修改为您的应用ID
    // ...
}
```

### 调整编码参数
在`Constants.java`中修改视频编码参数：
```java
public static final int VIDEO_BIT_RATE = 5000000; // 调整比特率
public static final int VIDEO_FRAME_RATE = 30;    // 调整帧率
```

### 修改网络端口
在`Constants.java`中修改：
```java
public static final int DEFAULT_TCP_PORT = 8888; // TCP端口
public static final int DEFAULT_UDP_PORT = 8889; // UDP端口
```

## 常见构建问题

### 1. Gradle同步失败
- 检查网络连接
- 检查Gradle版本兼容性
- 尝试清除缓存：`./gradlew clean`

### 2. 权限错误
- 确保在AndroidManifest.xml中声明了所有必要权限
- 对于Android 6.0+，需要在运行时请求权限

### 3. 编码器初始化失败
- 检查设备是否支持H.264硬编码
- 降低编码分辨率或比特率

### 4. WiFi P2P不可用
- 检查设备是否支持WiFi P2P
- 确保WiFi和位置服务已开启

## 调试建议

### 启用详细日志
在`Constants.java`中启用调试日志：
```java
public static final boolean DEBUG_MODE = true;
```

### 查看Logcat输出
```bash
adb logcat -s ScreenMirrorSender:V ScreenMirrorReceiver:V
```

### 性能分析
- 使用Android Studio的Profiler工具
- 监控CPU、内存和网络使用情况

## 发布构建
```bash
# 生成发布APK
./gradlew assembleRelease

# 生成签名APK
# 首先配置签名信息
```

## 支持的Android版本
- 最低：Android 5.0 (API 21)
- 目标：Android 13 (API 33)
- 编译：Android 13 (API 33)

## 设备要求
- 发送端：支持MediaProjection API的设备
- 接收端：支持MediaCodec H.264解码的设备
- 网络：WiFi或移动网络连接