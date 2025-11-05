# AndroidManifest.xml Setup Guide

When using this library in other projects, you need to configure the following in AndroidManifest.xml:

## Automatically Merged Content

The library's `AndroidManifest.xml` will be **automatically merged** into your application during build, including:

- ✅ All permission declarations (`<uses-permission>`)
- ✅ `BootReceiver` component declaration
- ✅ `DeviceAdminReceiver` component declaration
- ✅ `FileProvider` component declaration

**Therefore, you do NOT need to manually declare these components in your AndroidManifest.xml.**

## Files You Need to Manually Create

Although component declarations are automatically merged, you need to manually create the following XML resource files:

### 1. device_admin_receiver.xml

**File Path**: `android/app/src/main/res/xml/device_admin_receiver.xml`

**Content**:

```xml
<?xml version="1.0" encoding="utf-8"?>
<device-admin xmlns:android="http://schemas.android.com/apk/res/android">
  <uses-policies>
    <force-lock />
    <lock-task />
  </uses-policies>
</device-admin>
```

**Description**: This file defines device admin policies for enabling lock screen and lock task mode features.

### 2. file_provider_paths.xml (Optional, for APK installation features)

**File Path**: `android/app/src/main/res/xml/file_provider_paths.xml`

**Content**:

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths xmlns:android="http://schemas.android.com/apk/res/android">
    <external-files-path name="apk_updates" path="." />
    <external-path name="external" path="." />
</paths>
```

**Description**: Create this file if you need to use APK installation features (`installApk`, `silentInstallApk`, etc.). Otherwise, you can skip it.

## Verify Configuration

### Check Automatically Merged Components

After building your application, you can check the merged AndroidManifest.xml in the `android/app/build/intermediates/merged_manifests/` directory to confirm the following components are correctly merged:

```xml
<!-- BootReceiver -->
<receiver 
    android:name="com.riuhou.kioskmanager.BootReceiver" 
    android:enabled="true" 
    android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.BOOT_COMPLETED" />
    </intent-filter>
</receiver>

<!-- DeviceAdminReceiver -->
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

<!-- FileProvider -->
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.fileprovider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_provider_paths" />
</provider>
```

### Check Permissions

The merged permissions should include:

```xml
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
<uses-permission android:name="android.permission.allowlist_lockTaskPackages" />
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />
<uses-permission android:name="android.permission.INSTALL_PACKAGES" />
<uses-permission android:name="android.permission.WRITE_SETTINGS" />
<uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />
<uses-permission android:name="android.permission.ACCESS_NOTIFICATION_POLICY" />
```

## FAQ

### Q: Why do I need to manually create XML files?

A: Android's manifest merge only merges component declarations under `<manifest>` and `<application>` tags, but does not merge resource files (files in the res/xml directory). These XML configuration files need to be provided in the application's main resources directory.

### Q: What happens if I don't create device_admin_receiver.xml?

A: The application may crash at runtime because `DeviceAdminReceiver` requires this resource file. The system will report an error when trying to activate the device administrator because it cannot find the file.

### Q: Will FileProvider's authorities automatically use my application ID?

A: Yes. The library's AndroidManifest.xml uses the `${applicationId}` placeholder, which will be automatically replaced with your application package name during build. For example, if your package name is `com.example.app`, the FileProvider's authorities will automatically become `com.example.app.fileprovider`.

### Q: Do I need to modify component declarations in the library?

A: No. The library's component declarations will be automatically merged, and the package name and class names are correct (`com.riuhou.kioskmanager`).

## Complete Example

Assuming your project structure is:

```
your-app/
├── android/
│   └── app/
│       └── src/
│           └── main/
│               ├── AndroidManifest.xml  (no need to manually add component declarations)
│               └── res/
│                   └── xml/
│                       ├── device_admin_receiver.xml  (must create)
│                       └── file_provider_paths.xml    (optional, for APK installation)
```

Your `AndroidManifest.xml` only needs to include basic application configuration, the library's components will be automatically merged:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
  <application>
    <!-- Your application configuration -->
    <activity android:name=".MainActivity" ... />
    <!-- Library components will be automatically merged here -->
  </application>
</manifest>
```

## Summary

✅ **Automatically Merged** (no manual configuration needed):
- Permission declarations
- BootReceiver
- DeviceAdminReceiver
- FileProvider

❌ **Need to Manually Create**:
- `res/xml/device_admin_receiver.xml` (required)
- `res/xml/file_provider_paths.xml` (optional, for APK installation)

