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

  // Observers & Receivers
  private var brightnessObserver: ContentObserver? = null
  private var volumeReceiver: BroadcastReceiver? = null
  private var ringerReceiver: BroadcastReceiver? = null
  private var installReceiver: BroadcastReceiver? = null
  private var isObservingAv: Boolean = false
  private var pendingInstallPackageName: String? = null
  private var pendingInstallSessionId: Int? = null
  private var pollingRunnable: Runnable? = null
  private var isInstallComplete: Boolean = false
  private var pendingInstallOldVersionCode: Long? = null // 安装前的版本号

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

  // === 系统铃声模式（类似点击系统音量图标） ===
  @ReactMethod
  fun getRingerMode(promise: Promise) {
    try {
      val context = reactApplicationContext
      val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
      val mode = am.ringerMode // 0: silent, 1: vibrate, 2: normal
      val modeStr = when (mode) {
        AudioManager.RINGER_MODE_SILENT -> "silent"
        AudioManager.RINGER_MODE_VIBRATE -> "vibrate"
        else -> "normal"
      }
      promise.resolve(modeStr)
    } catch (e: Exception) {
      promise.reject("E_RINGER_FAILED", "Failed to get ringer mode: ${e.message}")
    }
  }

  // === 系统亮度与音量变更监听 ===
  @ReactMethod
  fun startObservingSystemAv() {
    if (isObservingAv) return
    isObservingAv = true

    val context = reactApplicationContext
    // Brightness observer
    val handler = Handler(Looper.getMainLooper())
    brightnessObserver = object : ContentObserver(handler) {
      override fun onChange(selfChange: Boolean) {
        try {
          val current = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 125)
          val map = Arguments.createMap()
          map.putInt("brightness", current)
          reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
            .emit("KioskManagerSystemBrightnessChanged", map)
        } catch (_: Exception) {}
      }
    }
    try {
      context.contentResolver.registerContentObserver(
        Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS),
        false,
        brightnessObserver as ContentObserver
      )
    } catch (_: Exception) {}

    // Volume receiver
    volumeReceiver = object : BroadcastReceiver() {
      override fun onReceive(ctx: Context?, intent: Intent?) {
        if (intent == null) return
        if (intent.action == "android.media.VOLUME_CHANGED_ACTION") {
          try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val streamType = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", AudioManager.STREAM_MUSIC)
            val index = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_VALUE", am.getStreamVolume(streamType))
            val max = am.getStreamMaxVolume(streamType)
            val ratio = if (max > 0) index.toDouble() / max.toDouble() else 0.0

            val streamName = when (streamType) {
              AudioManager.STREAM_MUSIC -> "music"
              AudioManager.STREAM_RING -> "ring"
              AudioManager.STREAM_ALARM -> "alarm"
              AudioManager.STREAM_NOTIFICATION -> "notification"
              AudioManager.STREAM_SYSTEM -> "system"
              AudioManager.STREAM_VOICE_CALL -> "voice_call"
              AudioManager.STREAM_DTMF -> "dtmf"
              else -> "music"
            }

            val payload = Arguments.createMap()
            payload.putString("stream", streamName)
            payload.putInt("index", index)
            payload.putInt("max", max)
            payload.putDouble("value", ratio)
            reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
              .emit("KioskManagerVolumeChanged", payload)

            // Also emit global volume average
            try {
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
                val m = am.getStreamMaxVolume(st)
                if (m > 0) {
                  val cur = am.getStreamVolume(st)
                  sum += cur.toDouble() / m.toDouble()
                  count++
                }
              }
              val avg = if (count > 0) sum / count else 0.0
              val g = Arguments.createMap()
              g.putDouble("value", avg)
              reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                .emit("KioskManagerGlobalVolumeChanged", g)
            } catch (_: Exception) {}
          } catch (_: Exception) {}
        }
      }
    }
    try {
      context.registerReceiver(volumeReceiver, IntentFilter("android.media.VOLUME_CHANGED_ACTION"))
    } catch (_: Exception) {}

    // Ringer mode receiver
    ringerReceiver = object : BroadcastReceiver() {
      override fun onReceive(ctx: Context?, intent: Intent?) {
        if (intent?.action == AudioManager.RINGER_MODE_CHANGED_ACTION) {
          try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val mode = am.ringerMode
            val modeStr = when (mode) {
              AudioManager.RINGER_MODE_SILENT -> "silent"
              AudioManager.RINGER_MODE_VIBRATE -> "vibrate"
              else -> "normal"
            }
            val m = Arguments.createMap()
            m.putString("mode", modeStr)
            reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
              .emit("KioskManagerRingerModeChanged", m)
          } catch (_: Exception) {}
        }
      }
    }
    try {
      context.registerReceiver(ringerReceiver, IntentFilter(AudioManager.RINGER_MODE_CHANGED_ACTION))
    } catch (_: Exception) {}
  }

  @ReactMethod
  fun stopObservingSystemAv() {
    val context = reactApplicationContext
    try {
      if (brightnessObserver != null) {
        context.contentResolver.unregisterContentObserver(brightnessObserver as ContentObserver)
        brightnessObserver = null
      }
    } catch (_: Exception) {}
    try {
      if (volumeReceiver != null) {
        context.unregisterReceiver(volumeReceiver)
        volumeReceiver = null
      }
    } catch (_: Exception) {}
    try {
      if (ringerReceiver != null) {
        context.unregisterReceiver(ringerReceiver)
        ringerReceiver = null
      }
    } catch (_: Exception) {}
    isObservingAv = false
  }

  @ReactMethod
  fun setRingerMode(mode: String, promise: Promise) {
    try {
      val context = reactApplicationContext
      val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
      val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

      // Android 6.0+ 某些机型在切换到静音/震动时需要免打扰权限
      val target = when (mode.lowercase()) {
        "silent" -> AudioManager.RINGER_MODE_SILENT
        "vibrate" -> AudioManager.RINGER_MODE_VIBRATE
        else -> AudioManager.RINGER_MODE_NORMAL
      }

      if ((target == AudioManager.RINGER_MODE_SILENT || target == AudioManager.RINGER_MODE_VIBRATE)
        && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
        && !nm.isNotificationPolicyAccessGranted) {
        promise.reject("E_PERMISSION", "Notification policy access not granted")
        return
      }

      am.ringerMode = target
      promise.resolve(true)
    } catch (e: Exception) {
      promise.reject("E_RINGER_FAILED", "Failed to set ringer mode: ${e.message}")
    }
  }

  @ReactMethod
  fun hasNotificationPolicyAccess(promise: Promise) {
    try {
      val context = reactApplicationContext
      val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) nm.isNotificationPolicyAccessGranted else true
      promise.resolve(granted)
    } catch (e: Exception) {
      promise.reject("E_CHECK_FAILED", "Failed to check notification policy access: ${e.message}")
    }
  }

  @ReactMethod
  fun requestNotificationPolicyAccess(promise: Promise) {
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val activity = reactContext.currentActivity
        if (activity == null) {
          promise.reject("E_NO_ACTIVITY", "No current activity")
          return
        }
        val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
        activity.startActivity(intent)
        promise.resolve(true)
      } else {
        promise.resolve(true)
      }
    } catch (e: Exception) {
      promise.reject("E_REQUEST_FAILED", "Failed to request notification policy access: ${e.message}")
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
      
      // 记录安装前的版本号（如果包已存在）
      try {
        val existingPackageInfo = context.packageManager.getPackageInfo(targetPackageName, 0)
        pendingInstallOldVersionCode = existingPackageInfo.longVersionCode
        Log.i("KioskManager", "检测到包已存在，当前版本号: ${pendingInstallOldVersionCode}")
      } catch (e: PackageManager.NameNotFoundException) {
        pendingInstallOldVersionCode = null
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
      val pendingIntent = android.app.PendingIntent.getBroadcast(
        context, 
        sessionId, 
        Intent("com.kioskmanager.INSTALL_COMPLETE"), 
        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
      )
      
      // 保存待安装的包名和会话ID，用于接收器
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
      if (action == "com.kioskmanager.INSTALL_COMPLETE") {
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
                // 如果这是我们正在等待的包，启动应用
                // 延迟1秒启动，给 PackageManager 足够时间刷新缓存
                if (finalPackageName == pendingInstallPackageName) {
                  Handler(Looper.getMainLooper()).postDelayed({
                    try {
                      sendInstallStatusEvent("launching", finalPackageName, "正在启动应用")
                      launchAppInternal(finalPackageName)
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
            }
            PackageInstaller.STATUS_FAILURE_ABORTED -> {
              val finalPackageName = packageName ?: pendingInstallPackageName
              Log.e("KioskManager", "安装已取消: $finalPackageName")
              sendInstallStatusEvent("cancelled", finalPackageName, "安装已取消")
            }
            PackageInstaller.STATUS_FAILURE_BLOCKED -> {
              val finalPackageName = packageName ?: pendingInstallPackageName
              Log.e("KioskManager", "安装被阻止: $finalPackageName")
              sendInstallStatusEvent("blocked", finalPackageName, "安装被阻止")
            }
            PackageInstaller.STATUS_FAILURE_CONFLICT -> {
              val finalPackageName = packageName ?: pendingInstallPackageName
              Log.e("KioskManager", "安装冲突: $finalPackageName")
              sendInstallStatusEvent("conflict", finalPackageName, "安装冲突")
            }
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> {
              val finalPackageName = packageName ?: pendingInstallPackageName
              Log.e("KioskManager", "应用不兼容: $finalPackageName")
              sendInstallStatusEvent("incompatible", finalPackageName, "应用不兼容")
            }
            PackageInstaller.STATUS_FAILURE_INVALID -> {
              val finalPackageName = packageName ?: pendingInstallPackageName
              Log.e("KioskManager", "无效的APK: $finalPackageName")
              sendInstallStatusEvent("invalid", finalPackageName, "无效的APK")
            }
            PackageInstaller.STATUS_FAILURE_STORAGE -> {
              val finalPackageName = packageName ?: pendingInstallPackageName
              Log.e("KioskManager", "存储空间不足: $finalPackageName")
              sendInstallStatusEvent("storage_error", finalPackageName, "存储空间不足")
            }
            else -> {
              val finalPackageName = packageName ?: pendingInstallPackageName
              Log.w("KioskManager", "未知安装状态: $status, packageName: $finalPackageName")
              sendInstallStatusEvent("unknown", finalPackageName, "未知状态: $status")
            }
          }
        }
      }
    }
    
    try {
      val filter = IntentFilter("com.kioskmanager.INSTALL_COMPLETE")
      context.registerReceiver(installReceiver, filter)
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
      val pendingIntent = android.app.PendingIntent.getBroadcast(
        context, 
        sessionId, 
        Intent("com.kioskmanager.INSTALL_COMPLETE"), 
        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
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

  @ReactMethod
  fun isAppInstalled(packageName: String, promise: Promise) {
    try {
      val context = reactApplicationContext
      val packageManager = context.packageManager
      
      // 方式1: 先尝试使用 PackageManager API
      var isInstalled = false
      try {
        val appInfo = packageManager.getApplicationInfo(packageName, 0)
        isInstalled = true
        Log.i("KioskManager", "✓ 通过 getApplicationInfo 检测到应用已安装: $packageName")
        Log.i("KioskManager", "包信息: enabled=${appInfo.enabled}")
      } catch (e: PackageManager.NameNotFoundException) {
        Log.i("KioskManager", "✗ 通过 getApplicationInfo 未检测到应用: $packageName")
      } catch (e: Exception) {
        Log.w("KioskManager", "getApplicationInfo 检查时出现异常: ${e.message}")
      }
      
      // 方式2: 如果 PackageManager API 找不到，使用 shell 命令检测（更可靠）
      if (!isInstalled) {
        try {
          val process = Runtime.getRuntime().exec("pm list packages")
          val reader = BufferedReader(InputStreamReader(process.inputStream))
          var line: String?
          
          while (reader.readLine().also { line = it } != null) {
            // pm list packages 输出格式: package:com.example.app
            if (line!!.trim().equals("package:$packageName", ignoreCase = true)) {
              isInstalled = true
              Log.i("KioskManager", "✓ 通过 pm list packages 检测到应用已安装: $packageName")
              break
            }
          }
          
          reader.close()
          process.waitFor()
          
          if (!isInstalled) {
            Log.i("KioskManager", "✗ 通过 pm list packages 也未检测到应用: $packageName")
          }
        } catch (e: Exception) {
          Log.w("KioskManager", "使用 shell 命令检测时出现异常: ${e.message}")
        }
      }
      
      promise.resolve(isInstalled)
    } catch (e: Exception) {
      Log.e("KioskManager", "检查应用安装状态失败: ${e.message}", e)
      promise.reject("E_CHECK_FAILED", "Failed to check if app is installed: ${e.message}")
    }
  }

  @ReactMethod
  fun launchApp(packageName: String, promise: Promise) {
    try {
      val context = reactApplicationContext
      val packageManager = context.packageManager
      
      Log.i("KioskManager", "=== 开始启动应用 ===")
      Log.i("KioskManager", "包名: $packageName")
      
      // 检查应用是否已安装 - 使用多种方式尝试
      var isInstalled = false
      try {
        // 方式1: 使用 GET_ACTIVITIES flag
        packageManager.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES)
        isInstalled = true
        Log.i("KioskManager", "应用已安装 (方式1)")
      } catch (e: PackageManager.NameNotFoundException) {
        try {
          // 方式2: 使用 0 flag
          packageManager.getPackageInfo(packageName, 0)
          isInstalled = true
          Log.i("KioskManager", "应用已安装 (方式2)")
        } catch (e2: PackageManager.NameNotFoundException) {
          // 方式3: 尝试获取启动意图（即使没有包信息，也可能有启动意图）
          val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
          if (launchIntent != null) {
            isInstalled = true
            Log.i("KioskManager", "应用已安装 (方式3: 通过启动意图检测)")
          } else {
            Log.e("KioskManager", "应用未安装: $packageName")
            Log.e("KioskManager", "所有检查方式都失败")
            promise.reject("E_APP_NOT_FOUND", "App not found: $packageName")
            return
          }
        }
      }
      
      if (!isInstalled) {
        Log.e("KioskManager", "应用未安装: $packageName")
        promise.reject("E_APP_NOT_FOUND", "App not found: $packageName")
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
        
        promise.resolve(true)
      } else {
        Log.e("KioskManager", "No launch intent found for package: $packageName")
        promise.reject("E_NO_LAUNCH_INTENT", "No launch intent found for package: $packageName")
      }
    } catch (e: Exception) {
      Log.e("KioskManager", "Failed to launch app: ${e.message}", e)
      promise.reject("E_LAUNCH_FAILED", "Failed to launch app: ${e.message}")
    }
  }

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
          
          // 尝试获取当前包的版本号
          try {
            val currentPackageInfo = packageManager.getPackageInfo(packageName, 0)
            currentVersionCode = currentPackageInfo.longVersionCode
            Log.d("KioskManager", "检测到包存在: $packageName, 版本号: $currentVersionCode")
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
              // 更新安装：版本号变化，说明更新成功
              if (currentVersionCode != pendingInstallOldVersionCode) {
                installDetected = true
                Log.i("KioskManager", "✓ 检测到应用更新成功: $packageName, 旧版本: ${pendingInstallOldVersionCode}, 新版本: $currentVersionCode (尝试次数: $attemptCount)")
              } else {
                if (attemptCount % 10 == 0) {
                  Log.d("KioskManager", "等待应用更新完成: $packageName (当前版本: $currentVersionCode, 旧版本: ${pendingInstallOldVersionCode}, 等待版本变化...) (尝试次数: $attemptCount/$maxAttempts)")
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
                launchAppInternal(packageName)
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
            // 超时后等待2秒再尝试启动，给系统更多时间完成安装
            handler.postDelayed({
              try {
                sendInstallStatusEvent("launching", packageName, "正在启动应用")
                launchAppInternal(packageName)
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
          }
        }
      }
    }
    
    // 保存 runnable 引用
    pollingRunnable = checkRunnable
    // 开始轮询（延迟1秒开始第一次检查，给安装进程一些启动时间）
    handler.postDelayed(checkRunnable, 1000)
  }

  private fun launchAppInternal(packageName: String, retryCount: Int = 0) {
    try {
      val context = reactApplicationContext
      val packageManager = context.packageManager
      val maxRetries = 20 // 最多重试20次
      val retryDelay = if (retryCount < 5) 1000L else 2000L // 前5次1秒，之后2秒
      
      Log.i("KioskManager", "尝试启动应用: $packageName (重试次数: $retryCount)")
      
      // 检测应用是否已安装 - 使用 getApplicationInfo 和 shell 命令
      var isInstalled = false
      try {
        packageManager.getApplicationInfo(packageName, 0)
        isInstalled = true
        Log.i("KioskManager", "✓ 检测到应用已安装: $packageName")
      } catch (e: PackageManager.NameNotFoundException) {
        // 如果 PackageManager API 找不到，尝试使用 shell 命令
        try {
          val process = Runtime.getRuntime().exec("pm list packages")
          val reader = BufferedReader(InputStreamReader(process.inputStream))
          var line: String?
          
          while (reader.readLine().also { line = it } != null) {
            if (line!!.trim().equals("package:$packageName", ignoreCase = true)) {
              isInstalled = true
              Log.i("KioskManager", "✓ 通过 pm list packages 检测到应用已安装: $packageName")
              break
            }
          }
          
          reader.close()
          process.waitFor()
          
          if (!isInstalled) {
            Log.w("KioskManager", "✗ 应用未找到: $packageName")
          }
        } catch (e2: Exception) {
          Log.w("KioskManager", "✗ 应用未找到: $packageName")
        }
      } catch (e: Exception) {
        Log.w("KioskManager", "检查应用安装状态时出现异常: ${e.message}")
      }
      
      // 获取启动意图
      val launchIntent = if (isInstalled) {
        packageManager.getLaunchIntentForPackage(packageName)
      } else {
        null
      }
      
      // 如果应用未安装且还有重试次数，延迟后重试
      if (!isInstalled && retryCount < maxRetries) {
        Log.w("KioskManager", "应用未找到，${retryDelay}ms 后重试 (${retryCount + 1}/$maxRetries): $packageName")
        Handler(Looper.getMainLooper()).postDelayed({
          launchAppInternal(packageName, retryCount + 1)
        }, retryDelay)
        return
      }
      
      // 如果重试次数用完还是找不到，报告错误
      if (!isInstalled) {
        Log.e("KioskManager", "应用未找到，已达到最大重试次数 ($maxRetries): $packageName")
        sendInstallStatusEvent("launch_failed", packageName, "应用未找到，可能安装失败")
        return
      }
      
      // 启动应用
      if (launchIntent != null) {
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        
        context.startActivity(launchIntent)
        
        Log.i("KioskManager", "=== 应用启动成功 ===")
        Log.i("KioskManager", "包名: $packageName")
        Log.i("KioskManager", "重试次数: $retryCount")
        Log.i("KioskManager", "==================")
        
        // 发送启动成功事件
        sendInstallStatusEvent("launched", packageName, "应用启动成功")
        
                // 清除待安装信息
                if (pendingInstallPackageName == packageName) {
                  pendingInstallPackageName = null
                  pendingInstallSessionId = null
                  pendingInstallOldVersionCode = null
                }
      } else {
        Log.e("KioskManager", "No launch intent found for package: $packageName")
        sendInstallStatusEvent("launch_failed", packageName, "找不到启动意图")
      }
    } catch (e: Exception) {
      Log.e("KioskManager", "Failed to launch app: ${e.message}", e)
      sendInstallStatusEvent("launch_failed", packageName, "启动失败: ${e.message}")
    }
  }

  override fun onCatalystInstanceDestroy() {
    super.onCatalystInstanceDestroy()
    // 清理接收器
    try {
      if (installReceiver != null) {
        reactApplicationContext.unregisterReceiver(installReceiver)
        installReceiver = null
      }
    } catch (e: Exception) {
      Log.e("KioskManager", "注销安装接收器失败: ${e.message}")
    }
  }
}