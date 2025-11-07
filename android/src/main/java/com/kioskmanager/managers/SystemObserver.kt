package com.riuhou.kioskmanager.managers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.modules.core.DeviceEventManagerModule
import kotlin.math.roundToInt

/**
 * 系统观察器 - 处理系统亮度和音量变更监听
 */
class SystemObserver(private val reactContext: ReactApplicationContext) {

  private var brightnessObserver: ContentObserver? = null
  private var volumeReceiver: BroadcastReceiver? = null
  private var ringerReceiver: BroadcastReceiver? = null
  private var isObservingAv: Boolean = false

  fun startObservingSystemAv() {
    if (isObservingAv) return
    isObservingAv = true

    val context = reactContext
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

  fun stopObservingSystemAv() {
    val context = reactContext
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

  fun cleanup() {
    stopObservingSystemAv()
  }
}

