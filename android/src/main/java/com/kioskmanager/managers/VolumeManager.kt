package com.riuhou.kioskmanager.managers

import android.content.Context
import android.media.AudioManager
import android.app.NotificationManager
import android.os.Build
import android.provider.Settings
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import kotlin.math.roundToInt

/**
 * 音量管理器 - 处理所有音量控制相关功能
 */
class VolumeManager(private val reactContext: ReactApplicationContext) {

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

  fun setVolume(stream: String, value: Double, promise: Promise) {
    try {
      val context = reactContext
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

  fun getVolume(stream: String, promise: Promise) {
    try {
      val context = reactContext
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

  fun getMaxVolume(stream: String, promise: Promise) {
    try {
      val context = reactContext
      val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
      val streamType = mapStream(stream)
      val max = am.getStreamMaxVolume(streamType)
      promise.resolve(max)
    } catch (e: Exception) {
      promise.reject("E_VOLUME_FAILED", "Failed to get max volume: ${e.message}")
    }
  }

  fun setGlobalVolume(value: Double, promise: Promise) {
    try {
      val context = reactContext
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

  fun getGlobalVolume(promise: Promise) {
    try {
      val context = reactContext
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

  fun setMute(stream: String, muted: Boolean, promise: Promise) {
    try {
      val context = reactContext
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

  fun isMuted(stream: String, promise: Promise) {
    try {
      val context = reactContext
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

  fun setGlobalMute(muted: Boolean, promise: Promise) {
    try {
      val context = reactContext
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

  fun isGlobalMuted(promise: Promise) {
    try {
      val context = reactContext
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

  fun getRingerMode(promise: Promise) {
    try {
      val context = reactContext
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

  fun setRingerMode(mode: String, promise: Promise) {
    try {
      val context = reactContext
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

  fun hasNotificationPolicyAccess(promise: Promise) {
    try {
      val context = reactContext
      val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) nm.isNotificationPolicyAccessGranted else true
      promise.resolve(granted)
    } catch (e: Exception) {
      promise.reject("E_CHECK_FAILED", "Failed to check notification policy access: ${e.message}")
    }
  }

  fun requestNotificationPolicyAccess(promise: Promise) {
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val activity = reactContext.currentActivity
        if (activity == null) {
          promise.reject("E_NO_ACTIVITY", "No current activity")
          return
        }
        val intent = android.content.Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
        activity.startActivity(intent)
        promise.resolve(true)
      } else {
        promise.resolve(true)
      }
    } catch (e: Exception) {
      promise.reject("E_REQUEST_FAILED", "Failed to request notification policy access: ${e.message}")
    }
  }
}

