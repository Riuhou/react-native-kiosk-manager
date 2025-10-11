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
}