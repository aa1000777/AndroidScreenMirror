# screenshare-sdk ProGuard Rules

# 保持 SDK 公开 API
-keep public class com.screenshare.sdk.** { *; }

# 保持构造函数
-keepclassmembers class * {
    public <init>(...);
}

# AndroidX
-keep class androidx.** { *; }
-dontwarn androidx.**
