#!/bin/bash
# 跨平台环境变量设置脚本 - Bash 版本
# 用于设置 Android 项目编译环境

echo "=== Android 项目环境变量设置脚本"
echo ""

# 检测操作系统
unameOut="$(uname -s)"
case "${unameOut}" in
    Linux*)     machine=Linux;;
    Darwin*)    machine=Mac;;
    CYGWIN*)    machine=Cygwin;;
    MINGW*)     machine=MinGw;;
    MSYS_NT*)   machine=Git;;
    *)          machine="UNKNOWN:${unameOut}"
esac

if [ "$machine" = "Mac" ]; then
    echo "检测到: macOS 系统"
    echo ""
    
    # Mac 系统环境变量设置
    JAVA_HOME="/Users/leiting/Library/Java/JavaVirtualMachines/jbr-17.0.8.1/Contents/Home"
    ANDROID_HOME="/Users/leiting/Library/Android/sdk"
    
    echo "设置 JAVA_HOME: $JAVA_HOME"
    export JAVA_HOME="$JAVA_HOME"
    
    echo "设置 ANDROID_HOME: $ANDROID_HOME"
    export ANDROID_HOME="$ANDROID_HOME"
    
    # 更新 PATH
    export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/tools:$JAVA_HOME/bin:$PATH"
    
    echo ""
    echo "环境变量已设置（当前会话生效）"
    echo ""
    echo "JAVA_HOME: $JAVA_HOME"
    echo "ANDROID_HOME: $ANDROID_HOME"
    echo ""
    echo "验证 Java:"
    java -version
    echo ""
    echo "验证 Android SDK:"
    if [ -e "$ANDROID_HOME/platform-tools/adb" ]; then
        echo "adb 可用"
    else
        echo "警告: 未找到 adb"
    fi
    
elif [ "$machine" = "Linux" ] || [ "$machine" = "Cygwin" ] || [ "$machine" = "MinGw" ]; then
    echo "检测到: $machine 系统"
    echo ""
    echo "请根据你的系统配置 JAVA_HOME 和 ANDROID_HOME"
else
    echo "未知操作系统: $machine"
fi

echo ""
echo "完成!"
