# AndroidManifest.xml 配置说明

在其他项目中使用本库时，需要在 AndroidManifest.xml 中配置以下内容：

## 自动合并的内容

本库的 `AndroidManifest.xml` 会在构建时**自动合并**到您的应用中，包括：

- ✅ 所有权限声明（`<uses-permission>`）
- ✅ `BootReceiver` 组件声明
- ✅ `DeviceAdminReceiver` 组件声明
- ✅ `FileProvider` 组件声明

**因此，您不需要手动在 AndroidManifest.xml 中声明这些组件。**

## 需要手动创建的文件

虽然组件声明会自动合并，但您需要手动创建以下 XML 资源文件：

### 1. device_admin_receiver.xml

**文件路径**: `android/app/src/main/res/xml/device_admin_receiver.xml`

**内容**:

```xml
<?xml version="1.0" encoding="utf-8"?>
<device-admin xmlns:android="http://schemas.android.com/apk/res/android">
  <uses-policies>
    <force-lock />
    <lock-task />
  </uses-policies>
</device-admin>
```

**说明**: 此文件定义了设备管理员策略，用于启用锁屏和锁定任务模式功能。

### 2. file_provider_paths.xml（可选，用于APK安装功能）

**文件路径**: `android/app/src/main/res/xml/file_provider_paths.xml`

**内容**:

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths xmlns:android="http://schemas.android.com/apk/res/android">
    <external-files-path name="apk_updates" path="." />
    <external-path name="external" path="." />
</paths>
```

**说明**: 如果您需要使用APK安装功能（`installApk`、`silentInstallApk` 等），需要创建此文件。否则可以跳过。

## 验证配置

### 检查自动合并的组件

构建应用后，可以在 `android/app/build/intermediates/merged_manifests/` 目录下查看合并后的 AndroidManifest.xml，确认以下组件已正确合并：

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

### 检查权限

合并后的权限应包括：

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

## 常见问题

### Q: 为什么需要手动创建 XML 文件？

A: Android 的 manifest 合并只会合并 `<manifest>` 和 `<application>` 标签下的组件声明，但不会合并资源文件（res/xml 目录下的文件）。这些 XML 配置文件需要在应用的主资源目录中提供。

### Q: 如果我不创建 device_admin_receiver.xml 会怎样？

A: 应用在运行时可能会崩溃，因为 `DeviceAdminReceiver` 需要引用这个资源文件。系统会在尝试激活设备管理员时找不到该文件而报错。

### Q: FileProvider 的 authorities 会自动使用我的应用ID吗？

A: 是的。库的 AndroidManifest.xml 中使用了 `${applicationId}` 占位符，构建时会自动替换为您的应用包名。例如，如果您的包名是 `com.example.app`，FileProvider 的 authorities 将自动变为 `com.example.app.fileprovider`。

### Q: 我需要修改库中的组件声明吗？

A: 不需要。库的组件声明会自动合并，包名和类名都是正确的（`com.riuhou.kioskmanager`）。

## 完整示例

假设您的项目结构如下：

```
your-app/
├── android/
│   └── app/
│       └── src/
│           └── main/
│               ├── AndroidManifest.xml  (不需要手动添加组件声明)
│               └── res/
│                   └── xml/
│                       ├── device_admin_receiver.xml  (必须创建)
│                       └── file_provider_paths.xml    (可选，用于APK安装)
```

您的 `AndroidManifest.xml` 只需要包含基本的应用配置，库的组件会自动合并：

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
  <application>
    <!-- 您的应用配置 -->
    <activity android:name=".MainActivity" ... />
    <!-- 库的组件会自动合并到这里 -->
  </application>
</manifest>
```

## 总结

✅ **自动合并**（无需手动配置）:
- 权限声明
- BootReceiver
- DeviceAdminReceiver
- FileProvider

❌ **需要手动创建**:
- `res/xml/device_admin_receiver.xml`（必须）
- `res/xml/file_provider_paths.xml`（可选，用于APK安装）

