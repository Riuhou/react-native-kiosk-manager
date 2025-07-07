# react-native-kiosk-manager

A React Native library for managing kiosk mode on Android devices.

[**中文文档**](README-zh.md) | **English**

⚠️ **Android only**: This package is designed for Android devices and will not work on iOS.

## Installation

```sh
npm install react-native-kiosk-manager
```

## Android Permission Configuration

### Automatic Permission Merging

After installing this library, the necessary permissions will be automatically merged into your application without manual configuration.

### Recommended Explicit Declaration (Optional)

For better transparency and compatibility, it's recommended to explicitly declare the following permissions in your `android/app/src/main/AndroidManifest.xml`:

```xml
<!-- Kiosk Manager required permissions -->
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
<uses-permission android:name="android.permission.allowlist_lockTaskPackages" />

<!-- Device Admin configuration -->
<receiver android:name="com.riuhou.kioskmanager.DeviceAdminReceiver"
          android:permission="android.permission.BIND_DEVICE_ADMIN"
          android:exported="true">
    <meta-data android:name="android.app.device_admin"
               android:resource="@xml/device_admin_receiver" />
    <intent-filter>
        <action android:name="android.app.action.DEVICE_ADMIN_ENABLED" />
    </intent-filter>
</receiver>
```

### Required Files to Copy

You also need to copy the following file to your application:

**Create file**: `android/app/src/main/res/xml/device_admin_receiver.xml`

```xml
<!-- res/xml/device_admin_receiver.xml -->
<device-admin xmlns:android="http://schemas.android.com/apk/res/android">
  <uses-policies>
    <force-lock />
    <lock-task />
  </uses-policies>
</device-admin>
```

## Usage

```js
import KioskManager from 'react-native-kiosk-manager';

// Start kiosk mode
KioskManager.startKiosk();

// Stop kiosk mode
KioskManager.stopKiosk();

// Check if device owner
const isOwner = await KioskManager.isDeviceOwner();

// Clear device owner
await KioskManager.clearDeviceOwner();
```

## DeviceAdminReceiver 配置说明 / DeviceAdminReceiver Setup

**注意 / Note:**  
集成本库的 App 需要在自己的包名下创建 `DeviceAdminReceiver.kt`，并参考 example 目录下的实现：  
Apps integrating this library must create their own `DeviceAdminReceiver.kt` under their package, following the example implementation.

1. 在你的项目中创建如下文件（以包名为例：`com.yourcompany.yourapp`）：  
   Create the following file in your project (replace with your actual package name):

   `android/app/src/main/java/com/yourcompany/yourapp/DeviceAdminReceiver.kt`

2. 内容可参考 example：  
   The content can refer to the example:

   ```kotlin
   package com.yourcompany.yourapp
   import android.app.admin.DeviceAdminReceiver

   class DeviceAdminReceiver : DeviceAdminReceiver() {}
   ```

3. AndroidManifest.xml 中的 `<receiver>` 也要使用你自己的包名和类名。  
   The `<receiver>` in AndroidManifest.xml should also use your own package and class name.

**参考实现 / Reference:**  
可直接参考 `example/android/app/src/main/java/com/kioskmanager/example/DeviceAdminReceiver.kt`.

## Device Owner Setup Guide

### Quick Setup (Recommended)

Use our automated setup scripts:

```bash
# Linux/macOS
npm run setup-device-owner

# Windows
npm run setup-device-owner-windows
```

These scripts will guide you through the entire process automatically!

### Method 1: Using ADB (Android Debug Bridge)

#### Prerequisites

- Device must be factory reset or never had a user account added
- USB debugging must be enabled
- ADB must be installed on your computer

#### Steps

1. **Factory reset the device** (if not already done)

   ```bash
   # Optional: Reset via ADB
   adb shell am broadcast -a android.intent.action.MASTER_CLEAR
   ```

2. **Skip the setup wizard** without adding any accounts
   - Do not sign in to Google account
   - Do not add any user accounts

3. **Enable Developer Options**
   - Go to Settings > About phone
   - Tap "Build number" 7 times
   - Go back to Settings > Developer options
   - Enable "USB debugging"

4. **Connect device to computer and verify ADB connection**

   ```bash
   adb devices
   ```

5. **Set your app as Device Owner**

   ```bash
   # Replace com.yourcompany.yourapp with your actual package name
   adb shell dpm set-device-owner com.yourcompany.yourapp/.DeviceAdminReceiver
   ```

6. **Verify Device Owner status**
   ```bash
   adb shell dpm list-owners
   ```

#### Example Commands for This Library

```bash
# For the example app
adb shell dpm set-device-owner com.kioskmanager.example/.DeviceAdminReceiver

# For your production app (replace with your package name)
adb shell dpm set-device-owner com.yourcompany.yourapp/.DeviceAdminReceiver
```

### Method 2: Using MDM (Mobile Device Management)

#### Enterprise Solutions

- **Google Workspace Admin Console**
- **Microsoft Intune**
- **VMware Workspace ONE**
- **Samsung Knox**
- **Custom EMM solutions**

#### General MDM Setup Process

1. **Enroll device in MDM**
   - Factory reset the device
   - During setup, scan QR code or enter enrollment details
   - Device will be provisioned as managed device

2. **Configure Device Owner via MDM**
   - Set your app as the device owner through MDM console
   - Deploy the app via MDM
   - Configure kiosk policies

3. **Benefits of MDM approach**
   - Remote management
   - Bulk deployment
   - Policy enforcement
   - Remote wipe capabilities

### Method 3: Using Android Enterprise (Zero-Touch)

#### For Large Scale Deployments

1. **Register with Android Enterprise**
   - Sign up for Android Enterprise
   - Configure zero-touch enrollment

2. **Device Configuration**
   - Devices are automatically enrolled when first powered on
   - Your app is automatically set as device owner
   - No manual setup required

### Important Notes

⚠️ **Critical Requirements:**

- Device must be in factory reset state
- No user accounts can be added before setting device owner
- Once device owner is set, it cannot be changed without factory reset
- Device owner has extensive system privileges

⚠️ **Security Considerations:**

- Device owner has full device control
- Can install/uninstall apps silently
- Can modify system settings
- Can wipe device remotely

### Troubleshooting

#### Common Issues

1. **"Not allowed to set the device owner"**
   - Solution: Factory reset and try again without adding accounts

2. **"Device owner is already set"**
   - Solution: Clear existing device owner first

   ```bash
   adb shell dpm remove-active-admin com.existing.package/.AdminReceiver
   ```

3. **"User is not empty"**
   - Solution: Remove all user accounts and try again

4. **ADB not recognized**
   - Solution: Install Android SDK Platform Tools
   - Add ADB to system PATH

#### Verification Commands

```bash
# Check current device owner
adb shell dpm list-owners

# Check if device is provisioned
adb shell getprop ro.setupwizard.mode

# List all device admin receivers
adb shell pm list packages -a | grep admin
```

### Development vs Production

#### Development

- Use ADB method for testing
- Easy to reset and reconfigure
- Good for debugging

#### Production

- Use MDM or Zero-Touch enrollment
- Scalable for multiple devices
- Professional device management

### For Production Deployment

📖 **[Complete Production Guide](docs/production-guide.md)** - Detailed production environment deployment and management guide

## API Reference

### Methods

#### `startKiosk()`

Starts kiosk mode on the device.

```js
KioskManager.startKiosk();
```

#### `stopKiosk()`

Stops kiosk mode and returns to normal operation.

```js
KioskManager.stopKiosk();
```

#### `isDeviceOwner(): Promise<boolean>`

Checks if the current app is set as device owner.

```js
const isOwner = await KioskManager.isDeviceOwner();
console.log('Is Device Owner:', isOwner);
```

#### `clearDeviceOwner(): Promise<void>`

Clears the device owner status (requires device owner privileges).

```js
await KioskManager.clearDeviceOwner();
```

## Requirements

- React Native >= 0.60
- Android API Level 21+ (Android 5.0+)
- Device Owner privileges for full functionality

## Limitations

- Android only (iOS not supported)
- Requires Device Owner setup
- Some features may not work on all Android versions/OEMs
- Factory reset required to change Device Owner

## Contributing

See the [contributing guide](CONTRIBUTING.md) to learn how to contribute to the repository and the development workflow.

## License

MIT

---

Made with [create-react-native-library](https://github.com/callstack/react-native-builder-bob)
