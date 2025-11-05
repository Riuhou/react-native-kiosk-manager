package com.riuhou.kioskmanager

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * 静态注册的广播接收器，用于处理 APK 安装完成后的启动
 * 
 * 这个接收器在 AndroidManifest.xml 中静态注册，即使应用进程被杀死也能接收广播
 * 这对于应用自己更新自己的场景非常重要
 * 
 * 监听两种广播：
 * 1. 系统的 PACKAGE_ADDED 广播 - 最可靠，可以捕获所有安装完成事件
 * 2. 自定义的 INSTALL_COMPLETE 广播 - 用于 PackageInstaller 的安装回调
 */
class InstallCompleteReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        
        val action = intent.action
        Log.i("InstallCompleteReceiver", "收到广播: action=$action")
        
        // 处理系统的 PACKAGE_ADDED 广播（最可靠的方式）
        if (action == Intent.ACTION_PACKAGE_ADDED || action == Intent.ACTION_PACKAGE_REPLACED) {
            val data = intent.data
            val installedPackage = data?.schemeSpecificPart
            
            if (installedPackage != null) {
                Log.i("InstallCompleteReceiver", "检测到应用安装/更新: $installedPackage")
                
                // 检查是否需要自动启动
                // 如果安装的是当前应用（应用自己更新自己），或者安装的是我们期望启动的应用
                // 这里可以根据需要调整逻辑，比如检查 SharedPreferences 中保存的待启动包名
                val shouldAutoLaunch = shouldAutoLaunch(context, installedPackage)
                
                if (shouldAutoLaunch) {
                    Log.i("InstallCompleteReceiver", "准备自动启动应用: $installedPackage")
                    // 延迟启动，给系统时间完成安装和刷新 PackageManager 缓存
                    Handler(Looper.getMainLooper()).postDelayed({
                        launchApp(context, installedPackage)
                    }, 2000) // 延迟2秒
                } else {
                    Log.i("InstallCompleteReceiver", "跳过自动启动: $installedPackage")
                }
            }
            return
        }
        
        // 处理自定义的 INSTALL_COMPLETE 广播（PackageInstaller 的回调）
        if (action == "com.kioskmanager.INSTALL_COMPLETE") {
            val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)
            // 优先使用我们传递的包名，如果没有则尝试从系统广播中获取
            var packageName = intent.getStringExtra("target_package_name")
            if (packageName == null) {
                packageName = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME)
            }
            if (packageName == null) {
                // 如果都没有，使用当前应用的包名（应用自己更新自己）
                packageName = context.packageName
            }
            
            Log.i("InstallCompleteReceiver", "安装状态: status=$status, packageName=$packageName")
            
            when (status) {
                PackageInstaller.STATUS_SUCCESS -> {
                    val targetPackageName = packageName ?: context.packageName
                    Log.i("InstallCompleteReceiver", "安装成功: $targetPackageName")
                    
                    // 延迟启动，给系统时间完成安装和刷新 PackageManager 缓存
                    Handler(Looper.getMainLooper()).postDelayed({
                        launchApp(context, targetPackageName)
                    }, 2000) // 延迟2秒
                }
                PackageInstaller.STATUS_FAILURE -> {
                    Log.e("InstallCompleteReceiver", "安装失败: $packageName")
                }
                else -> {
                    Log.w("InstallCompleteReceiver", "未知安装状态: $status")
                }
            }
        }
    }
    
    /**
     * 判断是否应该自动启动安装的应用
     * 可以根据业务需求调整逻辑
     */
    private fun shouldAutoLaunch(context: Context, packageName: String): Boolean {
        // 方式1: 如果是当前应用自己更新自己，则启动
        if (packageName == context.packageName) {
            Log.i("InstallCompleteReceiver", "检测到应用自己更新自己，应该启动")
            return true
        }
        
        // 方式2: 检查 SharedPreferences 中是否有标记需要启动的包名
        try {
            val prefs = context.getSharedPreferences("kiosk_manager_prefs", Context.MODE_PRIVATE)
            val pendingLaunchPackage = prefs.getString("pending_launch_package", null)
            if (pendingLaunchPackage == packageName) {
                Log.i("InstallCompleteReceiver", "检测到待启动的包名匹配，应该启动")
                // 清除标记
                prefs.edit().remove("pending_launch_package").apply()
                return true
            }
        } catch (e: Exception) {
            Log.w("InstallCompleteReceiver", "检查 SharedPreferences 失败: ${e.message}")
        }
        
        // 默认情况下，如果是应用自己更新自己才自动启动
        // 其他应用安装不会自动启动（可以根据需要调整）
        return false
    }
    
    /**
     * 启动应用
     */
    private fun launchApp(context: Context, packageName: String) {
        try {
            Log.i("InstallCompleteReceiver", "准备启动应用: $packageName")
            
            val packageManager = context.packageManager
            
            // 方式1: 使用标准 Intent 启动
            var launchSuccess = false
            try {
                val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                    
                    context.startActivity(launchIntent)
                    launchSuccess = true
                    Log.i("InstallCompleteReceiver", "✓ 使用标准 Intent 启动成功")
                }
            } catch (e: Exception) {
                Log.w("InstallCompleteReceiver", "标准 Intent 启动失败: ${e.message}")
            }
            
            // 方式2: 如果标准方式失败，使用 shell 命令启动（Device Owner 权限）
            if (!launchSuccess) {
                try {
                    // 检查是否为 Device Owner
                    val dpm = context.getSystemService(android.content.Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
                    val isDeviceOwner = dpm.isDeviceOwnerApp(context.packageName)
                    
                    if (isDeviceOwner) {
                        Log.i("InstallCompleteReceiver", "尝试使用 shell 命令启动应用")
                        
                        // 尝试获取主 Activity
                        var mainActivity: String? = null
                        try {
                            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
                            if (launchIntent != null && launchIntent.component != null) {
                                val component = launchIntent.component!!
                                val className = component.className
                                val shortName = if (className.startsWith(packageName)) {
                                    className.substring(packageName.length + 1)
                                } else {
                                    className.substringAfterLast('.')
                                }
                                mainActivity = "${component.packageName}/.$shortName"
                            }
                        } catch (e: Exception) {
                            Log.w("InstallCompleteReceiver", "无法获取主 Activity: ${e.message}")
                        }
                        
                        if (mainActivity != null) {
                            val command = "am start -n $mainActivity -a android.intent.action.MAIN -c android.intent.category.LAUNCHER"
                            Log.i("InstallCompleteReceiver", "执行命令: $command")
                            val process = Runtime.getRuntime().exec(command)
                            val exitCode = process.waitFor()
                            if (exitCode == 0) {
                                launchSuccess = true
                                Log.i("InstallCompleteReceiver", "✓ 使用 shell 命令启动成功")
                            } else {
                                val errorReader = BufferedReader(InputStreamReader(process.errorStream))
                                val error = errorReader.readText()
                                Log.w("InstallCompleteReceiver", "Shell 命令启动失败: $error")
                            }
                        } else {
                            // 尝试使用包名启动
                            val command = "am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER $packageName"
                            Log.i("InstallCompleteReceiver", "执行命令: $command")
                            val process = Runtime.getRuntime().exec(command)
                            val exitCode = process.waitFor()
                            if (exitCode == 0) {
                                launchSuccess = true
                                Log.i("InstallCompleteReceiver", "✓ 使用 shell 命令启动成功")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w("InstallCompleteReceiver", "Shell 命令启动失败: ${e.message}")
                }
            }
            
            if (!launchSuccess) {
                Log.e("InstallCompleteReceiver", "所有启动方式都失败: $packageName")
            }
        } catch (e: Exception) {
            Log.e("InstallCompleteReceiver", "启动应用失败: ${e.message}", e)
        }
    }
}

