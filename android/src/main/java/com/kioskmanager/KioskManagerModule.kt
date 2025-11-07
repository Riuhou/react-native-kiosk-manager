package com.riuhou.kioskmanager

import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.Promise
import android.content.SharedPreferences
import android.os.Build
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import android.Manifest
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager.NameNotFoundException
import com.facebook.react.bridge.WritableMap
import com.facebook.react.bridge.Arguments
import com.facebook.react.modules.core.DeviceEventManagerModule
import android.content.IntentSender
import android.media.AudioManager
import android.app.NotificationManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.content.BroadcastReceiver
import android.content.IntentFilter
import kotlin.math.roundToInt
import java.io.BufferedReader
import java.io.InputStreamReader

class KioskManagerModule(private val reactContext: ReactApplicationContext) :
  ReactContextBaseJavaModule(reactContext) {

  override fun getName(): String = "KioskManager"

  // 管理器实例
  private val brightnessManager = BrightnessManager(reactContext)
  private val volumeManager = VolumeManager(reactContext)
  private val systemObserver = SystemObserver(reactContext)
  private val deviceAdminManager = DeviceAdminManager(reactContext)
  private val kioskModeManager = KioskModeManager(reactContext)
  private val apkDownloadManager = ApkDownloadManager(reactContext) { progress, bytesRead, totalBytes ->
    sendProgressEvent(progress, bytesRead, totalBytes)
  }
  private val appLaunchManager = AppLaunchManager(reactContext) { status, packageName, message ->
    sendInstallStatusEvent(status, packageName, message)
  }

  // 安装相关状态（保留在主模块中，因为与安装接收器紧密耦合）
  private var installReceiver: BroadcastReceiver? = null
  private var pendingInstallPackageName: String? = null
  private var pendingInstallSessionId: Int? = null
  private var pollingRunnable: Runnable? = null
  private var isInstallComplete: Boolean = false
  private var pendingInstallOldVersionCode: Long? = null // 安装前的版本号
  private var pendingInstallOldLastUpdateTime: Long? = null // 安装前的最后更新时间

  private val installCompleteAction: String
    get() = "${reactApplicationContext.packageName}.INSTALL_COMPLETE"

  @ReactMethod
  fun startKiosk() {
    val activity = reactContext.currentActivity ?: return

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      val am = activity.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
      if (am.lockTaskModeState == ActivityManager.LOCK_TASK_MODE_NONE) {
        activity.startLockTask()
      }
    }
  }

  // === 屏幕亮度控制 ===
  @ReactMethod
  fun hasWriteSettingsPermission(promise: Promise) = brightnessManager.hasWriteSettingsPermission(promise)

  @ReactMethod
  fun requestWriteSettingsPermission(promise: Promise) = brightnessManager.requestWriteSettingsPermission(promise)

  @ReactMethod
  fun setSystemBrightness(value: Int, promise: Promise) = brightnessManager.setSystemBrightness(value, promise)

  @ReactMethod
  fun getSystemBrightness(promise: Promise) = brightnessManager.getSystemBrightness(promise)

  @ReactMethod
  fun setAppBrightness(value: Double) = brightnessManager.setAppBrightness(value)

  @ReactMethod
  fun resetAppBrightness() = brightnessManager.resetAppBrightness()

  @ReactMethod
  fun getAppBrightness(promise: Promise) = brightnessManager.getAppBrightness(promise)

  // === 音量控制 ===
  @ReactMethod
  fun setVolume(stream: String, value: Double, promise: Promise) = volumeManager.setVolume(stream, value, promise)

  @ReactMethod
  fun getVolume(stream: String, promise: Promise) = volumeManager.getVolume(stream, promise)

  @ReactMethod
  fun getMaxVolume(stream: String, promise: Promise) = volumeManager.getMaxVolume(stream, promise)

  @ReactMethod
  fun setGlobalVolume(value: Double, promise: Promise) = volumeManager.setGlobalVolume(value, promise)

  @ReactMethod
  fun getGlobalVolume(promise: Promise) = volumeManager.getGlobalVolume(promise)

  @ReactMethod
  fun setMute(stream: String, muted: Boolean, promise: Promise) = volumeManager.setMute(stream, muted, promise)

  @ReactMethod
  fun isMuted(stream: String, promise: Promise) = volumeManager.isMuted(stream, promise)

  @ReactMethod
  fun setGlobalMute(muted: Boolean, promise: Promise) = volumeManager.setGlobalMute(muted, promise)

  @ReactMethod
  fun isGlobalMuted(promise: Promise) = volumeManager.isGlobalMuted(promise)

  @ReactMethod
  fun getRingerMode(promise: Promise) = volumeManager.getRingerMode(promise)

  @ReactMethod
  fun setRingerMode(mode: String, promise: Promise) = volumeManager.setRingerMode(mode, promise)

  @ReactMethod
  fun hasNotificationPolicyAccess(promise: Promise) = volumeManager.hasNotificationPolicyAccess(promise)

  @ReactMethod
  fun requestNotificationPolicyAccess(promise: Promise) = volumeManager.requestNotificationPolicyAccess(promise)

  // === 系统亮度与音量变更监听 ===
  @ReactMethod
  fun startObservingSystemAv() = systemObserver.startObservingSystemAv()

  @ReactMethod
  fun stopObservingSystemAv() = systemObserver.stopObservingSystemAv()

  @ReactMethod
  fun stopKiosk() {
    val activity = reactContext.currentActivity ?: return

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      activity.stopLockTask()
    }
  }

  @ReactMethod
  fun enableBootAutoStart(enabled: Boolean) {
      val prefs = reactContext.getSharedPreferences("kiosk_prefs", Context.MODE_PRIVATE)
      prefs.edit().putBoolean("boot_auto_start", enabled).apply()
  }

  @ReactMethod
  fun isBootAutoStartEnabled(promise: Promise) {
    val prefs = reactContext.getSharedPreferences("kiosk_prefs", Context.MODE_PRIVATE)
    val enabled = prefs.getBoolean("boot_auto_start", true)
    promise.resolve(enabled)
  }

  // === 设备管理员 ===
  @ReactMethod
  fun requestDeviceAdmin(promise: Promise) = deviceAdminManager.requestDeviceAdmin(promise)

  @ReactMethod
  fun setupLockTaskPackage(promise: Promise) = deviceAdminManager.setupLockTaskPackage(promise)

  @ReactMethod
  fun clearDeviceOwner(promise: Promise) = deviceAdminManager.clearDeviceOwner(promise)

  @ReactMethod
  fun isDeviceOwner(promise: Promise) = deviceAdminManager.isDeviceOwner(promise)

  // === APK下载 ===
  @ReactMethod
  fun downloadApk(url: String, promise: Promise) = apkDownloadManager.downloadApk(url, promise)

  private fun sendProgressEvent(progress: Int, bytesRead: Long, totalBytes: Long) {
    try {
      val progressMap = Arguments.createMap()
      progressMap.putInt("progress", progress)
      progressMap.putLong("bytesRead", bytesRead)
      progressMap.putLong("totalBytes", totalBytes)
      progressMap.putDouble("percentage", progress.toDouble())
      
      reactContext
        .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
        .emit("KioskManagerDownloadProgress", progressMap)
    } catch (e: Exception) {
      Log.e("KioskManager", "Failed to send progress event: ${e.message}")
    }
  }

  private fun sendInstallStatusEvent(status: String, packageName: String? = null, message: String? = null, progress: Int? = null) {
    try {
      val statusMap = Arguments.createMap()
      statusMap.putString("status", status)
      if (packageName != null) {
        statusMap.putString("packageName", packageName)
      }
      if (message != null) {
        statusMap.putString("message", message)
      }
      if (progress != null) {
        statusMap.putInt("progress", progress)
      }
      
      reactContext
        .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
        .emit("KioskManagerInstallStatus", statusMap)
      
      Log.i("KioskManager", "发送安装状态事件: $status, packageName: $packageName, message: $message")
    } catch (e: Exception) {
      Log.e("KioskManager", "Failed to send install status event: ${e.message}")
    }
  }

  @ReactMethod
  fun installApk(filePath: String, promise: Promise) {
    try {
      val context = reactApplicationContext
      val apkFile = File(filePath)
      
      // 打印安装文件信息
      Log.i("KioskManager", "=== 开始安装 ===")
      Log.i("KioskManager", "APK文件路径: $filePath")
      Log.i("KioskManager", "文件存在: ${apkFile.exists()}")
      Log.i("KioskManager", "文件大小: ${apkFile.length()} 字节")
      Log.i("KioskManager", "文件可读: ${apkFile.canRead()}")
      Log.i("KioskManager", "==================")
      
      if (!apkFile.exists()) {
        promise.reject("E_INSTALL_FAILED", "APK file not found: $filePath")
        return
      }
      
      // 检查是否有安装权限
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        if (!context.packageManager.canRequestPackageInstalls()) {
          // 请求安装权限
          val activity = reactContext.currentActivity
          if (activity != null) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
            intent.data = Uri.parse("package:${context.packageName}")
            activity.startActivity(intent)
            promise.reject("E_PERMISSION_REQUIRED", "Install permission required. Please enable 'Install unknown apps' for this app.")
            return
          }
        }
      }
      
      val apkUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
      } else {
        Uri.fromFile(apkFile)
      }
      
      val intent = Intent(Intent.ACTION_VIEW)
      intent.setDataAndType(apkUri, "application/vnd.android.package-archive")
      intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
      
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        intent.flags = intent.flags or Intent.FLAG_GRANT_READ_URI_PERMISSION
      }
      
      context.startActivity(intent)
      
      // 打印安装完成信息
      Log.i("KioskManager", "=== 安装启动成功 ===")
      Log.i("KioskManager", "已启动系统安装界面")
      Log.i("KioskManager", "APK URI: $apkUri")
      Log.i("KioskManager", "==================")
      
      promise.resolve(true)
      
    } catch (e: Exception) {
      Log.e("KioskManager", "Install failed: ${e.message}")
      promise.reject("E_INSTALL_FAILED", "Install failed: ${e.message}")
    }
  }

  @ReactMethod
  fun downloadAndInstallApk(url: String, promise: Promise) {
    downloadApk(url, object : Promise {
      override fun resolve(value: Any?) {
        if (value is WritableMap) {
          val filePath = value.getString("filePath")
          if (filePath != null) {
            installApk(filePath, promise)
          } else {
            promise.reject("E_DOWNLOAD_FAILED", "Failed to get file path from download")
          }
        } else {
          promise.reject("E_DOWNLOAD_FAILED", "Invalid download result")
        }
      }
      
      override fun reject(code: String, message: String?) {
        promise.reject(code, message)
      }
      
      override fun reject(code: String, throwable: Throwable?) {
        promise.reject(code, throwable)
      }
      
      override fun reject(code: String, message: String?, throwable: Throwable?) {
        promise.reject(code, message, throwable)
      }
      
      override fun reject(throwable: Throwable) {
        promise.reject(throwable)
      }
      
      override fun reject(throwable: Throwable, userInfo: WritableMap) {
        promise.reject(throwable, userInfo)
      }
      
      override fun reject(code: String, userInfo: WritableMap) {
        promise.reject(code, userInfo)
      }
      
      override fun reject(code: String, throwable: Throwable?, userInfo: WritableMap) {
        promise.reject(code, throwable, userInfo)
      }
      
      override fun reject(code: String, message: String?, userInfo: WritableMap) {
        promise.reject(code, message, userInfo)
      }
      
      override fun reject(code: String?, message: String?, throwable: Throwable?, userInfo: WritableMap?) {
        promise.reject(code, message, throwable, userInfo)
      }
      
      @Deprecated("Prefer passing a module-specific error code to JS. Using this method will pass the error code EUNSPECIFIED")
      override fun reject(message: String) {
        promise.reject(message)
      }
    })
  }

  @ReactMethod
  fun checkInstallPermission(promise: Promise) {
    try {
      val context = reactApplicationContext
      val canInstall = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.packageManager.canRequestPackageInstalls()
      } else {
        true
      }
      promise.resolve(canInstall)
    } catch (e: Exception) {
      promise.reject("E_CHECK_FAILED", "Failed to check install permission: ${e.message}")
    }
  }

  @ReactMethod
  fun requestInstallPermission(promise: Promise) {
    try {
      val activity = reactContext.currentActivity
      if (activity == null) {
        promise.reject("E_NO_ACTIVITY", "No current activity")
        return
      }
      
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
        intent.data = Uri.parse("package:${reactContext.packageName}")
        activity.startActivity(intent)
        promise.resolve(true)
      } else {
        promise.resolve(true)
      }
    } catch (e: Exception) {
      promise.reject("E_REQUEST_FAILED", "Failed to request install permission: ${e.message}")
    }
  }

  @ReactMethod
  fun getDownloadedFiles(promise: Promise) = apkDownloadManager.getDownloadedFiles(promise)

  @ReactMethod
  fun deleteDownloadedFile(filePath: String, promise: Promise) = apkDownloadManager.deleteDownloadedFile(filePath, promise)

  @ReactMethod
  fun clearAllDownloadedFiles(promise: Promise) = apkDownloadManager.clearAllDownloadedFiles(promise)

  @ReactMethod
  fun silentInstallApk(filePath: String, promise: Promise) {
    try {
      val context = reactApplicationContext
      val apkFile = File(filePath)
      
      // 打印静默安装文件信息
      Log.i("KioskManager", "=== 开始静默安装 ===")
      Log.i("KioskManager", "APK文件路径: $filePath")
      Log.i("KioskManager", "文件存在: ${apkFile.exists()}")
      Log.i("KioskManager", "文件大小: ${apkFile.length()} 字节")
      Log.i("KioskManager", "文件可读: ${apkFile.canRead()}")
      Log.i("KioskManager", "==================")
      
      if (!apkFile.exists()) {
        promise.reject("E_SILENT_INSTALL_FAILED", "APK file not found: $filePath")
        return
      }
      
      // 检查设备管理员权限
      val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
      val adminComponent = ComponentName(context, DeviceAdminReceiver::class.java)
      
      if (!dpm.isDeviceOwnerApp(context.packageName)) {
        Log.e("KioskManager", "App is not device owner, cannot perform silent install")
        promise.reject("E_NOT_DEVICE_OWNER", "App is not device owner. Silent install requires device owner privileges.")
        return
      }
      
      // 使用PackageInstaller进行静默安装
      val packageInstaller = context.packageManager.packageInstaller
      val sessionParams = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
      
      // 获取APK包信息
      val packageInfo = context.packageManager.getPackageArchiveInfo(filePath, 0)
      if (packageInfo != null) {
        sessionParams.setAppPackageName(packageInfo.packageName)
        Log.i("KioskManager", "目标包名: ${packageInfo.packageName}")
      }
      
      // 设置安装参数，确保完全静默安装
      try {
        // 使用数字常量而不是可能不存在的常量
        sessionParams.setInstallLocation(1) // 1 = INSTALL_LOCATION_INTERNAL_ONLY
      } catch (e: Exception) {
        Log.w("KioskManager", "setInstallLocation not available: ${e.message}")
      }
      
      try {
        // 使用数字常量
        sessionParams.setInstallReason(4) // 4 = INSTALL_REASON_DEVICE_RESTORE
      } catch (e: Exception) {
        Log.w("KioskManager", "setInstallReason not available: ${e.message}")
      }
      
      // 添加额外的静默安装标志
      try {
        // 使用反射调用可能不存在的方法
        val method = sessionParams.javaClass.getMethod("setInstallFlags", Int::class.java)
        method.invoke(sessionParams, 2) // 2 = INSTALL_REPLACE_EXISTING
      } catch (e: Exception) {
        Log.w("KioskManager", "setInstallFlags not available: ${e.message}")
      }
      
      // 创建安装会话
      val sessionId = packageInstaller.createSession(sessionParams)
      val session = packageInstaller.openSession(sessionId)
      
      // 将APK文件写入会话
      val inputStream = apkFile.inputStream()
      val outputStream = session.openWrite("package", 0, apkFile.length())
      
      val buffer = ByteArray(8192)
      var bytesRead: Int
      while (inputStream.read(buffer).also { bytesRead = it } != -1) {
        outputStream.write(buffer, 0, bytesRead)
      }
      
      inputStream.close()
      outputStream.close()
      
      // 创建IntentSender用于安装回调
      // Android 12+ (API 31) 要求使用 FLAG_MUTABLE，否则使用 FLAG_IMMUTABLE
      // 使用常量值避免低版本 SDK 编译错误（FLAG_MUTABLE = 0x02000000）
      val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        // Android 12+ requires mutable PendingIntent for PackageInstaller
        android.app.PendingIntent.FLAG_UPDATE_CURRENT or 0x02000000 // FLAG_MUTABLE
      } else {
        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
      }
      val pendingIntent = android.app.PendingIntent.getBroadcast(
        context,
        sessionId,
        Intent(installCompleteAction),
        pendingIntentFlags
      )
      
      // 提交安装
      session.commit(pendingIntent.intentSender)
      
      Log.i("KioskManager", "=== 静默安装提交成功 ===")
      Log.i("KioskManager", "安装会话ID: $sessionId")
      Log.i("KioskManager", "==================")
      promise.resolve(true)
      
      session.close()
      
    } catch (e: Exception) {
      Log.e("KioskManager", "Silent install failed: ${e.message}")
      promise.reject("E_SILENT_INSTALL_FAILED", "Silent install failed: ${e.message}")
    }
  }

  @ReactMethod
  fun downloadAndSilentInstallApk(url: String, promise: Promise) {
    downloadApk(url, object : Promise {
      override fun resolve(value: Any?) {
        if (value is WritableMap) {
          val filePath = value.getString("filePath")
          if (filePath != null) {
            silentInstallAndLaunchApk(filePath, promise)
          } else {
            promise.reject("E_DOWNLOAD_FAILED", "Failed to get file path from download")
          }
        } else {
          promise.reject("E_DOWNLOAD_FAILED", "Invalid download result")
        }
      }
      
      override fun reject(code: String, message: String?) {
        promise.reject(code, message)
      }
      
      override fun reject(code: String, throwable: Throwable?) {
        promise.reject(code, throwable)
      }
      
      override fun reject(code: String, message: String?, throwable: Throwable?) {
        promise.reject(code, message, throwable)
      }
      
      override fun reject(throwable: Throwable) {
        promise.reject(throwable)
      }
      
      override fun reject(throwable: Throwable, userInfo: WritableMap) {
        promise.reject(throwable, userInfo)
      }
      
      override fun reject(code: String, userInfo: WritableMap) {
        promise.reject(code, userInfo)
      }
      
      override fun reject(code: String, throwable: Throwable?, userInfo: WritableMap) {
        promise.reject(code, throwable, userInfo)
      }
      
      override fun reject(code: String, message: String?, userInfo: WritableMap) {
        promise.reject(code, message, userInfo)
      }
      
      override fun reject(code: String?, message: String?, throwable: Throwable?, userInfo: WritableMap?) {
        promise.reject(code, message, throwable, userInfo)
      }
      
      @Deprecated("Prefer passing a module-specific error code to JS. Using this method will pass the error code EUNSPECIFIED")
      override fun reject(message: String) {
        promise.reject(message)
      }
    })
  }

  @ReactMethod
  fun silentInstallAndLaunchApk(filePath: String, promise: Promise) {
    try {
      val context = reactApplicationContext
      val apkFile = File(filePath)
      
      // 打印静默安装并启动文件信息
      Log.i("KioskManager", "=== 开始静默安装并启动 ===")
      Log.i("KioskManager", "APK文件路径: $filePath")
      Log.i("KioskManager", "文件存在: ${apkFile.exists()}")
      Log.i("KioskManager", "文件大小: ${apkFile.length()} 字节")
      Log.i("KioskManager", "==================")
      
      if (!apkFile.exists()) {
        promise.reject("E_SILENT_INSTALL_FAILED", "APK file not found: $filePath")
        return
      }
      
      // 检查设备管理员权限
      val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
      val adminComponent = ComponentName(context, DeviceAdminReceiver::class.java)
      
      if (!dpm.isDeviceOwnerApp(context.packageName)) {
        Log.e("KioskManager", "App is not device owner, cannot perform silent install")
        promise.reject("E_NOT_DEVICE_OWNER", "App is not device owner. Silent install requires device owner privileges.")
        return
      }
      
      // 获取APK包信息
      val packageInfo = context.packageManager.getPackageArchiveInfo(filePath, 0)
      if (packageInfo == null) {
        promise.reject("E_INVALID_APK", "Invalid APK file")
        return
      }
      
      val targetPackageName = packageInfo.packageName
      val newVersionCode = packageInfo.longVersionCode
      Log.i("KioskManager", "目标包名: $targetPackageName")
      Log.i("KioskManager", "新版本号: $newVersionCode")
      
      // 保存待启动的包名到 SharedPreferences，供 InstallCompleteReceiver 使用
      // 这样即使应用进程被杀死，静态注册的接收器也能知道要启动哪个应用
      try {
        val prefs = context.getSharedPreferences("kiosk_manager_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("pending_launch_package", targetPackageName).apply()
        Log.i("KioskManager", "已保存待启动包名到 SharedPreferences: $targetPackageName")
      } catch (e: Exception) {
        Log.w("KioskManager", "保存待启动包名到 SharedPreferences 失败: ${e.message}")
      }
      
      // 记录安装前的版本号（如果包已存在）
      try {
        val existingPackageInfo = context.packageManager.getPackageInfo(targetPackageName, 0)
        pendingInstallOldVersionCode = existingPackageInfo.longVersionCode
        pendingInstallOldLastUpdateTime = existingPackageInfo.lastUpdateTime
        Log.i("KioskManager", "检测到包已存在，当前版本号: ${pendingInstallOldVersionCode}, 最后更新时间: ${pendingInstallOldLastUpdateTime}")
      } catch (e: PackageManager.NameNotFoundException) {
        pendingInstallOldVersionCode = null
        pendingInstallOldLastUpdateTime = null
        Log.i("KioskManager", "包不存在，这是首次安装")
      }
      
      // 发送开始安装事件
      sendInstallStatusEvent("installing", targetPackageName, "开始安装应用")
      
      // 使用PackageInstaller进行静默安装
      val packageInstaller = context.packageManager.packageInstaller
      val sessionParams = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
      sessionParams.setAppPackageName(targetPackageName)
      
      // 设置安装参数，确保完全静默安装
      try {
        // 使用数字常量而不是可能不存在的常量
        sessionParams.setInstallLocation(1) // 1 = INSTALL_LOCATION_INTERNAL_ONLY
      } catch (e: Exception) {
        Log.w("KioskManager", "setInstallLocation not available: ${e.message}")
      }
      
      try {
        // 使用数字常量
        sessionParams.setInstallReason(4) // 4 = INSTALL_REASON_DEVICE_RESTORE
      } catch (e: Exception) {
        Log.w("KioskManager", "setInstallReason not available: ${e.message}")
      }
      
      // 添加额外的静默安装标志
      try {
        // 使用反射调用可能不存在的方法
        val method = sessionParams.javaClass.getMethod("setInstallFlags", Int::class.java)
        method.invoke(sessionParams, 2) // 2 = INSTALL_REPLACE_EXISTING
      } catch (e: Exception) {
        Log.w("KioskManager", "setInstallFlags not available: ${e.message}")
      }
      
      // 创建安装会话
      val sessionId = packageInstaller.createSession(sessionParams)
      val session = packageInstaller.openSession(sessionId)
      
      // 将APK文件写入会话
      val inputStream = apkFile.inputStream()
      val outputStream = session.openWrite("package", 0, apkFile.length())
      
      val buffer = ByteArray(8192)
      var bytesRead: Int
      while (inputStream.read(buffer).also { bytesRead = it } != -1) {
        outputStream.write(buffer, 0, bytesRead)
      }
      
      inputStream.close()
      outputStream.close()
      
      // 创建IntentSender用于安装回调
      // 使用静态注册的接收器，即使应用进程被杀死也能接收广播
      // 这对于应用自己更新自己的场景非常重要
      // 注意：只设置包名和 Action，让系统自动找到静态注册的接收器
      // 不设置 setClass，因为应用更新自己时，旧版本的类引用可能失效
      val intent = Intent(installCompleteAction).apply {
        setPackage(context.packageName)
        // 传递包名信息，因为应用更新自己时，新版本需要知道要启动哪个包
        putExtra("target_package_name", targetPackageName)
      }
      // Android 12+ (API 31) 要求使用 FLAG_MUTABLE，否则使用 FLAG_IMMUTABLE
      // 使用常量值避免低版本 SDK 编译错误（FLAG_MUTABLE = 0x02000000）
      val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        // Android 12+ requires mutable PendingIntent for PackageInstaller
        android.app.PendingIntent.FLAG_UPDATE_CURRENT or 0x02000000 // FLAG_MUTABLE
      } else {
        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
      }
      val pendingIntent = android.app.PendingIntent.getBroadcast(
        context, 
        sessionId, 
        intent, 
        pendingIntentFlags
      )
      
      // 保存待安装的包名和会话ID，用于接收器（用于动态接收器，作为备份）
      pendingInstallPackageName = targetPackageName
      pendingInstallSessionId = sessionId
      
      // 重置安装完成标志和停止之前的轮询
      isInstallComplete = false
      pollingRunnable?.let { runnable ->
        Handler(Looper.getMainLooper()).removeCallbacks(runnable)
        pollingRunnable = null
      }
      
      // 注册安装完成接收器（如果还没有注册）
      registerInstallReceiver()
      
      // 提交安装
      session.commit(pendingIntent.intentSender)
      
      Log.i("KioskManager", "=== 静默安装提交成功 ===")
      Log.i("KioskManager", "安装会话ID: $sessionId")
      Log.i("KioskManager", "目标包名: $targetPackageName")
      Log.i("KioskManager", "==================")
      
      // 发送安装提交事件
      sendInstallStatusEvent("installing", targetPackageName, "安装已提交，等待完成")
      
      // 使用轮询方式检测安装完成，然后启动应用
      startPollingForInstallCompletion(targetPackageName, sessionId)
      
      promise.resolve(true)
      session.close()
      
    } catch (e: Exception) {
      Log.e("KioskManager", "Silent install and launch failed: ${e.message}")
      sendInstallStatusEvent("failed", null, "安装失败: ${e.message}")
      promise.reject("E_SILENT_INSTALL_FAILED", "Silent install and launch failed: ${e.message}")
    }
  }

  private fun registerInstallReceiver() {
    if (installReceiver != null) return
    
    val context = reactApplicationContext
    installReceiver = object : BroadcastReceiver() {
      override fun onReceive(ctx: Context?, intent: Intent?) {
        if (intent == null) return
        
        val action = intent.action
        if (action == installCompleteAction) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)
        // PackageInstaller.EXTRA_PACKAGE_NAME 可能不存在，使用我们保存的包名
        val packageName = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME) ?: pendingInstallPackageName
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        
        Log.i("KioskManager", "收到安装完成广播: status=$status, packageName=$packageName, message=$message")
        
        when (status) {
            PackageInstaller.STATUS_SUCCESS -> {
              val finalPackageName = packageName ?: pendingInstallPackageName
              if (finalPackageName != null) {
                Log.i("KioskManager", "安装成功: $finalPackageName")
                // 标记安装完成，停止轮询
                isInstallComplete = true
                // 停止轮询
                pollingRunnable?.let { runnable ->
                  Handler(Looper.getMainLooper()).removeCallbacks(runnable)
                  pollingRunnable = null
                }
                // 发送100%进度和安装成功事件
                sendInstallStatusEvent("installing", finalPackageName, "安装完成", 100)
                sendInstallStatusEvent("installed", finalPackageName, "安装成功")
                // 清除旧版本号记录
                pendingInstallOldVersionCode = null
                pendingInstallOldLastUpdateTime = null
                // 如果这是我们正在等待的包，启动应用
                // 延迟1秒启动，给 PackageManager 足够时间刷新缓存
                if (finalPackageName == pendingInstallPackageName) {
                  Handler(Looper.getMainLooper()).postDelayed({
                    try {
                      sendInstallStatusEvent("launching", finalPackageName, "正在启动应用")
                      appLaunchManager.launchAppInternal(finalPackageName)
                    } catch (e: Exception) {
                      Log.e("KioskManager", "启动应用失败: ${e.message}")
                      sendInstallStatusEvent("launch_failed", finalPackageName, "启动失败: ${e.message}")
                    }
                  }, 1000) // 增加到1秒延迟
                }
              } else {
                Log.w("KioskManager", "安装成功但无法获取包名")
                isInstallComplete = true
                pollingRunnable?.let { runnable ->
                  Handler(Looper.getMainLooper()).removeCallbacks(runnable)
                  pollingRunnable = null
                }
                sendInstallStatusEvent("installed", null, "安装成功")
              }
            }
            PackageInstaller.STATUS_FAILURE -> {
              val finalPackageName = packageName ?: pendingInstallPackageName
              Log.e("KioskManager", "安装失败: $finalPackageName, 错误: $message")
              // 停止轮询
              isInstallComplete = true
              pollingRunnable?.let { runnable ->
                Handler(Looper.getMainLooper()).removeCallbacks(runnable)
                pollingRunnable = null
              }
              sendInstallStatusEvent("failed", finalPackageName, "安装失败: $message")
              pendingInstallOldVersionCode = null
              pendingInstallOldLastUpdateTime = null
            }
            PackageInstaller.STATUS_FAILURE_ABORTED -> {
              val finalPackageName = packageName ?: pendingInstallPackageName
              Log.e("KioskManager", "安装已取消: $finalPackageName")
              sendInstallStatusEvent("cancelled", finalPackageName, "安装已取消")
              pendingInstallOldVersionCode = null
              pendingInstallOldLastUpdateTime = null
            }
            PackageInstaller.STATUS_FAILURE_BLOCKED -> {
              val finalPackageName = packageName ?: pendingInstallPackageName
              Log.e("KioskManager", "安装被阻止: $finalPackageName")
              sendInstallStatusEvent("blocked", finalPackageName, "安装被阻止")
              pendingInstallOldVersionCode = null
              pendingInstallOldLastUpdateTime = null
            }
            PackageInstaller.STATUS_FAILURE_CONFLICT -> {
              val finalPackageName = packageName ?: pendingInstallPackageName
              Log.e("KioskManager", "安装冲突: $finalPackageName")
              sendInstallStatusEvent("conflict", finalPackageName, "安装冲突")
              pendingInstallOldVersionCode = null
              pendingInstallOldLastUpdateTime = null
            }
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> {
              val finalPackageName = packageName ?: pendingInstallPackageName
              Log.e("KioskManager", "应用不兼容: $finalPackageName")
              sendInstallStatusEvent("incompatible", finalPackageName, "应用不兼容")
              pendingInstallOldVersionCode = null
              pendingInstallOldLastUpdateTime = null
            }
            PackageInstaller.STATUS_FAILURE_INVALID -> {
              val finalPackageName = packageName ?: pendingInstallPackageName
              Log.e("KioskManager", "无效的APK: $finalPackageName")
              sendInstallStatusEvent("invalid", finalPackageName, "无效的APK")
              pendingInstallOldVersionCode = null
              pendingInstallOldLastUpdateTime = null
            }
            PackageInstaller.STATUS_FAILURE_STORAGE -> {
              val finalPackageName = packageName ?: pendingInstallPackageName
              Log.e("KioskManager", "存储空间不足: $finalPackageName")
              sendInstallStatusEvent("storage_error", finalPackageName, "存储空间不足")
              pendingInstallOldVersionCode = null
              pendingInstallOldLastUpdateTime = null
            }
            else -> {
              val finalPackageName = packageName ?: pendingInstallPackageName
              Log.w("KioskManager", "未知安装状态: $status, packageName: $finalPackageName")
              sendInstallStatusEvent("unknown", finalPackageName, "未知状态: $status")
              pendingInstallOldVersionCode = null
              pendingInstallOldLastUpdateTime = null
            }
          }
        }
      }
    }
    
    try {
      val filter = IntentFilter(installCompleteAction)
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.registerReceiver(installReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
      } else {
        context.registerReceiver(installReceiver, filter)
      }
      Log.i("KioskManager", "安装完成接收器已注册")
    } catch (e: Exception) {
      Log.e("KioskManager", "注册安装接收器失败: ${e.message}")
    }
  }

  @ReactMethod
  fun downloadAndSilentInstallAndLaunchApk(url: String, promise: Promise) {
    downloadApk(url, object : Promise {
      override fun resolve(value: Any?) {
        if (value is WritableMap) {
          val filePath = value.getString("filePath")
          if (filePath != null) {
            silentInstallAndLaunchApk(filePath, promise)
          } else {
            promise.reject("E_DOWNLOAD_FAILED", "Failed to get file path from download")
          }
        } else {
          promise.reject("E_DOWNLOAD_FAILED", "Invalid download result")
        }
      }
      
      override fun reject(code: String, message: String?) {
        promise.reject(code, message)
      }
      
      override fun reject(code: String, throwable: Throwable?) {
        promise.reject(code, throwable)
      }
      
      override fun reject(code: String, message: String?, throwable: Throwable?) {
        promise.reject(code, message, throwable)
      }
      
      override fun reject(throwable: Throwable) {
        promise.reject(throwable)
      }
      
      override fun reject(throwable: Throwable, userInfo: WritableMap) {
        promise.reject(throwable, userInfo)
      }
      
      override fun reject(code: String, userInfo: WritableMap) {
        promise.reject(code, userInfo)
      }
      
      override fun reject(code: String, throwable: Throwable?, userInfo: WritableMap) {
        promise.reject(code, throwable, userInfo)
      }
      
      override fun reject(code: String, message: String?, userInfo: WritableMap) {
        promise.reject(code, message, userInfo)
      }
      
      override fun reject(code: String?, message: String?, throwable: Throwable?, userInfo: WritableMap?) {
        promise.reject(code, message, throwable, userInfo)
      }
      
      @Deprecated("Prefer passing a module-specific error code to JS. Using this method will pass the error code EUNSPECIFIED")
      override fun reject(message: String) {
        promise.reject(message)
      }
    })
  }

  @ReactMethod
  fun systemSilentInstallApk(filePath: String, promise: Promise) {
    try {
      val context = reactApplicationContext
      val apkFile = File(filePath)
      
      Log.i("KioskManager", "=== 开始系统级静默安装 ===")
      Log.i("KioskManager", "APK文件路径: $filePath")
      Log.i("KioskManager", "文件存在: ${apkFile.exists()}")
      Log.i("KioskManager", "文件大小: ${apkFile.length()} 字节")
      Log.i("KioskManager", "文件可读: ${apkFile.canRead()}")
      Log.i("KioskManager", "==================")
      
      if (!apkFile.exists()) {
        promise.reject("E_SYSTEM_INSTALL_FAILED", "APK file not found: $filePath")
        return
      }
      
      // 检查设备管理员权限
      val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
      
      if (!dpm.isDeviceOwnerApp(context.packageName)) {
        Log.e("KioskManager", "App is not device owner, cannot perform system silent install")
        promise.reject("E_NOT_DEVICE_OWNER", "App is not device owner. System silent install requires device owner privileges.")
        return
      }
      
      // 使用PackageInstaller进行系统级静默安装（设备所有者可以使用此方法进行静默安装）
      val packageInstaller = context.packageManager.packageInstaller
      val sessionParams = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
      
      // 获取APK包信息
      val packageInfo = context.packageManager.getPackageArchiveInfo(filePath, 0)
      if (packageInfo != null) {
        val targetPackageName = packageInfo.packageName
        sessionParams.setAppPackageName(targetPackageName)
        Log.i("KioskManager", "目标包名: $targetPackageName")
      } else {
        promise.reject("E_INVALID_APK", "Invalid APK file")
        return
      }
      
      // 设置安装参数，确保完全静默安装
      try {
        // 使用数字常量而不是可能不存在的常量
        sessionParams.setInstallLocation(1) // 1 = INSTALL_LOCATION_INTERNAL_ONLY
      } catch (e: Exception) {
        Log.w("KioskManager", "setInstallLocation not available: ${e.message}")
      }
      
      try {
        // 使用数字常量
        sessionParams.setInstallReason(4) // 4 = INSTALL_REASON_DEVICE_RESTORE
      } catch (e: Exception) {
        Log.w("KioskManager", "setInstallReason not available: ${e.message}")
      }
      
      // 添加额外的静默安装标志
      try {
        // 使用反射调用可能不存在的方法
        val method = sessionParams.javaClass.getMethod("setInstallFlags", Int::class.java)
        method.invoke(sessionParams, 2) // 2 = INSTALL_REPLACE_EXISTING
      } catch (e: Exception) {
        Log.w("KioskManager", "setInstallFlags not available: ${e.message}")
      }
      
      // 创建安装会话
      val sessionId = packageInstaller.createSession(sessionParams)
      val session = packageInstaller.openSession(sessionId)
      
      // 将APK文件写入会话
      val inputStream = apkFile.inputStream()
      val outputStream = session.openWrite("package", 0, apkFile.length())
      
      val buffer = ByteArray(8192)
      var bytesRead: Int
      while (inputStream.read(buffer).also { bytesRead = it } != -1) {
        outputStream.write(buffer, 0, bytesRead)
      }
      
      inputStream.close()
      outputStream.close()
      
      // 创建IntentSender用于安装回调
      // Android 12+ (API 31) 要求使用 FLAG_MUTABLE，否则使用 FLAG_IMMUTABLE
      // 使用常量值避免低版本 SDK 编译错误（FLAG_MUTABLE = 0x02000000）
      val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        // Android 12+ requires mutable PendingIntent for PackageInstaller
        android.app.PendingIntent.FLAG_UPDATE_CURRENT or 0x02000000 // FLAG_MUTABLE
      } else {
        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
      }
      val pendingIntent = android.app.PendingIntent.getBroadcast(
        context, 
        sessionId, 
        Intent(installCompleteAction), 
        pendingIntentFlags
      )
      
      // 提交安装
      session.commit(pendingIntent.intentSender)
      
      Log.i("KioskManager", "=== 系统级静默安装提交成功 ===")
      Log.i("KioskManager", "安装会话ID: $sessionId")
      Log.i("KioskManager", "目标包名: ${packageInfo.packageName}")
      Log.i("KioskManager", "==================")
      
      promise.resolve(true)
      session.close()
      
    } catch (e: Exception) {
      Log.e("KioskManager", "System silent install failed: ${e.message}", e)
      promise.reject("E_SYSTEM_INSTALL_FAILED", "System silent install failed: ${e.message}")
    }
  }

  // === 应用启动 ===
  @ReactMethod
  fun isAppInstalled(packageName: String, promise: Promise) = appLaunchManager.isAppInstalled(packageName, promise)

  @ReactMethod
  fun launchApp(packageName: String, promise: Promise) = appLaunchManager.launchApp(packageName, promise)

  private fun startPollingForInstallCompletion(packageName: String, sessionId: Int) {
    val context = reactApplicationContext
    val handler = Handler(Looper.getMainLooper())
    val packageManager = context.packageManager
    var attemptCount = 0
    val maxAttempts = 120 // 最多尝试120次（60秒，每500ms一次）
    val pollInterval = 500L // 每500ms检查一次
    
    // 重置安装完成标志
    isInstallComplete = false
    
    Log.i("KioskManager", "开始轮询检测安装状态: $packageName, sessionId: $sessionId")
    
    val checkRunnable = object : Runnable {
      override fun run() {
        // 如果已经通过广播接收器收到安装完成通知，停止轮询
        if (isInstallComplete) {
          Log.i("KioskManager", "安装已完成（通过广播接收器），停止轮询: $packageName")
          pollingRunnable = null
          return
        }
        
        attemptCount++
        
        try {
          // 使用基于时间的估算进度（每10次更新一次）
          if (attemptCount % 10 == 0) {
            val estimatedProgress = (attemptCount * 90 / maxAttempts).coerceAtMost(90)
            sendInstallStatusEvent("installing", packageName, "正在安装中... ($estimatedProgress%)", estimatedProgress)
            Log.d("KioskManager", "安装进度估算: $estimatedProgress% (尝试次数: $attemptCount/$maxAttempts)")
          }
          
          // 检测应用是否安装/更新完成 - 通过版本号变化来判断
          var installDetected = false
          var currentVersionCode: Long? = null
          var currentLastUpdateTime: Long? = null
          
          // 尝试获取当前包的版本号
          try {
            val currentPackageInfo = packageManager.getPackageInfo(packageName, 0)
            currentVersionCode = currentPackageInfo.longVersionCode
            currentLastUpdateTime = currentPackageInfo.lastUpdateTime
            Log.d("KioskManager", "检测到包存在: $packageName, 版本号: $currentVersionCode, 最后更新时间: $currentLastUpdateTime")
          } catch (e: PackageManager.NameNotFoundException) {
            // PackageManager API 检测不到，尝试使用 shell 命令
            try {
              val process = Runtime.getRuntime().exec("pm list packages")
              val reader = BufferedReader(InputStreamReader(process.inputStream))
              var line: String?
              var foundInShell = false
              
              while (reader.readLine().also { line = it } != null) {
                if (line!!.trim().equals("package:$packageName", ignoreCase = true)) {
                  foundInShell = true
                  break
                }
              }
              
              reader.close()
              process.waitFor()
              
              if (foundInShell) {
                // 包在 shell 中能找到，但 PackageManager API 找不到，可能是权限问题
                // 尝试直接获取版本号
                try {
                  val process2 = Runtime.getRuntime().exec("dumpsys package $packageName | grep versionCode")
                  val reader2 = BufferedReader(InputStreamReader(process2.inputStream))
                  val versionLine = reader2.readLine()
                  reader2.close()
                  process2.waitFor()
                  
                  if (versionLine != null && versionLine.contains("versionCode")) {
                    // 提取版本号（简化处理，实际可能需要更复杂的解析）
                    Log.d("KioskManager", "通过 shell 检测到包存在: $packageName")
                    // 如果包在 shell 中存在，我们认为安装可能完成了，但需要谨慎判断
                    // 这里暂时不设置 installDetected，等待后续轮询
                  }
                } catch (e2: Exception) {
                  Log.d("KioskManager", "无法通过 shell 获取版本号: ${e2.message}")
                }
              }
            } catch (e2: Exception) {
              Log.d("KioskManager", "使用 shell 命令检测失败: ${e2.message}")
            }
            
            // 如果包不存在
            if (currentVersionCode == null) {
              if (pendingInstallOldVersionCode == null) {
                if (attemptCount % 10 == 0) {
                  Log.d("KioskManager", "等待应用安装完成: $packageName (尝试次数: $attemptCount/$maxAttempts)")
                }
              } else {
                // 包之前存在但现在不存在，可能安装失败
                Log.w("KioskManager", "警告: 包之前存在但现在不存在，可能安装失败: $packageName")
              }
            }
          } catch (e: Exception) {
            Log.w("KioskManager", "检测安装状态时出现异常: ${e.message}")
          }
          
          // 如果获取到了版本号，判断是否安装完成
          if (currentVersionCode != null) {
            if (pendingInstallOldVersionCode == null) {
              // 首次安装：包不存在 -> 包存在，说明安装成功
              // 但需要确保不是一开始就存在的（至少等待几次检测）
              if (attemptCount >= 3) {
                installDetected = true
                Log.i("KioskManager", "✓ 检测到应用首次安装成功: $packageName, 版本号: $currentVersionCode (尝试次数: $attemptCount)")
              } else {
                Log.d("KioskManager", "等待更多检测次数确认首次安装: $packageName (尝试次数: $attemptCount)")
              }
            } else {
              val oldVersion = pendingInstallOldVersionCode
              val oldLastUpdate = pendingInstallOldLastUpdateTime
              if (oldVersion != null && currentVersionCode != null && currentVersionCode != oldVersion) {
                installDetected = true
                Log.i("KioskManager", "✓ 检测到应用更新成功: $packageName, 旧版本: ${pendingInstallOldVersionCode}, 新版本: $currentVersionCode (尝试次数: $attemptCount)")
              } else if (oldLastUpdate != null && currentLastUpdateTime != null && currentLastUpdateTime > oldLastUpdate) {
                installDetected = true
                Log.i("KioskManager", "✓ 检测到应用重新安装成功（版本号未变化，最后更新时间变化）: $packageName, 上次更新时间: $oldLastUpdate, 当前更新时间: $currentLastUpdateTime (尝试次数: $attemptCount)")
              } else {
                if (attemptCount % 10 == 0) {
                  Log.d("KioskManager", "等待应用更新完成: $packageName (当前版本: $currentVersionCode, 旧版本: ${pendingInstallOldVersionCode}, 当前更新时间: $currentLastUpdateTime, 旧更新时间: $oldLastUpdate, 等待版本或更新时间变化...) (尝试次数: $attemptCount/$maxAttempts)")
                }
              }
            }
          }
          
          // 如果检测到安装/更新完成
          if (installDetected) {
            Log.i("KioskManager", "应用安装/更新完成，准备启动: $packageName")
            // 标记安装完成
            isInstallComplete = true
            pollingRunnable = null
            // 清除旧版本号记录
            pendingInstallOldVersionCode = null
            pendingInstallOldLastUpdateTime = null
            // 发送100%进度和安装成功事件（如果还没有通过接收器发送）
            if (pendingInstallPackageName == packageName) {
              sendInstallStatusEvent("installing", packageName, "安装完成", 100)
              sendInstallStatusEvent("installed", packageName, "安装成功")
            }
            // 延迟1秒启动，给 PackageManager 足够时间刷新缓存
            handler.postDelayed({
              try {
                Log.i("KioskManager", "准备启动应用: $packageName")
                sendInstallStatusEvent("launching", packageName, "正在启动应用")
                appLaunchManager.launchAppInternal(packageName)
              } catch (e: Exception) {
                Log.e("KioskManager", "启动应用失败: ${e.message}", e)
                sendInstallStatusEvent("launch_failed", packageName, "启动失败: ${e.message}")
              }
            }, 1000)
            return
          }
          
          // 如果还未达到最大尝试次数且未完成安装，继续轮询
          if (attemptCount < maxAttempts && !isInstallComplete) {
            pollingRunnable = this
            handler.postDelayed(this, pollInterval)
          } else if (attemptCount >= maxAttempts && !isInstallComplete) {
            Log.w("KioskManager", "安装检测超时，尝试直接启动: $packageName (尝试次数: $attemptCount)")
            isInstallComplete = true
            pollingRunnable = null
            sendInstallStatusEvent("timeout", packageName, "安装检测超时，尝试启动")
            pendingInstallOldVersionCode = null
            pendingInstallOldLastUpdateTime = null
            // 超时后等待2秒再尝试启动，给系统更多时间完成安装
            handler.postDelayed({
              try {
                sendInstallStatusEvent("launching", packageName, "正在启动应用")
                appLaunchManager.launchAppInternal(packageName)
              } catch (e: Exception) {
                Log.e("KioskManager", "最终启动尝试失败: ${e.message}", e)
                sendInstallStatusEvent("launch_failed", packageName, "启动失败: ${e.message}")
              }
            }, 2000)
          }
        } catch (e: Exception) {
          Log.e("KioskManager", "轮询检测安装状态时出错: ${e.message}", e)
          // 即使出错也继续尝试（如果还没完成）
          if (attemptCount < maxAttempts && !isInstallComplete) {
            pollingRunnable = this
            handler.postDelayed(this, pollInterval)
          } else {
            isInstallComplete = true
            pollingRunnable = null
            sendInstallStatusEvent("error", packageName, "检测安装状态时出错: ${e.message}")
            pendingInstallOldVersionCode = null
            pendingInstallOldLastUpdateTime = null
          }
        }
      }
    }
    
    // 保存 runnable 引用
    pollingRunnable = checkRunnable
    // 开始轮询（延迟1秒开始第一次检查，给安装进程一些启动时间）
    handler.postDelayed(checkRunnable, 1000)
  }

  // 委托给 appLaunchManager
  private fun launchAppInternal(packageName: String, retryCount: Int = 0) {
    appLaunchManager.launchAppInternal(packageName, retryCount)
  }

  override fun onCatalystInstanceDestroy() {
    super.onCatalystInstanceDestroy()
    // 清理接收器和观察器
    try {
      if (installReceiver != null) {
        reactApplicationContext.unregisterReceiver(installReceiver)
        installReceiver = null
      }
    } catch (e: Exception) {
      Log.e("KioskManager", "注销安装接收器失败: ${e.message}")
    }
    systemObserver.cleanup()
  }
}