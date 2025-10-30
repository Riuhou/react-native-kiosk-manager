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
import kotlin.math.roundToInt

class KioskManagerModule(private val reactContext: ReactApplicationContext) :
  ReactContextBaseJavaModule(reactContext) {

  override fun getName(): String = "KioskManager"

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

  // === 屏幕亮度与音量控制 ===

  @ReactMethod
  fun hasWriteSettingsPermission(promise: Promise) {
    try {
      val has = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        Settings.System.canWrite(reactApplicationContext)
      } else {
        true
      }
      promise.resolve(has)
    } catch (e: Exception) {
      promise.reject("E_CHECK_FAILED", "Failed to check WRITE_SETTINGS: ${e.message}")
    }
  }

  @ReactMethod
  fun requestWriteSettingsPermission(promise: Promise) {
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val activity = reactContext.currentActivity
        if (activity == null) {
          promise.reject("E_NO_ACTIVITY", "No current activity")
          return
        }
        val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
        intent.data = Uri.parse("package:${reactContext.packageName}")
        activity.startActivity(intent)
        promise.resolve(true)
      } else {
        promise.resolve(true)
      }
    } catch (e: Exception) {
      promise.reject("E_REQUEST_FAILED", "Failed to request WRITE_SETTINGS: ${e.message}")
    }
  }

  @ReactMethod
  fun setSystemBrightness(value: Int, promise: Promise) {
    try {
      val context = reactApplicationContext
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.System.canWrite(context)) {
        promise.reject("E_PERMISSION", "WRITE_SETTINGS permission not granted")
        return
      }
      val clamped = value.coerceIn(0, 255)
      val ok = Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, clamped)
      promise.resolve(ok)
    } catch (e: Exception) {
      promise.reject("E_BRIGHTNESS_FAILED", "Failed to set system brightness: ${e.message}")
    }
  }

  @ReactMethod
  fun getSystemBrightness(promise: Promise) {
    try {
      val context = reactApplicationContext
      val current = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 125)
      promise.resolve(current)
    } catch (e: Exception) {
      promise.reject("E_BRIGHTNESS_FAILED", "Failed to get system brightness: ${e.message}")
    }
  }

  @ReactMethod
  fun setAppBrightness(value: Double) {
    val activity = reactContext.currentActivity ?: return
    val clamped = value.coerceIn(0.0, 1.0).toFloat()
    activity.runOnUiThread {
      val params = activity.window.attributes
      params.screenBrightness = clamped
      activity.window.attributes = params
    }
  }

  @ReactMethod
  fun resetAppBrightness() {
    val activity = reactContext.currentActivity ?: return
    activity.runOnUiThread {
      val params = activity.window.attributes
      params.screenBrightness = -1f // 跟随系统亮度
      activity.window.attributes = params
    }
  }

  @ReactMethod
  fun getAppBrightness(promise: Promise) {
    try {
      val activity = reactContext.currentActivity
      if (activity == null) {
        promise.reject("E_NO_ACTIVITY", "No current activity")
        return
      }
      val v = activity.window.attributes.screenBrightness
      // -1 代表遵循系统亮度
      promise.resolve(v.toDouble())
    } catch (e: Exception) {
      promise.reject("E_BRIGHTNESS_FAILED", "Failed to get app brightness: ${e.message}")
    }
  }

  private fun mapStream(stream: String): Int {
    return when (stream.lowercase()) {
      "music" -> AudioManager.STREAM_MUSIC
      "ring" -> AudioManager.STREAM_RING
      "alarm" -> AudioManager.STREAM_ALARM
      "notification" -> AudioManager.STREAM_NOTIFICATION
      "system" -> AudioManager.STREAM_SYSTEM
      "voice_call" -> AudioManager.STREAM_VOICE_CALL
      "dtmf" -> AudioManager.STREAM_DTMF
      else -> AudioManager.STREAM_MUSIC
    }
  }

  @ReactMethod
  fun setVolume(stream: String, value: Double, promise: Promise) {
    try {
      val context = reactApplicationContext
      val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
      val streamType = mapStream(stream)
      val max = am.getStreamMaxVolume(streamType)
      val index = (value.coerceIn(0.0, 1.0) * max).roundToInt()
      am.setStreamVolume(streamType, index, 0)
      promise.resolve(true)
    } catch (e: Exception) {
      promise.reject("E_VOLUME_FAILED", "Failed to set volume: ${e.message}")
    }
  }

  @ReactMethod
  fun getVolume(stream: String, promise: Promise) {
    try {
      val context = reactApplicationContext
      val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
      val streamType = mapStream(stream)
      val current = am.getStreamVolume(streamType)
      val max = am.getStreamMaxVolume(streamType)
      val ratio = if (max > 0) current.toDouble() / max.toDouble() else 0.0
      promise.resolve(ratio)
    } catch (e: Exception) {
      promise.reject("E_VOLUME_FAILED", "Failed to get volume: ${e.message}")
    }
  }

  @ReactMethod
  fun getMaxVolume(stream: String, promise: Promise) {
    try {
      val context = reactApplicationContext
      val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
      val streamType = mapStream(stream)
      val max = am.getStreamMaxVolume(streamType)
      promise.resolve(max)
    } catch (e: Exception) {
      promise.reject("E_VOLUME_FAILED", "Failed to get max volume: ${e.message}")
    }
  }

  @ReactMethod
  fun setGlobalVolume(value: Double, promise: Promise) {
    try {
      val context = reactApplicationContext
      val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
      val clamped = value.coerceIn(0.0, 1.0)
      val streams = listOf(
        AudioManager.STREAM_MUSIC,
        AudioManager.STREAM_RING,
        AudioManager.STREAM_ALARM,
        AudioManager.STREAM_NOTIFICATION,
        AudioManager.STREAM_SYSTEM,
        AudioManager.STREAM_VOICE_CALL,
        AudioManager.STREAM_DTMF
      )
      streams.forEach { st ->
        val max = am.getStreamMaxVolume(st)
        val index = (clamped * max).roundToInt()
        am.setStreamVolume(st, index, 0)
      }
      promise.resolve(true)
    } catch (e: Exception) {
      promise.reject("E_VOLUME_FAILED", "Failed to set global volume: ${e.message}")
    }
  }

  @ReactMethod
  fun getGlobalVolume(promise: Promise) {
    try {
      val context = reactApplicationContext
      val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
      val streams = listOf(
        AudioManager.STREAM_MUSIC,
        AudioManager.STREAM_RING,
        AudioManager.STREAM_ALARM,
        AudioManager.STREAM_NOTIFICATION,
        AudioManager.STREAM_SYSTEM,
        AudioManager.STREAM_VOICE_CALL,
        AudioManager.STREAM_DTMF
      )
      var sum = 0.0
      var count = 0
      streams.forEach { st ->
        val max = am.getStreamMaxVolume(st)
        if (max > 0) {
          val cur = am.getStreamVolume(st)
          sum += cur.toDouble() / max.toDouble()
          count++
        }
      }
      val avg = if (count > 0) sum / count else 0.0
      promise.resolve(avg)
    } catch (e: Exception) {
      promise.reject("E_VOLUME_FAILED", "Failed to get global volume: ${e.message}")
    }
  }

  // === 静音控制 ===
  @ReactMethod
  fun setMute(stream: String, muted: Boolean, promise: Promise) {
    try {
      val context = reactApplicationContext
      val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
      val streamType = mapStream(stream)
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        if (muted) {
          am.adjustStreamVolume(streamType, AudioManager.ADJUST_MUTE, 0)
        } else {
          am.adjustStreamVolume(streamType, AudioManager.ADJUST_UNMUTE, 0)
        }
      } else {
        @Suppress("DEPRECATION")
        am.setStreamMute(streamType, muted)
      }
      promise.resolve(true)
    } catch (e: Exception) {
      promise.reject("E_MUTE_FAILED", "Failed to set mute: ${e.message}")
    }
  }

  @ReactMethod
  fun isMuted(stream: String, promise: Promise) {
    try {
      val context = reactApplicationContext
      val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
      val streamType = mapStream(stream)
      val muted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        am.isStreamMute(streamType)
      } else {
        // 低版本无直接API，使用音量为0作为近似判断
        am.getStreamVolume(streamType) == 0
      }
      promise.resolve(muted)
    } catch (e: Exception) {
      promise.reject("E_MUTE_FAILED", "Failed to get mute state: ${e.message}")
    }
  }

  @ReactMethod
  fun setGlobalMute(muted: Boolean, promise: Promise) {
    try {
      val context = reactApplicationContext
      val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
      val streams = listOf(
        AudioManager.STREAM_MUSIC,
        AudioManager.STREAM_RING,
        AudioManager.STREAM_ALARM,
        AudioManager.STREAM_NOTIFICATION,
        AudioManager.STREAM_SYSTEM,
        AudioManager.STREAM_VOICE_CALL,
        AudioManager.STREAM_DTMF
      )
      streams.forEach { st ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
          if (muted) {
            am.adjustStreamVolume(st, AudioManager.ADJUST_MUTE, 0)
          } else {
            am.adjustStreamVolume(st, AudioManager.ADJUST_UNMUTE, 0)
          }
        } else {
          @Suppress("DEPRECATION")
          am.setStreamMute(st, muted)
        }
      }
      promise.resolve(true)
    } catch (e: Exception) {
      promise.reject("E_MUTE_FAILED", "Failed to set global mute: ${e.message}")
    }
  }

  @ReactMethod
  fun isGlobalMuted(promise: Promise) {
    try {
      val context = reactApplicationContext
      val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
      val streams = listOf(
        AudioManager.STREAM_MUSIC,
        AudioManager.STREAM_RING,
        AudioManager.STREAM_ALARM,
        AudioManager.STREAM_NOTIFICATION,
        AudioManager.STREAM_SYSTEM,
        AudioManager.STREAM_VOICE_CALL,
        AudioManager.STREAM_DTMF
      )
      val allMuted = streams.all { st ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
          am.isStreamMute(st)
        } else {
          am.getStreamVolume(st) == 0
        }
      }
      promise.resolve(allMuted)
    } catch (e: Exception) {
      promise.reject("E_MUTE_FAILED", "Failed to get global mute state: ${e.message}")
    }
  }

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

  @ReactMethod
  fun requestDeviceAdmin(promise: Promise) {
    val activity = reactContext.currentActivity
    if (activity == null) {
      promise.reject("E_NO_ACTIVITY", "No current activity")
      return
    }

    try {
      val context = reactApplicationContext
      val componentName = ComponentName(context, DeviceAdminReceiver::class.java)
      val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
      intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName)
      intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Enable device admin to allow kiosk mode.")

      activity.startActivity(intent)
      promise.resolve(true)
    } catch (e: Exception) {
      promise.reject("E_REQUEST_FAILED", "Failed to request device admin: ${e.message}")
    }
  }

  @ReactMethod
  fun setupLockTaskPackage(promise: Promise) {
    try {
      val context = reactApplicationContext
      val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
      val adminComponent = ComponentName(context, DeviceAdminReceiver::class.java)

      if (dpm.isDeviceOwnerApp(context.packageName)) {
        dpm.setLockTaskPackages(adminComponent, arrayOf(context.packageName))
        promise.resolve(true)
      } else {
        Log.w("KioskManager", "App is not device owner")
        promise.reject("E_NOT_DEVICE_OWNER", "App is not device owner")
      }
    } catch (e: Exception) {
      promise.reject("E_SETUP_FAILED", "Failed to setup lock task package: ${e.message}")
    }
  }

  @ReactMethod
  fun clearDeviceOwner(promise: Promise) {
    try {
      val context = reactApplicationContext
      val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
      val adminComponent = ComponentName(context, DeviceAdminReceiver::class.java)

      if (dpm.isDeviceOwnerApp(context.packageName)) {
        // Clear lock task packages first
        dpm.setLockTaskPackages(adminComponent, arrayOf())
        
        // Remove device owner
        dpm.clearDeviceOwnerApp(context.packageName)
        
        Log.i("KioskManager", "Device owner cleared successfully")
        promise.resolve(true)
      } else {
        Log.w("KioskManager", "App is not device owner")
        promise.reject("E_NOT_DEVICE_OWNER", "App is not device owner")
      }
    } catch (e: Exception) {
      Log.e("KioskManager", "Failed to clear device owner: ${e.message}")
      promise.reject("E_CLEAR_FAILED", "Failed to clear device owner: ${e.message}")
    }
  }

  @ReactMethod
  fun isDeviceOwner(promise: Promise) {
    try {
      val context = reactApplicationContext
      val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
      val isOwner = dpm.isDeviceOwnerApp(context.packageName)
      promise.resolve(isOwner)
    } catch (e: Exception) {
      promise.reject("E_CHECK_FAILED", "Failed to check device owner status: ${e.message}")
    }
  }

  @ReactMethod
  fun downloadApk(url: String, promise: Promise) {
    try {
      val context = reactApplicationContext
      val fileName = "update_${System.currentTimeMillis()}.apk"
      val downloadsDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "apk_updates")
      if (!downloadsDir.exists()) {
        downloadsDir.mkdirs()
      }
      
      val apkFile = File(downloadsDir, fileName)
      
      // 打印下载开始信息
      Log.i("KioskManager", "=== 开始下载 ===")
      Log.i("KioskManager", "下载URL: $url")
      Log.i("KioskManager", "目标文件名: $fileName")
      Log.i("KioskManager", "下载目录: ${downloadsDir.absolutePath}")
      Log.i("KioskManager", "完整路径: ${apkFile.absolutePath}")
      Log.i("KioskManager", "目录存在: ${downloadsDir.exists()}")
      Log.i("KioskManager", "目录可写: ${downloadsDir.canWrite()}")
      Log.i("KioskManager", "==================")
      
      val connection = URL(url).openConnection() as HttpURLConnection
      connection.requestMethod = "GET"
      connection.connect()
      
      if (connection.responseCode != HttpURLConnection.HTTP_OK) {
        promise.reject("E_DOWNLOAD_FAILED", "HTTP error: ${connection.responseCode}")
        return
      }
      
      val inputStream: InputStream = connection.inputStream
      val outputStream = FileOutputStream(apkFile)
      
      val buffer = ByteArray(4096)
      var bytesRead: Int
      var totalBytesRead = 0L
      val contentLength = connection.contentLength.toLong()
      var lastProgressSent = -1
      
      while (inputStream.read(buffer).also { bytesRead = it } != -1) {
        outputStream.write(buffer, 0, bytesRead)
        totalBytesRead += bytesRead
        
        // 发送进度更新（每5%发送一次，避免过于频繁）
        if (contentLength > 0) {
          val progress = (totalBytesRead * 100 / contentLength).toInt()
          if (progress >= lastProgressSent + 5 || progress == 100) {
            lastProgressSent = progress
            sendProgressEvent(progress, totalBytesRead, contentLength)
          }
        }
      }
      
      inputStream.close()
      outputStream.close()
      connection.disconnect()
      
      // 打印下载文件的详细信息
      Log.i("KioskManager", "=== 下载完成 ===")
      Log.i("KioskManager", "文件名: $fileName")
      Log.i("KioskManager", "文件路径: ${apkFile.absolutePath}")
      Log.i("KioskManager", "文件大小: ${apkFile.length()} 字节 (${apkFile.length() / 1024 / 1024} MB)")
      Log.i("KioskManager", "下载目录: ${downloadsDir.absolutePath}")
      Log.i("KioskManager", "文件是否存在: ${apkFile.exists()}")
      Log.i("KioskManager", "文件可读: ${apkFile.canRead()}")
      Log.i("KioskManager", "文件可写: ${apkFile.canWrite()}")
      Log.i("KioskManager", "==================")
      
      val result = Arguments.createMap()
      result.putString("filePath", apkFile.absolutePath)
      result.putString("fileName", fileName)
      result.putLong("fileSize", apkFile.length())
      promise.resolve(result)
      
    } catch (e: Exception) {
      Log.e("KioskManager", "Download failed: ${e.message}")
      promise.reject("E_DOWNLOAD_FAILED", "Download failed: ${e.message}")
    }
  }

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
  fun getDownloadedFiles(promise: Promise) {
    try {
      val context = reactApplicationContext
      val downloadsDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "apk_updates")
      
      if (!downloadsDir.exists()) {
        promise.resolve(Arguments.createArray())
        return
      }
      
      val files = downloadsDir.listFiles()?.filter { it.isFile && it.name.endsWith(".apk") } ?: emptyList()
      val fileList = Arguments.createArray()
      
      files.sortedByDescending { it.lastModified() }.forEach { file ->
        val fileInfo = Arguments.createMap()
        fileInfo.putString("fileName", file.name)
        fileInfo.putString("filePath", file.absolutePath)
        fileInfo.putLong("fileSize", file.length())
        fileInfo.putDouble("lastModified", file.lastModified().toDouble())
        fileInfo.putBoolean("canRead", file.canRead())
        fileInfo.putBoolean("canWrite", file.canWrite())
        fileList.pushMap(fileInfo)
      }
      
      Log.i("KioskManager", "=== 获取下载文件列表 ===")
      Log.i("KioskManager", "下载目录: ${downloadsDir.absolutePath}")
      Log.i("KioskManager", "找到 ${files.size} 个 APK 文件")
      files.forEach { file ->
        Log.i("KioskManager", "文件: ${file.name} (${file.length()} 字节)")
      }
      Log.i("KioskManager", "==================")
      
      promise.resolve(fileList)
    } catch (e: Exception) {
      Log.e("KioskManager", "Failed to get downloaded files: ${e.message}")
      promise.reject("E_GET_FILES_FAILED", "Failed to get downloaded files: ${e.message}")
    }
  }

  @ReactMethod
  fun deleteDownloadedFile(filePath: String, promise: Promise) {
    try {
      val file = File(filePath)
      
      Log.i("KioskManager", "=== 删除文件 ===")
      Log.i("KioskManager", "文件路径: $filePath")
      Log.i("KioskManager", "文件存在: ${file.exists()}")
      Log.i("KioskManager", "文件大小: ${file.length()} 字节")
      Log.i("KioskManager", "==================")
      
      if (!file.exists()) {
        promise.reject("E_FILE_NOT_FOUND", "File not found: $filePath")
        return
      }
      
      if (!file.canWrite()) {
        promise.reject("E_FILE_NOT_WRITABLE", "File is not writable: $filePath")
        return
      }
      
      val deleted = file.delete()
      if (deleted) {
        Log.i("KioskManager", "文件删除成功: $filePath")
        promise.resolve(true)
      } else {
        Log.e("KioskManager", "文件删除失败: $filePath")
        promise.reject("E_DELETE_FAILED", "Failed to delete file: $filePath")
      }
    } catch (e: Exception) {
      Log.e("KioskManager", "Failed to delete file: ${e.message}")
      promise.reject("E_DELETE_FAILED", "Failed to delete file: ${e.message}")
    }
  }

  @ReactMethod
  fun clearAllDownloadedFiles(promise: Promise) {
    try {
      val context = reactApplicationContext
      val downloadsDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "apk_updates")
      
      if (!downloadsDir.exists()) {
        promise.resolve(0)
        return
      }
      
      val files = downloadsDir.listFiles()?.filter { it.isFile && it.name.endsWith(".apk") } ?: emptyList()
      var deletedCount = 0
      
      Log.i("KioskManager", "=== 清空下载文件 ===")
      Log.i("KioskManager", "下载目录: ${downloadsDir.absolutePath}")
      Log.i("KioskManager", "找到 ${files.size} 个 APK 文件")
      
      files.forEach { file ->
        if (file.delete()) {
          deletedCount++
          Log.i("KioskManager", "已删除: ${file.name}")
        } else {
          Log.e("KioskManager", "删除失败: ${file.name}")
        }
      }
      
      Log.i("KioskManager", "成功删除 $deletedCount 个文件")
      Log.i("KioskManager", "==================")
      
      promise.resolve(deletedCount)
    } catch (e: Exception) {
      Log.e("KioskManager", "Failed to clear downloaded files: ${e.message}")
      promise.reject("E_CLEAR_FILES_FAILED", "Failed to clear downloaded files: ${e.message}")
    }
  }

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
      val pendingIntent = android.app.PendingIntent.getBroadcast(
        context, 
        sessionId, 
        Intent("com.kioskmanager.INSTALL_COMPLETE"), 
        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
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
            silentInstallApk(filePath, promise)
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
      Log.i("KioskManager", "目标包名: $targetPackageName")
      
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
      val pendingIntent = android.app.PendingIntent.getBroadcast(
        context, 
        sessionId, 
        Intent("com.kioskmanager.INSTALL_COMPLETE"), 
        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
      )
      
      // 提交安装
      session.commit(pendingIntent.intentSender)
      
      Log.i("KioskManager", "=== 静默安装提交成功 ===")
      Log.i("KioskManager", "安装会话ID: $sessionId")
      Log.i("KioskManager", "目标包名: $targetPackageName")
      Log.i("KioskManager", "==================")
      
      // 延迟启动应用（等待安装完成）
      android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
        try {
          launchApp(targetPackageName)
        } catch (e: Exception) {
          Log.e("KioskManager", "Failed to launch app after install: ${e.message}")
        }
      }, 3000) // 等待3秒后启动应用
      
      promise.resolve(true)
      session.close()
      
    } catch (e: Exception) {
      Log.e("KioskManager", "Silent install and launch failed: ${e.message}")
      promise.reject("E_SILENT_INSTALL_FAILED", "Silent install and launch failed: ${e.message}")
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
      
      // 使用设备策略管理器进行系统级安装
      try {
        val packageInfo = context.packageManager.getPackageArchiveInfo(filePath, 0)
        if (packageInfo != null) {
          val targetPackageName = packageInfo.packageName
          Log.i("KioskManager", "目标包名: $targetPackageName")
          
          // 使用设备策略管理器安装应用
          val adminComponent = ComponentName(context, DeviceAdminReceiver::class.java)
          val apkUri = Uri.fromFile(apkFile)
          val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
          
          // 使用反射调用installSystemUpdate方法，因为参数可能在不同Android版本中不同
          try {
            val method = dpm.javaClass.getMethod("installSystemUpdate", 
              ComponentName::class.java, Uri::class.java, java.util.concurrent.Executor::class.java)
            method.invoke(dpm, adminComponent, apkUri, executor)
          } catch (e: Exception) {
            Log.w("KioskManager", "installSystemUpdate not available: ${e.message}")
            // 如果系统级安装不可用，回退到普通静默安装
            promise.reject("E_SYSTEM_INSTALL_NOT_AVAILABLE", "System install not available: ${e.message}")
            return
          }
          
          Log.i("KioskManager", "=== 系统级静默安装提交成功 ===")
          Log.i("KioskManager", "目标包名: $targetPackageName")
          Log.i("KioskManager", "==================")
          
          promise.resolve(true)
        } else {
          promise.reject("E_INVALID_APK", "Invalid APK file")
        }
      } catch (e: Exception) {
        Log.e("KioskManager", "System silent install failed: ${e.message}")
        promise.reject("E_SYSTEM_INSTALL_FAILED", "System silent install failed: ${e.message}")
      }
      
    } catch (e: Exception) {
      Log.e("KioskManager", "System silent install failed: ${e.message}")
      promise.reject("E_SYSTEM_INSTALL_FAILED", "System silent install failed: ${e.message}")
    }
  }

  private fun launchApp(packageName: String) {
    try {
      val context = reactApplicationContext
      val packageManager = context.packageManager
      
      // 检查应用是否已安装
      try {
        packageManager.getPackageInfo(packageName, 0)
      } catch (e: PackageManager.NameNotFoundException) {
        Log.e("KioskManager", "App not found: $packageName")
        return
      }
      
      // 获取应用的主Activity
      val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
      if (launchIntent != null) {
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        
        context.startActivity(launchIntent)
        
        Log.i("KioskManager", "=== 应用启动成功 ===")
        Log.i("KioskManager", "包名: $packageName")
        Log.i("KioskManager", "==================")
      } else {
        Log.e("KioskManager", "No launch intent found for package: $packageName")
      }
    } catch (e: Exception) {
      Log.e("KioskManager", "Failed to launch app: ${e.message}")
    }
  }
}