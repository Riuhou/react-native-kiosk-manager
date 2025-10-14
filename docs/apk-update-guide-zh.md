# APK 自动更新功能使用指南

## 概述

React Native Kiosk Manager 现在支持 APK 自动更新功能，允许您从 URL 下载 APK 文件并自动安装。

## 功能特性

- **APK 下载**: 从指定 URL 下载 APK 文件
- **自动安装**: 下载完成后自动启动安装程序
- **权限管理**: 自动检查和请求安装权限
- **进度跟踪**: 支持下载进度监控和实时回调
- **错误处理**: 完善的错误处理和用户提示
- **事件监听**: 支持下载进度事件监听

## 新增 API 方法

### 1. downloadApk(url: string)

下载 APK 文件到本地存储。

**参数:**
- `url: string` - APK 文件的下载链接

**返回值:**
```typescript
Promise<{
  filePath: string;    // 下载文件的完整路径
  fileName: string;    // 文件名
  fileSize: number;    // 文件大小（字节）
}>
```

**使用示例:**
```typescript
import KioskManager from 'react-native-kiosk-manager';

try {
  const result = await KioskManager.downloadApk('https://example.com/app.apk');
  console.log('下载完成:', result.fileName);
  console.log('文件大小:', result.fileSize);
} catch (error) {
  console.error('下载失败:', error);
}
```

### 2. installApk(filePath: string)

安装本地 APK 文件。

**参数:**
- `filePath: string` - APK 文件的本地路径

**返回值:**
```typescript
Promise<boolean>
```

**使用示例:**
```typescript
try {
  await KioskManager.installApk('/path/to/app.apk');
  console.log('安装程序已启动');
} catch (error) {
  console.error('安装失败:', error);
}
```

### 3. downloadAndInstallApk(url: string)

一键下载并安装 APK 文件。

**参数:**
- `url: string` - APK 文件的下载链接

**返回值:**
```typescript
Promise<boolean>
```

**使用示例:**
```typescript
try {
  await KioskManager.downloadAndInstallApk('https://example.com/app.apk');
  console.log('下载并安装已启动');
} catch (error) {
  console.error('操作失败:', error);
}
```

### 4. checkInstallPermission()

检查是否有安装 APK 的权限。

**返回值:**
```typescript
Promise<boolean>
```

**使用示例:**
```typescript
const hasPermission = await KioskManager.checkInstallPermission();
if (hasPermission) {
  console.log('有安装权限');
} else {
  console.log('需要请求安装权限');
}
```

### 5. requestInstallPermission()

请求安装 APK 的权限。

**返回值:**
```typescript
Promise<boolean>
```

**使用示例:**
```typescript
try {
  await KioskManager.requestInstallPermission();
  console.log('权限请求已发送');
} catch (error) {
  console.error('权限请求失败:', error);
}
```

## 下载进度监听

### 使用 DownloadProgressListener

```typescript
import React, { useState, useEffect } from 'react';
import { View, Text, TouchableOpacity, TextInput, Alert } from 'react-native';
import KioskManager, { 
  DownloadProgressListener, 
  type DownloadProgress 
} from 'react-native-kiosk-manager';

const ApkUpdateWithProgress = () => {
  const [progress, setProgress] = useState<DownloadProgress | null>(null);
  const [isDownloading, setIsDownloading] = useState(false);
  const [listener, setListener] = useState<DownloadProgressListener | null>(null);

  const handleDownload = async () => {
    setIsDownloading(true);
    
    // 创建进度监听器
    const progressListener = new DownloadProgressListener();
    setListener(progressListener);
    
    // 开始监听进度
    progressListener.startListening(
      (progress: DownloadProgress) => {
        setProgress(progress);
        console.log(`下载进度: ${progress.progress}%`);
      },
      (error: any) => {
        console.error('下载错误:', error);
      }
    );

    try {
      const result = await KioskManager.downloadApk('https://example.com/app.apk');
      console.log('下载完成:', result.fileName);
    } catch (error) {
      console.error('下载失败:', error);
    } finally {
      setIsDownloading(false);
      progressListener.stopListening();
      setListener(null);
    }
  };

  // 清理监听器
  useEffect(() => {
    return () => {
      if (listener) {
        listener.stopListening();
      }
    };
  }, [listener]);

  return (
    <View>
      <TouchableOpacity onPress={handleDownload} disabled={isDownloading}>
        <Text>{isDownloading ? '下载中...' : '下载 APK'}</Text>
      </TouchableOpacity>
      
      {progress && (
        <View>
          <Text>进度: {progress.progress}%</Text>
          <Text>已下载: {(progress.bytesRead / 1024 / 1024).toFixed(2)} MB</Text>
          <Text>总大小: {(progress.totalBytes / 1024 / 1024).toFixed(2)} MB</Text>
        </View>
      )}
    </View>
  );
};
```

### 进度数据结构

```typescript
interface DownloadProgress {
  progress: number;        // 进度百分比 (0-100)
  bytesRead: number;      // 已下载字节数
  totalBytes: number;     // 总字节数
  percentage: number;     // 进度百分比 (0.0-100.0)
}
```

## 完整使用示例

```typescript
import React, { useState } from 'react';
import { View, Text, TouchableOpacity, TextInput, Alert } from 'react-native';
import KioskManager from 'react-native-kiosk-manager';

const ApkUpdateExample = () => {
  const [apkUrl, setApkUrl] = useState('');
  const [isInstalling, setIsInstalling] = useState(false);

  const handleDownloadAndInstall = async () => {
    if (!apkUrl.trim()) {
      Alert.alert('错误', '请输入有效的 APK URL');
      return;
    }

    setIsInstalling(true);
    try {
      // 检查安装权限
      const hasPermission = await KioskManager.checkInstallPermission();
      if (!hasPermission) {
        await KioskManager.requestInstallPermission();
        Alert.alert('提示', '请允许安装未知来源的应用');
        return;
      }

      // 下载并安装
      await KioskManager.downloadAndInstallApk(apkUrl);
      Alert.alert('成功', 'APK 下载并安装已启动');
    } catch (error) {
      Alert.alert('错误', `操作失败: ${error}`);
    } finally {
      setIsInstalling(false);
    }
  };

  return (
    <View style={{ padding: 20 }}>
      <TextInput
        style={{ borderWidth: 1, padding: 10, marginBottom: 20 }}
        value={apkUrl}
        onChangeText={setApkUrl}
        placeholder="输入 APK 下载链接"
      />
      
      <TouchableOpacity
        style={{ backgroundColor: '#007bff', padding: 15, borderRadius: 5 }}
        onPress={handleDownloadAndInstall}
        disabled={isInstalling}
      >
        <Text style={{ color: 'white', textAlign: 'center' }}>
          {isInstalling ? '处理中...' : '下载并安装 APK'}
        </Text>
      </TouchableOpacity>
    </View>
  );
};

export default ApkUpdateExample;
```

## 权限配置

### Android 权限

确保在 `android/app/src/main/AndroidManifest.xml` 中添加以下权限：

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />
<uses-permission android:name="android.permission.INSTALL_PACKAGES" />
```

### FileProvider 配置

库已自动配置 FileProvider 来支持 APK 安装。确保您的应用 ID 与 FileProvider 的 authorities 匹配。

## 注意事项

1. **网络权限**: 确保应用有网络访问权限
2. **存储权限**: 确保应用有写入外部存储的权限
3. **安装权限**: Android 8.0+ 需要用户手动允许安装未知来源的应用
4. **文件路径**: 下载的文件会保存在应用的私有目录中
5. **错误处理**: 建议始终使用 try-catch 来处理可能的错误

## 错误代码

- `E_DOWNLOAD_FAILED`: 下载失败
- `E_INSTALL_FAILED`: 安装失败
- `E_PERMISSION_REQUIRED`: 需要安装权限
- `E_NO_ACTIVITY`: 没有当前活动

## 最佳实践

1. **权限检查**: 在下载前先检查安装权限
2. **用户提示**: 在需要权限时给用户明确的提示
3. **错误处理**: 提供友好的错误信息
4. **进度反馈**: 对于大文件，考虑显示下载进度
5. **安全性**: 只从可信的来源下载 APK 文件
