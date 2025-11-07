package com.riuhou.kioskmanager.managers

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.riuhou.kioskmanager.DeviceAdminReceiver
import java.util.Calendar

/**
 * 定时开关机管理器 - 处理定时开关机功能
 * 
 * 注意：
 * - 定时关机需要设备所有者权限
 * - 定时开机功能受限于 Android 系统，某些设备可能不支持
 */
class PowerScheduleManager(private val reactContext: ReactApplicationContext) {

  companion object {
    private const val TAG = "PowerScheduleManager"
    private const val PREFS_NAME = "power_schedule_prefs"
    private const val KEY_SHUTDOWN_ENABLED = "shutdown_enabled"
    private const val KEY_SHUTDOWN_HOUR = "shutdown_hour"
    private const val KEY_SHUTDOWN_MINUTE = "shutdown_minute"
    private const val KEY_SHUTDOWN_REPEAT = "shutdown_repeat"
    private const val KEY_BOOT_ENABLED = "boot_enabled"
    private const val KEY_BOOT_HOUR = "boot_hour"
    private const val KEY_BOOT_MINUTE = "boot_minute"
    private const val KEY_BOOT_REPEAT = "boot_repeat"
    private const val ACTION_SCHEDULED_SHUTDOWN = "com.riuhou.kioskmanager.SCHEDULED_SHUTDOWN"
    private const val ACTION_SCHEDULED_BOOT = "com.riuhou.kioskmanager.SCHEDULED_BOOT"
  }

  private val prefs: SharedPreferences
    get() = reactContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  /**
   * 检查是否为设备所有者
   */
  private fun isDeviceOwner(): Boolean {
    return try {
      val dpm = reactContext.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
      dpm.isDeviceOwnerApp(reactContext.packageName)
    } catch (e: Exception) {
      Log.e(TAG, "检查设备所有者权限失败: ${e.message}")
      false
    }
  }

  /**
   * 设置定时关机
   * 
   * @param hour 小时 (0-23)
   * @param minute 分钟 (0-59)
   * @param repeat 是否每天重复
   */
  fun setScheduledShutdown(hour: Int, minute: Int, repeat: Boolean, promise: Promise) {
    try {
      if (!isDeviceOwner()) {
        promise.reject("E_NOT_DEVICE_OWNER", "需要设备所有者权限才能设置定时关机")
        return
      }

      // 验证时间参数
      if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
        promise.reject("E_INVALID_TIME", "时间参数无效：小时(0-23)，分钟(0-59)")
        return
      }

      // 保存设置
      prefs.edit().apply {
        putBoolean(KEY_SHUTDOWN_ENABLED, true)
        putInt(KEY_SHUTDOWN_HOUR, hour)
        putInt(KEY_SHUTDOWN_MINUTE, minute)
        putBoolean(KEY_SHUTDOWN_REPEAT, repeat)
        apply()
      }

      // 设置 AlarmManager
      val alarmManager = reactContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
      val intent = Intent(ACTION_SCHEDULED_SHUTDOWN).apply {
        setPackage(reactContext.packageName)
      }
      
      val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
      } else {
        PendingIntent.FLAG_UPDATE_CURRENT
      }
      
      val pendingIntent = PendingIntent.getBroadcast(
        reactContext,
        0,
        intent,
        pendingIntentFlags
      )

      // 计算目标时间
      val calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        
        // 如果设置的时间已过，设置为明天
        if (timeInMillis <= System.currentTimeMillis()) {
          add(Calendar.DAY_OF_YEAR, 1)
        }
      }

      // 设置闹钟
      if (repeat) {
        // 每天重复
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
          alarmManager.setRepeating(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            AlarmManager.INTERVAL_DAY,
            pendingIntent
          )
        } else {
          @Suppress("DEPRECATION")
          alarmManager.setRepeating(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            AlarmManager.INTERVAL_DAY,
            pendingIntent
          )
        }
      } else {
        // 单次执行
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
          alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            pendingIntent
          )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
          alarmManager.setExact(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
        } else {
          @Suppress("DEPRECATION")
          alarmManager.set(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
        }
      }

      Log.i(TAG, "定时关机设置成功: ${String.format("%02d:%02d", hour, minute)}, 重复: $repeat")
      promise.resolve(true)
    } catch (e: Exception) {
      Log.e(TAG, "设置定时关机失败: ${e.message}", e)
      promise.reject("E_SET_SHUTDOWN_FAILED", "设置定时关机失败: ${e.message}")
    }
  }

  /**
   * 取消定时关机
   */
  fun cancelScheduledShutdown(promise: Promise) {
    try {
      val alarmManager = reactContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
      val intent = Intent(ACTION_SCHEDULED_SHUTDOWN).apply {
        setPackage(reactContext.packageName)
      }
      
      val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
      } else {
        PendingIntent.FLAG_UPDATE_CURRENT
      }
      
      val pendingIntent = PendingIntent.getBroadcast(
        reactContext,
        0,
        intent,
        pendingIntentFlags
      )

      alarmManager.cancel(pendingIntent)
      
      // 清除设置
      prefs.edit().putBoolean(KEY_SHUTDOWN_ENABLED, false).apply()

      Log.i(TAG, "定时关机已取消")
      promise.resolve(true)
    } catch (e: Exception) {
      Log.e(TAG, "取消定时关机失败: ${e.message}", e)
      promise.reject("E_CANCEL_SHUTDOWN_FAILED", "取消定时关机失败: ${e.message}")
    }
  }

  /**
   * 获取定时关机设置
   */
  fun getScheduledShutdown(promise: Promise) {
    try {
      val enabled = prefs.getBoolean(KEY_SHUTDOWN_ENABLED, false)
      if (!enabled) {
        promise.resolve(null)
        return
      }

      val hour = prefs.getInt(KEY_SHUTDOWN_HOUR, 0)
      val minute = prefs.getInt(KEY_SHUTDOWN_MINUTE, 0)
      val repeat = prefs.getBoolean(KEY_SHUTDOWN_REPEAT, false)

      val result = com.facebook.react.bridge.Arguments.createMap().apply {
        putBoolean("enabled", true)
        putInt("hour", hour)
        putInt("minute", minute)
        putBoolean("repeat", repeat)
      }

      promise.resolve(result)
    } catch (e: Exception) {
      Log.e(TAG, "获取定时关机设置失败: ${e.message}", e)
      promise.reject("E_GET_SHUTDOWN_FAILED", "获取定时关机设置失败: ${e.message}")
    }
  }

  /**
   * 执行关机操作
   * 注意：此方法需要设备所有者权限
   */
  fun performShutdown(promise: Promise) {
    try {
      if (!isDeviceOwner()) {
        promise.reject("E_NOT_DEVICE_OWNER", "需要设备所有者权限才能执行关机")
        return
      }

      val dpm = reactContext.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
      
      // 使用 DevicePolicyManager 的 reboot 方法，传入 "shutdown" 作为原因
      // 注意：某些设备可能不支持直接关机，只能重启
      try {
        // 尝试使用反射调用 reboot 方法（如果可用）
        val method = dpm.javaClass.getMethod("reboot", ComponentName::class.java, String::class.java)
        val adminComponent = ComponentName(reactContext, DeviceAdminReceiver::class.java)
        method.invoke(dpm, adminComponent, "shutdown")
        Log.i(TAG, "关机命令已执行")
        promise.resolve(true)
      } catch (e: NoSuchMethodException) {
        // 如果 reboot 方法不可用，尝试使用 shell 命令
        Log.w(TAG, "DevicePolicyManager.reboot 方法不可用，尝试使用 shell 命令")
        try {
          val process = Runtime.getRuntime().exec("su -c reboot -p")
          process.waitFor()
          Log.i(TAG, "通过 shell 命令执行关机")
          promise.resolve(true)
        } catch (e2: Exception) {
          Log.e(TAG, "执行关机失败: ${e2.message}", e2)
          promise.reject("E_SHUTDOWN_FAILED", "执行关机失败: ${e2.message}")
        }
      }
    } catch (e: Exception) {
      Log.e(TAG, "执行关机失败: ${e.message}", e)
      promise.reject("E_SHUTDOWN_FAILED", "执行关机失败: ${e.message}")
    }
  }

  /**
   * 设置定时开机
   * 
   * 注意：Android 系统本身不支持定时开机，此功能依赖于设备硬件支持。
   * 某些厂商的 ROM 可能支持此功能。
   * 
   * @param hour 小时 (0-23)
   * @param minute 分钟 (0-59)
   * @param repeat 是否每天重复
   */
  fun setScheduledBoot(hour: Int, minute: Int, repeat: Boolean, promise: Promise) {
    try {
      // 验证时间参数
      if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
        promise.reject("E_INVALID_TIME", "时间参数无效：小时(0-23)，分钟(0-59)")
        return
      }

      // 保存设置
      prefs.edit().apply {
        putBoolean(KEY_BOOT_ENABLED, true)
        putInt(KEY_BOOT_HOUR, hour)
        putInt(KEY_BOOT_MINUTE, minute)
        putBoolean(KEY_BOOT_REPEAT, repeat)
        apply()
      }

      // 尝试设置定时开机（需要硬件支持）
      // 某些设备可能通过系统属性或厂商 API 支持
      val success = trySetBootSchedule(hour, minute, repeat)
      
      if (success) {
        Log.i(TAG, "定时开机设置成功: ${String.format("%02d:%02d", hour, minute)}, 重复: $repeat")
        promise.resolve(true)
      } else {
        Log.w(TAG, "设备可能不支持定时开机功能")
        // 即使硬件不支持，也保存设置，以便将来使用
        promise.resolve(false)
      }
    } catch (e: Exception) {
      Log.e(TAG, "设置定时开机失败: ${e.message}", e)
      promise.reject("E_SET_BOOT_FAILED", "设置定时开机失败: ${e.message}")
    }
  }

  /**
   * 尝试设置定时开机（需要硬件支持）
   */
  private fun trySetBootSchedule(hour: Int, minute: Int, repeat: Boolean): Boolean {
    return try {
      // 方法1: 尝试通过系统属性设置（某些设备支持）
      val process = Runtime.getRuntime().exec("setprop persist.sys.scheduled_boot_time \"$hour:$minute\"")
      val exitCode = process.waitFor()
      
      if (exitCode == 0) {
        Log.i(TAG, "通过系统属性设置定时开机成功")
        return true
      }
      
      // 方法2: 尝试通过厂商特定的方法（这里只是示例，实际需要根据设备调整）
      // 某些设备可能使用不同的方法
      
      false
    } catch (e: Exception) {
      Log.w(TAG, "设置定时开机失败，设备可能不支持: ${e.message}")
      false
    }
  }

  /**
   * 取消定时开机
   */
  fun cancelScheduledBoot(promise: Promise) {
    try {
      // 清除系统属性
      try {
        val process = Runtime.getRuntime().exec("setprop persist.sys.scheduled_boot_time \"\"")
        process.waitFor()
      } catch (e: Exception) {
        Log.w(TAG, "清除定时开机系统属性失败: ${e.message}")
      }
      
      // 清除设置
      prefs.edit().putBoolean(KEY_BOOT_ENABLED, false).apply()

      Log.i(TAG, "定时开机已取消")
      promise.resolve(true)
    } catch (e: Exception) {
      Log.e(TAG, "取消定时开机失败: ${e.message}", e)
      promise.reject("E_CANCEL_BOOT_FAILED", "取消定时开机失败: ${e.message}")
    }
  }

  /**
   * 获取定时开机设置
   */
  fun getScheduledBoot(promise: Promise) {
    try {
      val enabled = prefs.getBoolean(KEY_BOOT_ENABLED, false)
      if (!enabled) {
        promise.resolve(null)
        return
      }

      val hour = prefs.getInt(KEY_BOOT_HOUR, 0)
      val minute = prefs.getInt(KEY_BOOT_MINUTE, 0)
      val repeat = prefs.getBoolean(KEY_BOOT_REPEAT, false)

      val result = com.facebook.react.bridge.Arguments.createMap().apply {
        putBoolean("enabled", true)
        putInt("hour", hour)
        putInt("minute", minute)
        putBoolean("repeat", repeat)
      }

      promise.resolve(result)
    } catch (e: Exception) {
      Log.e(TAG, "获取定时开机设置失败: ${e.message}", e)
      promise.reject("E_GET_BOOT_FAILED", "获取定时开机设置失败: ${e.message}")
    }
  }
}

