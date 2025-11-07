package com.riuhou.kioskmanager

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext

/**
 * 亮度管理器 - 处理系统亮度和应用亮度控制
 */
class BrightnessManager(private val reactContext: ReactApplicationContext) {

  fun hasWriteSettingsPermission(promise: Promise) {
    try {
      val has = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        Settings.System.canWrite(reactContext)
      } else {
        true
      }
      promise.resolve(has)
    } catch (e: Exception) {
      promise.reject("E_CHECK_FAILED", "Failed to check WRITE_SETTINGS: ${e.message}")
    }
  }

  fun requestWriteSettingsPermission(promise: Promise) {
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val activity = reactContext.currentActivity
        if (activity == null) {
          promise.reject("E_NO_ACTIVITY", "No current activity")
          return
        }
        val intent = android.content.Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
        intent.data = android.net.Uri.parse("package:${reactContext.packageName}")
        activity.startActivity(intent)
        promise.resolve(true)
      } else {
        promise.resolve(true)
      }
    } catch (e: Exception) {
      promise.reject("E_REQUEST_FAILED", "Failed to request WRITE_SETTINGS: ${e.message}")
    }
  }

  fun setSystemBrightness(value: Int, promise: Promise) {
    try {
      val context = reactContext
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

  fun getSystemBrightness(promise: Promise) {
    try {
      val context = reactContext
      val current = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 125)
      promise.resolve(current)
    } catch (e: Exception) {
      promise.reject("E_BRIGHTNESS_FAILED", "Failed to get system brightness: ${e.message}")
    }
  }

  fun setAppBrightness(value: Double) {
    val activity = reactContext.currentActivity ?: return
    val clamped = value.coerceIn(0.0, 1.0).toFloat()
    activity.runOnUiThread {
      val params = activity.window.attributes
      params.screenBrightness = clamped
      activity.window.attributes = params
    }
  }

  fun resetAppBrightness() {
    val activity = reactContext.currentActivity ?: return
    activity.runOnUiThread {
      val params = activity.window.attributes
      params.screenBrightness = -1f // 跟随系统亮度
      activity.window.attributes = params
    }
  }

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
}

