package com.riuhou.kioskmanager.managers

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.PowerManager
import android.util.Log
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.riuhou.kioskmanager.DeviceAdminReceiver
import java.lang.reflect.InvocationTargetException
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

    fun tryExecuteDevicePolicyShutdown(context: Context, reason: String = "shutdown"): Boolean {
      return try {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        if (dpm == null) {
          Log.e(TAG, "无法获取 DevicePolicyManager")
          return false
        }

        if (!dpm.isDeviceOwnerApp(context.packageName)) {
          Log.e(TAG, "应用不是设备所有者，无法执行关机")
          return false
        }

        // 使用应用的包名来构建 ComponentName，而不是库的包名
        // 应用的 DeviceAdminReceiver 应该在自己的包名下
        val adminComponent = ComponentName(context.packageName, "${context.packageName}.DeviceAdminReceiver")
        val rebootMethods = mutableListOf<java.lang.reflect.Method>()

        runCatching {
          dpm.javaClass.getMethod("reboot", ComponentName::class.java, String::class.java)
        }.onSuccess { method ->
          method.isAccessible = true
          rebootMethods.add(method)
        }

        runCatching {
          dpm.javaClass.getMethod("reboot", ComponentName::class.java)
        }.onSuccess { method ->
          method.isAccessible = true
          rebootMethods.add(method)
        }

        for (method in rebootMethods) {
          try {
            if (method.parameterTypes.size == 2) {
              method.invoke(dpm, adminComponent, reason)
            } else {
              method.invoke(dpm, adminComponent)
            }
            Log.i(TAG, "通过 DevicePolicyManager 执行关机成功")
            return true
          } catch (invokeError: InvocationTargetException) {
            val cause = invokeError.cause ?: invokeError
            Log.w(TAG, "DevicePolicyManager.reboot 调用失败: ${cause.message}", cause)
          } catch (accessError: IllegalAccessException) {
            Log.w(TAG, "无法访问 DevicePolicyManager.reboot: ${accessError.message}", accessError)
          }
        }

        false
      } catch (e: Exception) {
        Log.e(TAG, "尝试通过 DevicePolicyManager 执行关机失败: ${e.message}", e)
        false
      }
    }

    fun hasExactAlarmPermission(context: Context): Boolean {
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        return true
      }

      val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false
      return alarmManager.canScheduleExactAlarms()
    }

    private fun getPrefs(context: Context): SharedPreferences {
      return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun buildShutdownIntent(context: Context): Intent {
      return Intent(ACTION_SCHEDULED_SHUTDOWN).apply {
        setPackage(context.packageName)
      }
    }

    private fun buildShutdownPendingIntent(context: Context, flag: Int = PendingIntent.FLAG_UPDATE_CURRENT): PendingIntent {
      val baseFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        flag or PendingIntent.FLAG_IMMUTABLE
      } else {
        flag
      }

      return PendingIntent.getBroadcast(
        context,
        0,
        buildShutdownIntent(context),
        baseFlags
      )
    }

    fun cancelShutdownAlarm(context: Context) {
      val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
      val pendingIntent = buildShutdownPendingIntent(context)
      alarmManager.cancel(pendingIntent)
    }

    fun scheduleShutdownAlarm(context: Context, triggered: Boolean = false) {
      val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: run {
        Log.e(TAG, "无法获取 AlarmManager，无法设置定时关机")
        return
      }

      val prefs = getPrefs(context)
      val enabled = prefs.getBoolean(KEY_SHUTDOWN_ENABLED, false)
      if (!enabled) {
        cancelShutdownAlarm(context)
        return
      }

      val hour = prefs.getInt(KEY_SHUTDOWN_HOUR, 0)
      val minute = prefs.getInt(KEY_SHUTDOWN_MINUTE, 0)
      val repeat = prefs.getBoolean(KEY_SHUTDOWN_REPEAT, false)

      if (!repeat && triggered) {
        Log.i(TAG, "单次定时关机已触发，自动关闭定时")
        prefs.edit().putBoolean(KEY_SHUTDOWN_ENABLED, false).apply()
        cancelShutdownAlarm(context)
        return
      }

      val calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)

        if (timeInMillis <= System.currentTimeMillis()) {
          add(Calendar.DAY_OF_YEAR, 1)
        }
      }

      val triggerAt = calendar.timeInMillis
      val pendingIntent = buildShutdownPendingIntent(context)

      // 避免重复的 PendingIntent
      alarmManager.cancel(pendingIntent)

      val canUseExact = hasExactAlarmPermission(context)

      fun scheduleExact() {
        when {
          Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
          }
          Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT -> {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
          }
          else -> {
            @Suppress("DEPRECATION")
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
          }
        }
      }

      fun scheduleInexact() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
          alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        } else {
          @Suppress("DEPRECATION")
          alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
      }

      try {
        if (canUseExact) {
          scheduleExact()
        } else {
          Log.w(
            TAG,
            "未获得精确定时权限，改用非精确定时闹钟，可能会出现延迟。如需更精准可提醒用户授予精确定时权限。"
          )
          scheduleInexact()
        }
      } catch (se: SecurityException) {
        Log.w(TAG, "设置精确定时闹钟被拒绝，使用非精确定时方案重试", se)
        scheduleInexact()
      }

      Log.i(
        TAG,
        "定时关机闹钟已设置: ${String.format("%02d:%02d", hour, minute)}, 下一次触发时间: ${calendar.time}, 重复: $repeat"
      )
    }
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

      scheduleShutdownAlarm(reactContext)

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
      cancelShutdownAlarm(reactContext)

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

      if (tryExecuteDevicePolicyShutdown(reactContext)) {
        promise.resolve(true)
        return
      }

      // 如果 DevicePolicyManager 接口不可用，尝试使用 shell 命令
      Log.w(TAG, "DevicePolicyManager reboot 接口不可用，尝试使用 shell 命令")
      try {
        val process = Runtime.getRuntime().exec("su -c reboot -p")
        process.waitFor()
        Log.i(TAG, "通过 shell 命令执行关机")
        promise.resolve(true)
      } catch (e: Exception) {
        Log.e(TAG, "执行关机失败: ${e.message}", e)
        promise.reject("E_SHUTDOWN_FAILED", "执行关机失败: ${e.message}")
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

