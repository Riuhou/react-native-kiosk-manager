package com.riuhou.kioskmanager

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log


class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            context?.let {
                val prefs = it.getSharedPreferences("kiosk_prefs", Context.MODE_PRIVATE)
                val shouldAutoStart = prefs.getBoolean("boot_auto_start", true)

                if (shouldAutoStart) {
                    // 启动应用的主Activity
                    val packageManager = it.packageManager
                    val launchIntent = packageManager.getLaunchIntentForPackage(it.packageName)
                    launchIntent?.let { intent ->
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        it.startActivity(intent)
                        Log.d("BootReceiver", "Auto-starting kiosk app on boot")
                    }
                }
            }
        }
    }
}