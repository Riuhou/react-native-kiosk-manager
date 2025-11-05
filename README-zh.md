# react-native-kiosk-manager

用于在Android设备上管理Kiosk模式的React Native库。

**中文** | [**English**](README.md)

⚠️ **仅支持Android**：本库专为Android设备设计，不支持iOS。

## 安装

```sh
npm install react-native-kiosk-manager
```

## Android权限配置

### 自动合并

安装本库后，以下内容会**自动合并**到您的应用中，**无需手动配置**：

- ✅ 所有权限声明
- ✅ `BootReceiver` 组件
- ✅ `DeviceAdminReceiver` 组件
- ✅ `FileProvider` 组件

**您不需要在 AndroidManifest.xml 中手动声明这些组件！**

### 需要手动创建的文件

虽然组件会自动合并，但您需要手动创建以下 XML 资源文件：

**1. 必须创建**: `android/app/src/main/res/xml/device_admin_receiver.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<device-admin xmlns:android="http://schemas.android.com/apk/res/android">
  <uses-policies>
    <force-lock />
    <lock-task />
  </uses-policies>
</device-admin>
```

**2. 可选创建**（如果使用APK安装功能）: `android/app/src/main/res/xml/file_provider_paths.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths xmlns:android="http://schemas.android.com/apk/res/android">
    <external-files-path name="apk_updates" path="." />
    <external-path name="external" path="." />
</paths>
```

### 详细配置说明

📖 **完整配置文档**: [AndroidManifest.xml 配置说明](./docs/android-manifest-setup-zh.md)

## 使用方法

```js
import KioskManager from 'react-native-kiosk-manager';

// 启动Kiosk模式
KioskManager.startKiosk();

// 停止Kiosk模式
KioskManager.stopKiosk();

// 检查是否为设备所有者
const isOwner = await KioskManager.isDeviceOwner();

// 清除设备所有者状态
await KioskManager.clearDeviceOwner();
```

## 设备所有者设置指南

### 快速设置（推荐）

使用我们的自动化设置脚本：

```bash
# Linux/macOS
npm run setup-device-owner

# Windows
npm run setup-device-owner-windows
```

这些脚本会自动引导您完成整个设置过程！

### 方法1：使用ADB（Android Debug Bridge）

#### 先决条件

- 设备必须恢复出厂设置或从未添加用户账户
- 必须启用USB调试
- 计算机上必须安装ADB

#### 步骤

1. **恢复设备出厂设置**（如果尚未完成）

   ```bash
   # 可选：通过ADB重置
   adb shell am broadcast -a android.intent.action.MASTER_CLEAR
   ```

2. **跳过设置向导**，不添加任何账户
   - 不要登录Google账户
   - 不要添加任何用户账户

3. **启用开发者选项**
   - 进入设置 > 关于手机
   - 连续点击"版本号"7次
   - 返回设置 > 开发者选项
   - 启用"USB调试"

4. **连接设备到计算机并验证ADB连接**

   ```bash
   adb devices
   ```

5. **将您的应用设置为设备所有者**

   ```bash
   # 将com.yourcompany.yourapp替换为您的实际包名
   adb shell dpm set-device-owner com.riuhou.kioskmanager/.DeviceAdminReceiver
   ```

6. **验证设备所有者状态**
   ```bash
   adb shell dpm list-owners
   ```

#### 本库的示例命令

```bash
# 用于示例应用
adb shell dpm set-device-owner com.kioskmanager.example/.DeviceAdminReceiver

# 用于您的生产应用（替换为您的包名）
adb shell dpm set-device-owner com.yourcompany.yourapp/.DeviceAdminReceiver
```

### 方法2：使用MDM（移动设备管理）

#### 企业解决方案

- **Google Workspace管理控制台**
- **Microsoft Intune**
- **VMware Workspace ONE**
- **Samsung Knox**
- **自定义EMM解决方案**

#### 一般MDM设置过程

1. **在MDM中注册设备**
   - 恢复设备出厂设置
   - 在设置过程中，扫描二维码或输入注册详细信息
   - 设备将被配置为托管设备

2. **通过MDM配置设备所有者**
   - 通过MDM控制台将您的应用设置为设备所有者
   - 通过MDM部署应用
   - 配置Kiosk策略

3. **MDM方法的优势**
   - 远程管理
   - 批量部署
   - 策略执行
   - 远程擦除功能

### 方法3：使用Android Enterprise（零接触）

#### 适用于大规模部署

1. **注册Android Enterprise**
   - 注册Android Enterprise
   - 配置零接触注册

2. **设备配置**
   - 设备首次开机时自动注册
   - 您的应用自动设置为设备所有者
   - 无需手动设置

### 重要说明

⚠️ **关键要求：**

- 设备必须处于出厂重置状态
- 设置设备所有者之前不能添加用户账户
- 一旦设置了设备所有者，就不能在不恢复出厂设置的情况下更改
- 设备所有者拥有广泛的系统权限

⚠️ **安全考虑：**

- 设备所有者拥有完整的设备控制权
- 可以静默安装/卸载应用
- 可以修改系统设置
- 可以远程擦除设备

### 故障排除

#### 常见问题

1. **"不允许设置设备所有者"**
   - 解决方案：恢复出厂设置后重试，不添加账户

2. **"设备所有者已设置"**
   - 解决方案：首先清除现有的设备所有者

   ```bash
   adb shell dpm remove-active-admin com.existing.package/.AdminReceiver
   ```

3. **"用户不为空"**
   - 解决方案：删除所有用户账户后重试

4. **ADB无法识别**
   - 解决方案：安装Android SDK平台工具
   - 将ADB添加到系统PATH

#### 验证命令

```bash
# 检查当前设备所有者
adb shell dpm list-owners

# 检查设备是否已配置
adb shell getprop ro.setupwizard.mode

# 列出所有设备管理接收器
adb shell pm list packages -a | grep admin
```

### 开发与生产环境

#### 开发环境

- 使用ADB方法进行测试
- 易于重置和重新配置
- 适合调试

#### 生产环境

- 使用MDM或零接触注册
- 可扩展到多个设备
- 专业设备管理

### 生产部署

📖 **[完整生产指南](docs/production-guide.md)** - 详细的生产环境部署和管理指南

## API参考

### 方法

#### `startKiosk()`

在设备上启动Kiosk模式。

```js
KioskManager.startKiosk();
```

#### `stopKiosk()`

停止Kiosk模式并返回正常操作。

```js
KioskManager.stopKiosk();
```

#### `isDeviceOwner(): Promise<boolean>`

检查当前应用是否设置为设备所有者。

```js
const isOwner = await KioskManager.isDeviceOwner();
console.log('是设备所有者:', isOwner);
```

#### `clearDeviceOwner(): Promise<void>`

清除设备所有者状态（需要设备所有者权限）。

```js
await KioskManager.clearDeviceOwner();
```

## 系统要求

- React Native >= 0.60
- Android API Level 21+ (Android 5.0+)
- 需要设备所有者权限才能使用完整功能

## 限制

- 仅支持Android（不支持iOS）
- 需要设备所有者设置
- 某些功能可能无法在所有Android版本/OEM设备上工作
- 更改设备所有者需要恢复出厂设置

## 贡献

请参阅[贡献指南](CONTRIBUTING.md)了解如何为项目贡献代码和开发工作流程。

## 许可证

MIT

---

使用 [create-react-native-library](https://github.com/callstack/react-native-builder-bob) 创建
