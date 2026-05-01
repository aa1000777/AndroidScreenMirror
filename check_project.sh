#!/bin/bash

# 项目结构检查脚本

echo "检查Android屏幕镜像项目结构..."

# 检查必要的目录和文件
required_files=(
    "app/src/main/java/com/example/screenmirror/MainActivity.java"
    "app/src/main/java/com/example/screenmirror/SenderActivity.java"
    "app/src/main/java/com/example/screenmirror/ReceiverActivity.java"
    "app/src/main/java/com/example/screenmirror/ScreenCaptureService.java"
    "app/src/main/java/com/example/screenmirror/WifiP2pService.java"
    "app/src/main/java/com/example/screenmirror/NetworkService.java"
    "app/src/main/res/layout/activity_main.xml"
    "app/src/main/res/layout/activity_sender.xml"
    "app/src/main/res/layout/activity_receiver.xml"
    "app/src/main/res/values/strings.xml"
    "app/src/main/res/values/styles.xml"
    "app/src/main/res/values/colors.xml"
    "app/src/main/AndroidManifest.xml"
    "app/build.gradle"
    "build.gradle"
    "settings.gradle"
)

missing_files=0
for file in "${required_files[@]}"; do
    if [ -f "$file" ]; then
        echo "✅ $file"
    else
        echo "❌ $file (缺失)"
        missing_files=$((missing_files + 1))
    fi
done

# 检查Java文件中的中文注释
echo -e "\n检查Java文件中的中文注释..."
java_files=($(find app/src/main/java -name "*.java"))
for java_file in "${java_files[@]}"; do
    chinese_count=$(grep -c "[\\u4e00-\\u9fff]" "$java_file" 2>/dev/null || echo "0")
    if [ "$chinese_count" -gt 10 ]; then
        echo "✅ $java_file (包含中文注释)"
    else
        echo "⚠️  $java_file (中文注释可能不足)"
    fi
done

# 检查AndroidManifest权限
echo -e "\n检查AndroidManifest权限..."
if grep -q "RECORD_AUDIO" app/src/main/AndroidManifest.xml; then
    echo "✅ 包含录音权限"
else
    echo "❌ 缺少录音权限"
fi

if grep -q "MediaProjection" app/src/main/AndroidManifest.xml; then
    echo "✅ 包含屏幕录制权限"
else
    echo "❌ 缺少屏幕录制权限"
fi

if grep -q "WIFI_P2P" app/src/main/AndroidManifest.xml; then
    echo "✅ 包含WiFi P2P权限"
else
    echo "⚠️  缺少WiFi P2P权限"
fi

# 总结
echo -e "\n=== 检查完成 ==="
if [ $missing_files -eq 0 ]; then
    echo "✅ 项目结构完整"
else
    echo "❌ 缺失 $missing_files 个必要文件"
fi

echo -e "\n编译说明："
echo "1. 使用Android Studio打开项目"
echo "2. 连接Android设备或启动模拟器"
echo "3. 点击运行按钮编译并安装应用"
echo "4. 按照README.md中的说明测试功能"