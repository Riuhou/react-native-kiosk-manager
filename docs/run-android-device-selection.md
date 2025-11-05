# React Native run-android 指定设备

## 方法 1: 使用 --deviceId 参数（推荐）

### 步骤 1: 查看连接的设备列表

```bash
adb devices
```

输出示例：
```
List of devices attached
20251029094858	device
adb-20250915200953-bHe9ea._adb-tls-connect._tcp	device
adb-R52W90K71VA-1yt1Er._adb-tls-connect._tcp	device
```

### 步骤 2: 使用设备ID运行

```bash
# 使用设备ID
react-native run-android --deviceId=20251029094858

# 或者使用设备序列号
react-native run-android --deviceId=adb-20250915200953-bHe9ea._adb-tls-connect._tcp
```

### 在 package.json 中添加脚本

您可以修改 `package.json` 中的脚本，添加设备选择功能：

```json
{
  "scripts": {
    "android": "react-native run-android",
    "android:device1": "react-native run-android --deviceId=20251029094858",
    "android:device2": "react-native run-android --deviceId=adb-20250915200953-bHe9ea._adb-tls-connect._tcp"
  }
}
```

然后运行：
```bash
npm run android:device1
```

## 方法 2: 使用环境变量 ANDROID_SERIAL

### 设置环境变量

```bash
# macOS/Linux
export ANDROID_SERIAL=20251029094858
react-native run-android

# Windows (PowerShell)
$env:ANDROID_SERIAL="20251029094858"
react-native run-android

# Windows (CMD)
set ANDROID_SERIAL=20251029094858
react-native run-android
```

### 在 package.json 中使用环境变量

```json
{
  "scripts": {
    "android": "react-native run-android",
    "android:device1": "ANDROID_SERIAL=20251029094858 react-native run-android"
  }
}
```

## 方法 3: 使用 adb -s 指定设备后运行

```bash
# 先指定默认设备
adb -s 20251029094858 shell

# 然后正常运行（React Native 会自动使用 ADB 的默认设备）
react-native run-android
```

## 方法 4: 使用设备型号（部分设备）

某些情况下可以使用设备型号：

```bash
react-native run-android --deviceId=R52W90K71VA
```

## 实用技巧

### 获取设备详细信息

```bash
# 获取设备ID和型号
adb devices -l

# 输出示例：
# 20251029094858           device product:model device:model transport_id:1
```

### 自动选择第一个设备

如果只有一个设备，React Native 会自动选择。如果有多个设备，可以编写脚本：

```bash
#!/bin/bash
# 获取第一个设备ID
DEVICE_ID=$(adb devices | grep -w "device" | awk '{print $1}' | head -n 1)
if [ -z "$DEVICE_ID" ]; then
  echo "No device found"
  exit 1
fi
echo "Using device: $DEVICE_ID"
react-native run-android --deviceId=$DEVICE_ID
```

### 在 package.json 中创建灵活的设备选择脚本

```json
{
  "scripts": {
    "android": "react-native run-android",
    "android:list": "adb devices",
    "android:first": "react-native run-android --deviceId=$(adb devices | grep -w 'device' | awk '{print $1}' | head -n 1)"
  }
}
```

## 常见问题

### Q: 如何查看所有连接的设备？

A: 使用 `adb devices` 命令

### Q: 设备显示为 "unauthorized" 怎么办？

A: 在设备上点击"允许USB调试"授权

### Q: 设备显示为 "offline" 怎么办？

A: 
1. 断开并重新连接设备
2. 运行 `adb kill-server` 然后 `adb start-server`
3. 重新授权USB调试

### Q: 如何同时安装到多个设备？

A: React Native 不支持同时安装到多个设备，需要分别运行：
```bash
react-native run-android --deviceId=device1
react-native run-android --deviceId=device2
```

## 完整示例

假设您的项目在 `example` 目录：

```bash
# 1. 查看设备
cd example
adb devices

# 2. 选择设备运行
react-native run-android --deviceId=20251029094858

# 或者使用 npm script
npm run android -- --deviceId=20251029094858
```

