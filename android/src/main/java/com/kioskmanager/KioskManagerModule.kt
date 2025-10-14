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
        intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
      }
      
      context.startActivity(intent)
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
}