# Production Environment Guide

This guide will help you properly deploy and manage Device Owner applications in production environments.

**English** | [**中文**](production-guide-zh.md)

## Device Preparation

### 1. Bulk Device Preparation

For large numbers of devices in production environments, the following methods are recommended:

#### Method A: Using MDM Solutions

- **Google Workspace** (recommended for enterprises)
- **Microsoft Intune**
- **VMware Workspace ONE**
- **Samsung Knox**

#### Method B: Zero-Touch Enrollment

- Suitable for large-scale deployments
- Devices configure automatically on first boot
- No manual intervention required

#### Method C: ADB Bulk Scripts

```bash
#!/bin/bash
# Bulk setup script example
PACKAGE_NAME="com.yourcompany.kioskapp"

for device in $(adb devices | grep -v "List" | awk '{print $1}'); do
    echo "Setting up device: $device"
    adb -s $device shell dpm set-device-owner "$PACKAGE_NAME/.DeviceAdminReceiver"
done
```

### 2. Device Configuration Checklist

✅ **Device Preparation Checklist**

- [ ] Device has been factory reset
- [ ] Skip setup wizard (don't add Google account)
- [ ] Enable developer options and USB debugging (if using ADB)
- [ ] Application is installed
- [ ] Network connection is working

## Application Deployment

### 1. Application Signing

Ensure your production application uses the correct signature:

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

### 2. Permission Configuration

Ensure AndroidManifest.xml contains the necessary permissions:

```xml
<!-- Required permissions for Kiosk Manager -->
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
<uses-permission android:name="android.permission.allowlist_lockTaskPackages" />

<!-- Device Admin configuration -->
<receiver
    android:name="com.riuhou.kioskmanager.DeviceAdminReceiver"
    android:permission="android.permission.BIND_DEVICE_ADMIN"
    android:exported="true">
    <meta-data
        android:name="android.app.device_admin"
        android:resource="@xml/device_admin_receiver" />
    <intent-filter>
        <action android:name="android.app.action.DEVICE_ADMIN_ENABLED" />
    </intent-filter>
</receiver>
```

## Production Environment Best Practices

### 1. Error Handling and Monitoring

```typescript
import KioskManager from 'react-native-kiosk-manager';

class KioskController {
  async initializeKiosk() {
    try {
      // Check Device Owner status
      const isOwner = await KioskManager.isDeviceOwner();
      if (!isOwner) {
        this.handleDeviceOwnerError();
        return;
      }

      // Start Kiosk mode
      KioskManager.startKiosk();

      // Log success
      this.logEvent('kiosk_started', { success: true });
    } catch (error) {
      this.logError('kiosk_initialization_failed', error);
      this.handleKioskError(error);
    }
  }

  private handleDeviceOwnerError() {
    // Display user-friendly error message
    // May need to guide user to reset Device Owner
    console.error('Device Owner not set. Please contact administrator.');
  }

  private handleKioskError(error: any) {
    // Implement error recovery strategy
    // E.g.: retry, fallback to non-kiosk mode, etc.
  }

  private logEvent(event: string, data: any) {
    // Send to analytics service (e.g., Firebase Analytics)
  }

  private logError(error: string, details: any) {
    // Send to error monitoring service (e.g., Crashlytics)
  }
}
```

### 2. Remote Management

Implement remote management functionality:

```typescript
class RemoteManagement {
  async checkForUpdates() {
    // Check for application updates
    // Can use your own server or Firebase Remote Config
  }

  async receiveRemoteCommands() {
    // Receive remote commands (e.g., exit kiosk mode, restart, etc.)
    // Can use Firebase Cloud Functions or WebSocket
  }

  async reportDeviceStatus() {
    const status = {
      isKioskMode: await this.isInKioskMode(),
      isDeviceOwner: await KioskManager.isDeviceOwner(),
      batteryLevel: await this.getBatteryLevel(),
      lastHeartbeat: new Date().toISOString(),
    };

    // Send to management server
    await this.sendStatusToServer(status);
  }
}
```

### 3. Security Considerations

```typescript
class SecurityManager {
  async validateDeviceIntegrity() {
    // Check if device has been tampered with
    // Verify application signature
    // Check root status
  }

  async setupSecurityPolicies() {
    // Disable unnecessary system features
    // Restrict user operations
    // Set password policies (if needed)
  }

  async handleSecurityBreach() {
    // Security incident response
    // May include: remote lock, data wipe, etc.
  }
}
```

## Troubleshooting

### Common Production Issues

1. **Device Owner permissions lost**

   ```typescript
   // Periodically check Device Owner status
   setInterval(async () => {
     const isOwner = await KioskManager.isDeviceOwner();
     if (!isOwner) {
       // Handle permission loss
       this.handleDeviceOwnerLoss();
     }
   }, 30000); // Check every 30 seconds
   ```

2. **Kiosk mode unexpectedly exits**

   ```typescript
   // Application lifecycle monitoring
   AppState.addEventListener('change', (nextAppState) => {
     if (nextAppState !== 'active') {
       // Try to restart kiosk mode
       setTimeout(() => {
         KioskManager.startKiosk();
       }, 1000);
     }
   });
   ```

3. **Device performance issues**

   ```typescript
   // Memory monitoring and cleanup
   const performanceMonitor = {
     checkMemoryUsage() {
       // Check memory usage
       // Restart app if necessary
     },

     optimizePerformance() {
       // Clear cache
       // Optimize resource usage
     },
   };
   ```

## Monitoring and Analytics

### 1. Key Metrics

- Kiosk mode uptime
- Device Owner status stability
- Application crash rate
- Device health status
- User interaction data

### 2. Logging

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

    // Store locally
    this.storeLocally(logEntry);

    // Send to server
    this.sendToServer(logEntry);
  }
}
```

## Maintenance Schedule

### Regular Tasks

1. **Daily**
   - Check device online status
   - Verify kiosk mode operation
   - Collect performance data

2. **Weekly**
   - Analyze error logs
   - Check device health
   - Update configurations (if needed)

3. **Monthly**
   - Deploy application updates
   - Review security policies
   - Performance optimization

4. **As Needed**
   - Emergency update deployment
   - Security patch deployment
   - Configuration changes

## Support Contact

If you encounter production environment issues, please provide the following information:

- Device model and Android version
- Application version
- Error logs
- Reproduction steps
- Number of devices and deployment scale
