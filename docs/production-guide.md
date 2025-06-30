# 生产环境使用指南

本指南将帮助您在生产环境中正确部署和管理Device Owner应用。

## 设备准备

### 1. 批量设备准备

对于生产环境中的大量设备，推荐以下方法：

#### 方法A：使用MDM解决方案

- **Google Workspace** (推荐用于企业)
- **Microsoft Intune**
- **VMware Workspace ONE**
- **Samsung Knox**

#### 方法B：零接触部署 (Zero-Touch Enrollment)

- 适用于大规模部署
- 设备开机自动配置
- 无需手动干预

#### 方法C：ADB批量脚本

```bash
#!/bin/bash
# 批量设置脚本示例
PACKAGE_NAME="com.yourcompany.kioskapp"

for device in $(adb devices | grep -v "List" | awk '{print $1}'); do
    echo "Setting up device: $device"
    adb -s $device shell dpm set-device-owner "$PACKAGE_NAME/.DeviceAdminReceiver"
done
```

### 2. 设备配置清单

✅ **设备准备检查**

- [ ] 设备已恢复出厂设置
- [ ] 跳过设置向导（不添加Google账户）
- [ ] 启用开发者选项和USB调试（如使用ADB）
- [ ] 应用已安装
- [ ] 网络连接正常

## 应用部署

### 1. 应用签名

确保您的生产应用使用正确的签名：

```gradle
// android/app/build.gradle
android {
    signingConfigs {
        release {
            storeFile file(MYAPP_RELEASE_STORE_FILE)
            storePassword MYAPP_RELEASE_STORE_PASSWORD
            keyAlias MYAPP_RELEASE_KEY_ALIAS
            keyPassword MYAPP_RELEASE_KEY_PASSWORD
        }
    }
    buildTypes {
        release {
            signingConfig signingConfigs.release
            minifyEnabled true
            proguardFiles getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro"
        }
    }
}
```

### 2. 权限配置

确保AndroidManifest.xml包含必要权限：

```xml
<uses-permission android:name="android.permission.BIND_DEVICE_ADMIN" />
<uses-permission android:name="android.permission.WRITE_SECURE_SETTINGS" />

<receiver
    android:name=".DeviceAdminReceiver"
    android:permission="android.permission.BIND_DEVICE_ADMIN">
    <meta-data
        android:name="android.app.device_admin"
        android:resource="@xml/device_admin_receiver" />
    <intent-filter>
        <action android:name="android.app.action.DEVICE_ADMIN_ENABLED" />
    </intent-filter>
</receiver>
```

## 生产环境最佳实践

### 1. 错误处理和监控

```typescript
import KioskManager from 'react-native-kiosk-manager';

class KioskController {
  async initializeKiosk() {
    try {
      // 检查Device Owner状态
      const isOwner = await KioskManager.isDeviceOwner();
      if (!isOwner) {
        this.handleDeviceOwnerError();
        return;
      }

      // 设置锁定任务包
      await KioskManager.setupLockTaskPackage();

      // 启动Kiosk模式
      KioskManager.startKiosk();

      // 记录成功日志
      this.logEvent('kiosk_started', { success: true });
    } catch (error) {
      this.logError('kiosk_initialization_failed', error);
      this.handleKioskError(error);
    }
  }

  private handleDeviceOwnerError() {
    // 显示用户友好的错误消息
    // 可能需要引导用户重新设置Device Owner
    console.error('Device Owner not set. Please contact administrator.');
  }

  private handleKioskError(error: any) {
    // 实现错误恢复策略
    // 例如：重试、回退到非Kiosk模式等
  }

  private logEvent(event: string, data: any) {
    // 发送到分析服务（如Firebase Analytics）
  }

  private logError(error: string, details: any) {
    // 发送到错误监控服务（如Crashlytics）
  }
}
```

### 2. 远程管理

实现远程管理功能：

```typescript
class RemoteManagement {
  async checkForUpdates() {
    // 检查应用更新
    // 可以通过自己的服务器或Firebase Remote Config
  }

  async receiveRemoteCommands() {
    // 接收远程命令（如退出Kiosk模式、重启等）
    // 可以使用Firebase Cloud Functions或WebSocket
  }

  async reportDeviceStatus() {
    const status = {
      isKioskMode: await this.isInKioskMode(),
      isDeviceOwner: await KioskManager.isDeviceOwner(),
      batteryLevel: await this.getBatteryLevel(),
      lastHeartbeat: new Date().toISOString(),
    };

    // 发送到管理服务器
    await this.sendStatusToServer(status);
  }
}
```

### 3. 安全考虑

```typescript
class SecurityManager {
  async validateDeviceIntegrity() {
    // 检查设备是否被篡改
    // 验证应用签名
    // 检查root状态
  }

  async setupSecurityPolicies() {
    // 禁用不必要的系统功能
    // 限制用户操作
    // 设置密码策略（如果需要）
  }

  async handleSecurityBreach() {
    // 安全事件响应
    // 可能包括：远程锁定、数据清除等
  }
}
```

## 故障排除

### 常见生产问题

1. **Device Owner权限丢失**

   ```typescript
   // 定期检查Device Owner状态
   setInterval(async () => {
     const isOwner = await KioskManager.isDeviceOwner();
     if (!isOwner) {
       // 处理权限丢失情况
       this.handleDeviceOwnerLoss();
     }
   }, 30000); // 每30秒检查一次
   ```

2. **Kiosk模式意外退出**

   ```typescript
   // 应用生命周期监控
   AppState.addEventListener('change', (nextAppState) => {
     if (nextAppState !== 'active') {
       // 尝试重新启动Kiosk模式
       setTimeout(() => {
         KioskManager.startKiosk();
       }, 1000);
     }
   });
   ```

3. **设备性能问题**

   ```typescript
   // 内存监控和清理
   const performanceMonitor = {
     checkMemoryUsage() {
       // 检查内存使用情况
       // 必要时重启应用
     },

     optimizePerformance() {
       // 清理缓存
       // 优化资源使用
     },
   };
   ```

## 监控和分析

### 1. 关键指标

- Kiosk模式正常运行时间
- Device Owner状态稳定性
- 应用崩溃率
- 设备健康状况
- 用户交互数据

### 2. 日志记录

```typescript
class Logger {
  static logKioskEvent(event: string, data?: any) {
    const logEntry = {
      timestamp: new Date().toISOString(),
      event,
      data,
      deviceId: this.getDeviceId(),
      appVersion: this.getAppVersion(),
    };

    // 本地存储
    this.storeLocally(logEntry);

    // 发送到服务器
    this.sendToServer(logEntry);
  }
}
```

## 维护计划

### 定期任务

1. **每日**
   - 检查设备在线状态
   - 验证Kiosk模式运行状态
   - 收集性能数据

2. **每周**
   - 分析错误日志
   - 检查设备健康状况
   - 更新配置（如需要）

3. **每月**
   - 应用更新部署
   - 安全策略审查
   - 性能优化

4. **按需**
   - 紧急更新推送
   - 安全补丁部署
   - 配置变更

## 联系支持

如果遇到生产环境问题，请提供以下信息：

- 设备型号和Android版本
- 应用版本
- 错误日志
- 复现步骤
- 设备数量和部署规模
