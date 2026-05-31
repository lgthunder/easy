# 环境变量设置脚本

本项目提供了跨平台的环境变量设置脚本，用于 Android 项目编译。

## 脚本文件

### Windows 系统

#### 1. setup_env.bat - Windows 批处理脚本（推荐）
- 简单、直接，无需额外配置
- 在项目根目录双击或在命令行运行

使用方法：
```batch
setup_env.bat
```

#### 2. setup_env.ps1 - PowerShell 脚本
- 更强大的脚本语言
- 如果遇到执行策略问题，可以使用以下方式运行：

使用方法：
```powershell
powershell -ExecutionPolicy Bypass -File setup_env.ps1
```

### macOS/Linux 系统

#### setup_env.sh - Bash 脚本
- 用于 macOS 或 Linux 系统
- 需要先添加执行权限

使用方法：
```bash
chmod +x setup_env.sh
source setup_env.sh
```

## 设置的环境变量

### Windows
- `JAVA_HOME=D:\jdk-17.0.2`
- `ANDROID_HOME=E:\Android\android-sdk`

### macOS
- `JAVA_HOME=/Users/leiting/Library/Java/JavaVirtualMachines/jbr-17.0.8.1/Contents/Home`
- `ANDROID_HOME=/Users/leiting/Library/Android/sdk`

## 注意事项

1. 这些脚本仅在**当前会话**中生效
2. 如需永久生效，请在系统设置中添加环境变量
3. 建议在编译前运行这些脚本以确保环境变量正确设置

## 编译命令示例

### Windows
```batch
setup_env.bat
gradlew.bat assembleDebug
```

### macOS
```bash
source setup_env.sh
./gradlew assembleDebug
```
