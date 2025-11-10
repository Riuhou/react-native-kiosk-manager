package com.riuhou.kioskmanager

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.riuhou.kioskmanager.managers.PowerScheduleManager

/**
 * 定时开关机广播接收器
 * 处理定时关机的广播事件
 */
class PowerScheduleReceiver : BroadcastReceiver() {
  
  companion object {
    private const val TAG = "PowerScheduleReceiver"
  }

  override fun onReceive(context: Context, intent: Intent) {
    when (intent.action) {
      "com.riuhou.kioskmanager.SCHEDULED_SHUTDOWN" -> {
        Log.i(TAG, "收到定时关机广播")
        handleScheduledShutdown(context)
      }
      "com.riuhou.kioskmanager.SCHEDULED_BOOT" -> {
        Log.i(TAG, "收到定时开机广播")
        // 定时开机通常由系统处理，这里只是记录日志
      }
    }
  }

  private fun handleScheduledShutdown(context: Context) {
    val targetContext = context.applicationContext ?: context
    try {
      if (PowerScheduleManager.tryExecuteDevicePolicyShutdown(targetContext)) {
        Log.i(TAG, "定时关机命令已执行")
        return
      }

      Log.w(TAG, "系统 API 关机方法不可用，尝试使用其他方法")
      // 作为设备所有者，尝试多种关机方法
      var shutdownSuccess = false
      
      // 方法1: 直接执行 reboot -p
      try {
        val process = Runtime.getRuntime().exec("reboot -p")
        val completed = process.waitFor(500, java.util.concurrent.TimeUnit.MILLISECONDS)
        if (completed) {
          val exitCode = process.exitValue()
          Log.i(TAG, "通过 reboot -p 命令执行定时关机，退出码: $exitCode")
          shutdownSuccess = true
        } else {
          Log.w(TAG, "reboot -p 命令超时，但可能已触发关机")
          shutdownSuccess = true
        }
      } catch (e: Exception) {
        Log.w(TAG, "reboot -p 失败: ${e.message}")
      }
      
      // 方法2: 通过 sh 执行 reboot -p
      if (!shutdownSuccess) {
        try {
          val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", "reboot -p"))
          val completed = process.waitFor(500, java.util.concurrent.TimeUnit.MILLISECONDS)
          if (completed) {
            val exitCode = process.exitValue()
            Log.i(TAG, "通过 sh -c reboot -p 执行定时关机，退出码: $exitCode")
            shutdownSuccess = true
          } else {
            Log.w(TAG, "sh -c reboot -p 命令超时，但可能已触发关机")
            shutdownSuccess = true
          }
        } catch (e: Exception) {
          Log.w(TAG, "sh -c reboot -p 失败: ${e.message}")
        }
      }
      
      // 方法3: 尝试使用 am broadcast 发送关机广播
      if (!shutdownSuccess) {
        try {
          val process = Runtime.getRuntime().exec(arrayOf("am", "broadcast", "-a", "android.intent.action.ACTION_REQUEST_SHUTDOWN"))
          val completed = process.waitFor(500, java.util.concurrent.TimeUnit.MILLISECONDS)
          if (completed) {
            val exitCode = process.exitValue()
            Log.i(TAG, "通过 am broadcast 发送关机广播，退出码: $exitCode")
            shutdownSuccess = true
          }
        } catch (e: Exception) {
          Log.w(TAG, "am broadcast 发送关机广播失败: ${e.message}")
        }
      }
      
      // 方法4: 尝试使用 svc power shutdown
      if (!shutdownSuccess) {
        try {
          val process = Runtime.getRuntime().exec(arrayOf("svc", "power", "shutdown"))
          val completed = process.waitFor(500, java.util.concurrent.TimeUnit.MILLISECONDS)
          if (completed) {
            val exitCode = process.exitValue()
            Log.i(TAG, "通过 svc power shutdown 执行定时关机，退出码: $exitCode")
            shutdownSuccess = true
          }
        } catch (e: Exception) {
          Log.w(TAG, "svc power shutdown 失败: ${e.message}")
        }
      }
      
      if (!shutdownSuccess) {
        Log.e(TAG, "所有关机方法都失败")
      }
    } catch (e: Exception) {
      Log.e(TAG, "处理定时关机失败: ${e.message}", e)
    } finally {
      PowerScheduleManager.scheduleShutdownAlarm(targetContext, triggered = true)
    }
  }
}

