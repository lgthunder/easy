# Android 项目脚本使用指南

## 📋 概述

本项目提供了一系列自动化脚本，用于简化 Android 应用的编译、测试和安装过程。

## 🎯 脚本列表

### 1. build_and_install.bat ⭐ 推荐
**功能**：完整的编译和安装脚本
- 自动设置环境变量（JAVA_HOME, ANDROID_HOME）
- 修复 ADB 问题
- 检测设备连接状态
- 编译 APK
- 自动安装到已连接的设备
- 如果没有设备连接，仅编译 APK

**使用方法**：
```batch
build_and_install.bat
```

**特性**：
- ✅ 自动处理各种情况
- ✅ 详细的进度提示
- ✅ 智能检测设备连接
- ✅ 编译失败时提供错误信息
- ✅ 安装后自动启动应用

---

### 2. setup_env.bat
**功能**：仅设置环境变量
**使用方法**：
```batch
setup_env.bat
```

**设置的环境变量**：
- `JAVA_HOME=D:\jdk-17.0.2`
- `ANDROID_HOME=E:\Android\android-sdk`

---

### 3. fix_adb.bat
**功能**：修复 ADB 连接问题
**使用方法**：
```batch
fix_adb.bat
```

**解决的问题**：
- ADB 进程占用
- ADB 服务未启动
- 设备检测不到

---

### 4. build_only.bat
**功能**：仅编译 APK（不安装）
**使用方法**：
```batch
build_only.bat
```

---

## 🔧 环境要求

### Windows 系统
- JDK 17+ (建议使用 `D:\jdk-17.0.2`)
- Android SDK (建议使用 `E:\Android\android-sdk`)
- USB 调试驱动（用于连接真实设备）

### macOS/Linux 系统
- JDK 17+
- Android SDK
- 使用 `setup_env.sh` 设置环境变量

## 📱 设备连接指南

### 连接真实 Android 设备

1. **启用开发者选项**：
   - 打开手机设置
   - 关于手机 → 连续点击"版本号"7次
   - 返回设置，找到"开发者选项"

2. **启用 USB 调试**：
   - 进入开发者选项
   - 开启"USB 调试"

3. **连接电脑**：
   - 用 USB 线连接手机和电脑
   - 在手机上授权此电脑调试

4. **验证连接**：
   ```bash
   adb devices
   ```
   应该显示设备 ID

### 启动 Android 模拟器

1. 打开 Android Studio
2. 创建或打开 AVD（Android Virtual Device）
3. 启动模拟器
4. 等待模拟器完全启动
5. 运行 `fix_adb.bat` 确保 ADB 识别到模拟器

---

## 🚀 快速开始

### 方式 1：使用完整脚本（推荐）
```batch
# 双击或在命令行运行
build_and_install.bat
```

### 方式 2：分步执行
```batch
# 1. 设置环境变量
setup_env.bat

# 2. 修复 ADB（如果需要）
fix_adb.bat

# 3. 编译和安装
gradlew.bat installDebug
```

---

## 📊 输出位置

### 编译产物
- **Debug APK**: `app\build\outputs\apk\debug\app-debug.apk`
- **Release APK**: `app\build\outputs\apk\release\app-release.apk`

### 编译日志
- **输出文件**: `full_build_log.txt`
- **错误文件**: `full_build_err.txt`

---

## ❓ 常见问题

### Q1: 编译失败，提示 JAVA_HOME 未设置
**解决**：
- 运行 `setup_env.bat`
- 或手动设置环境变量后重新编译

### Q2: ADB 检测不到设备
**解决**：
1. 运行 `fix_adb.bat`
2. 检查手机上是否授权了 USB 调试
3. 尝试重新插拔 USB 线
4. 安装手机对应的 USB 驱动

### Q3: 安装失败，提示签名不匹配
**解决**：
```bash
# 卸载旧版本
adb uninstall com.mt5.easy

# 重新安装
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

### Q4: 模拟器无法启动
**解决**：
1. 在 Android Studio 中检查 AVD 配置
2. 确保虚拟化技术已启用（Intel HAXM 或 AMD Hyper-V）
3. 分配足够的内存给模拟器

### Q5: 编译很慢
**解决**：
- 使用 Release 版本进行最终测试
- 清理缓存：`gradlew clean`
- 禁用 Gradle Daemon（在脚本中已使用 `--no-daemon`）

---

## 🔄 推荐的开发工作流

### 开发阶段
```batch
# 快速编译和安装
build_and_install.bat
```

### 测试阶段
```batch
# 清理后重新编译
gradlew.bat clean assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

### 发布阶段
```batch
# Release 版本
gradlew.bat assembleRelease
```

---

## 📝 注意事项

1. **环境变量仅在当前会话有效**
   - 每次新开命令行窗口需要重新运行 `setup_env.bat`
   - 或将环境变量添加到系统环境变量中

2. **设备连接状态**
   - 脚本会自动检测设备连接
   - 没有设备时仅编译 APK
   - APK 位置会显示在输出中

3. **ADB 权限**
   - 首次连接新设备需要在手机上授权
   - 授权后会记住该电脑

4. **USB 连接模式**
   - 选择"仅充电"模式也可以
   - 不需要选择文件传输模式

---

## 🎨 脚本输出示例

```
======================================
  Android Build and Install Script
======================================

[1/4] Environment setup:
  JAVA_HOME=D:\jdk-17.0.2
  ANDROID_HOME=E:\Android\android-sdk

[2/4] Fixing ADB...
  ADB server started

[3/4] Checking devices...
  List of devices attached
  emulator-5554	device

[4/4] Building APK...
  (编译过程...)
  
======================================
  BUILD SUCCESSFUL!
======================================

Installing APK to device...
  (安装过程...)
  
======================================
  INSTALLATION SUCCESSFUL!
======================================

  Launching app...
  Done!
```

---

## 📞 获取帮助

如果遇到问题：
1. 查看脚本输出的错误信息
2. 检查环境变量是否正确设置
3. 确认 Android SDK 和 JDK 已正确安装
4. 检查设备连接和授权状态

---

**版本**: 1.0  
**更新日期**: 2026-05-31  
**适用平台**: Windows (macOS/Linux 见 `setup_env.sh`)
