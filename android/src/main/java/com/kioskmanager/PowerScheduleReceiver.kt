package com.riuhou.kioskmanager

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

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
    try {
      // 创建 PowerScheduleManager 实例并执行关机
      // 注意：这里需要 ReactApplicationContext，但 BroadcastReceiver 只有 Context
      // 所以我们需要直接使用 DevicePolicyManager
      val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
      
      if (dpm.isDeviceOwnerApp(context.packageName)) {
        try {
          // 尝试使用反射调用 reboot 方法
          val method = dpm.javaClass.getMethod("reboot", android.content.ComponentName::class.java, String::class.java)
          val adminComponent = android.content.ComponentName(context, DeviceAdminReceiver::class.java)
          method.invoke(dpm, adminComponent, "shutdown")
          Log.i(TAG, "定时关机命令已执行")
        } catch (e: NoSuchMethodException) {
          // 如果 reboot 方法不可用，尝试使用 shell 命令
          Log.w(TAG, "DevicePolicyManager.reboot 方法不可用，尝试使用 shell 命令")
          try {
            val process = Runtime.getRuntime().exec("su -c reboot -p")
            process.waitFor()
            Log.i(TAG, "通过 shell 命令执行定时关机")
          } catch (e2: Exception) {
            Log.e(TAG, "执行定时关机失败: ${e2.message}", e2)
          }
        }
      } else {
        Log.e(TAG, "应用不是设备所有者，无法执行定时关机")
      }
    } catch (e: Exception) {
      Log.e(TAG, "处理定时关机失败: ${e.message}", e)
    }
  }
}

