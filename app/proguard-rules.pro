# 添加项目特定的 ProGuard 规则
# 参考 https://developer.android.com/studio/build/shrink-code

# 保留所有与MediaCodec相关的类
-keep class android.media.MediaCodec { *; }
-keep class android.media.MediaFormat { *; }
-keep class android.media.MediaProjection { *; }
-keep class android.media.projection.MediaProjectionManager { *; }

# 保留WiFi P2P相关类
-keep class android.net.wifi.p2p.** { *; }

# 保留网络相关类
-keep class java.net.** { *; }

# 保留应用组件
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver

# 保留注解
-keepattributes *Annotation*

# 保留本地方法
-keepclasseswithmembernames class * {
    native <methods>;
}

# 保留枚举
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# 保留Parcelable实现
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}