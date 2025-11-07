package com.riuhou.kioskmanager.managers

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * 应用启动管理器 - 处理应用启动和安装状态检查
 */
class AppLaunchManager(
  private val reactContext: ReactApplicationContext,
  private val onStatusEvent: (status: String, packageName: String?, message: String?) -> Unit
) {

  fun isAppInstalled(packageName: String, promise: Promise) {
    try {
      val context = reactContext
      val packageManager = context.packageManager
      
      // 方式1: 先尝试使用 PackageManager API
      var isInstalled = false
      try {
        val appInfo = packageManager.getApplicationInfo(packageName, 0)
        isInstalled = true
        Log.i("KioskManager", "✓ 通过 getApplicationInfo 检测到应用已安装: $packageName")
        Log.i("KioskManager", "包信息: enabled=${appInfo.enabled}")
      } catch (e: PackageManager.NameNotFoundException) {
        Log.i("KioskManager", "✗ 通过 getApplicationInfo 未检测到应用: $packageName")
      } catch (e: Exception) {
        Log.w("KioskManager", "getApplicationInfo 检查时出现异常: ${e.message}")
      }
      
      // 方式2: 如果 PackageManager API 找不到，使用 shell 命令检测（更可靠）
      if (!isInstalled) {
        try {
          val process = Runtime.getRuntime().exec("pm list packages")
          val reader = BufferedReader(InputStreamReader(process.inputStream))
          var line: String?
          
          while (reader.readLine().also { line = it } != null) {
            // pm list packages 输出格式: package:com.example.app
            if (line!!.trim().equals("package:$packageName", ignoreCase = true)) {
              isInstalled = true
              Log.i("KioskManager", "✓ 通过 pm list packages 检测到应用已安装: $packageName")
              break
            }
          }
          
          reader.close()
          process.waitFor()
          
          if (!isInstalled) {
            Log.i("KioskManager", "✗ 通过 pm list packages 也未检测到应用: $packageName")
          }
        } catch (e: Exception) {
          Log.w("KioskManager", "使用 shell 命令检测时出现异常: ${e.message}")
        }
      }
      
      promise.resolve(isInstalled)
    } catch (e: Exception) {
      Log.e("KioskManager", "检查应用安装状态失败: ${e.message}", e)
      promise.reject("E_CHECK_FAILED", "Failed to check if app is installed: ${e.message}")
    }
  }

  fun launchApp(packageName: String, promise: Promise) {
    try {
      val context = reactContext
      val packageManager = context.packageManager
      
      Log.i("KioskManager", "=== 开始启动应用 ===")
      Log.i("KioskManager", "包名: $packageName")
      
      // 检查应用是否已安装 - 使用多种方式尝试
      var isInstalled = false
      try {
        // 方式1: 使用 GET_ACTIVITIES flag
        packageManager.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES)
        isInstalled = true
        Log.i("KioskManager", "应用已安装 (方式1)")
      } catch (e: PackageManager.NameNotFoundException) {
        try {
          // 方式2: 使用 0 flag
          packageManager.getPackageInfo(packageName, 0)
          isInstalled = true
          Log.i("KioskManager", "应用已安装 (方式2)")
        } catch (e2: PackageManager.NameNotFoundException) {
          // 方式3: 尝试获取启动意图（即使没有包信息，也可能有启动意图）
          val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
          if (launchIntent != null) {
            isInstalled = true
            Log.i("KioskManager", "应用已安装 (方式3: 通过启动意图检测)")
          } else {
            Log.e("KioskManager", "应用未安装: $packageName")
            Log.e("KioskManager", "所有检查方式都失败")
            promise.reject("E_APP_NOT_FOUND", "App not found: $packageName")
            return
          }
        }
      }
      
      if (!isInstalled) {
        Log.e("KioskManager", "应用未安装: $packageName")
        promise.reject("E_APP_NOT_FOUND", "App not found: $packageName")
        return
      }
      
      // 获取应用的主Activity
      val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
      if (launchIntent != null) {
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        
        context.startActivity(launchIntent)
        
        Log.i("KioskManager", "=== 应用启动成功 ===")
        Log.i("KioskManager", "包名: $packageName")
        Log.i("KioskManager", "==================")
        
        promise.resolve(true)
      } else {
        Log.e("KioskManager", "No launch intent found for package: $packageName")
        promise.reject("E_NO_LAUNCH_INTENT", "No launch intent found for package: $packageName")
      }
    } catch (e: Exception) {
      Log.e("KioskManager", "Failed to launch app: ${e.message}", e)
      promise.reject("E_LAUNCH_FAILED", "Failed to launch app: ${e.message}")
    }
  }

  fun launchAppInternal(packageName: String, retryCount: Int = 0) {
    try {
      val context = reactContext
      val packageManager = context.packageManager
      val maxRetries = 20 // 最多重试20次
      val retryDelay = if (retryCount < 5) 1000L else 2000L // 前5次1秒，之后2秒
      
      Log.i("KioskManager", "尝试启动应用: $packageName (重试次数: $retryCount)")
      
      // 检测应用是否已安装 - 使用 getApplicationInfo 和 shell 命令
      var isInstalled = false
      var mainActivity: String? = null
      try {
        val appInfo = packageManager.getApplicationInfo(packageName, 0)
        isInstalled = true
        Log.i("KioskManager", "✓ 检测到应用已安装: $packageName")
        
        // 尝试获取主 Activity
        try {
          val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
          if (launchIntent != null && launchIntent.component != null) {
            val component = launchIntent.component!!
            // am start 命令格式: packageName/.ActivityName 或 packageName/ActivityName
            // component.className 是完整类名，需要提取短名称
            val className = component.className
            val shortName = if (className.startsWith(packageName)) {
              className.substring(packageName.length + 1) // 去掉包名和点
            } else {
              className.substringAfterLast('.') // 如果格式不同，取最后一段
            }
            mainActivity = "${component.packageName}/.$shortName"
            Log.i("KioskManager", "✓ 获取到主 Activity: $mainActivity (完整类名: $className)")
          }
        } catch (e: Exception) {
          Log.w("KioskManager", "无法获取主 Activity: ${e.message}")
        }
      } catch (e: PackageManager.NameNotFoundException) {
        // 如果 PackageManager API 找不到，尝试使用 shell 命令
        try {
          val process = Runtime.getRuntime().exec("pm list packages")
          val reader = BufferedReader(InputStreamReader(process.inputStream))
          var line: String?
          
          while (reader.readLine().also { line = it } != null) {
            if (line!!.trim().equals("package:$packageName", ignoreCase = true)) {
              isInstalled = true
              Log.i("KioskManager", "✓ 通过 pm list packages 检测到应用已安装: $packageName")
              break
            }
          }
          
          reader.close()
          process.waitFor()
          
          if (!isInstalled) {
            Log.w("KioskManager", "✗ 应用未找到: $packageName")
          } else {
            // 如果通过 shell 检测到应用，尝试通过 shell 获取主 Activity
            try {
              val process2 = Runtime.getRuntime().exec("pm dump $packageName | grep -A 5 'MAIN'")
              val reader2 = BufferedReader(InputStreamReader(process2.inputStream))
              var line2: String?
              while (reader2.readLine().also { line2 = it } != null) {
                val lineStr = line2 ?: ""
                if (lineStr.contains("android.intent.action.MAIN") && lineStr.contains(packageName)) {
                  // 提取 Activity 名称
                  val activityMatch = Regex("""$packageName[/.]([^\s]+)""").find(lineStr)
                  if (activityMatch != null) {
                    mainActivity = "${packageName}/${activityMatch.groupValues[1]}"
                    Log.i("KioskManager", "✓ 通过 shell 获取到主 Activity: $mainActivity")
                    break
                  }
                }
              }
              reader2.close()
              process2.waitFor()
            } catch (e2: Exception) {
              Log.w("KioskManager", "无法通过 shell 获取主 Activity: ${e2.message}")
            }
          }
        } catch (e2: Exception) {
          Log.w("KioskManager", "✗ 应用未找到: $packageName")
        }
      } catch (e: Exception) {
        Log.w("KioskManager", "检查应用安装状态时出现异常: ${e.message}")
      }
      
      // 如果应用未安装且还有重试次数，延迟后重试
      if (!isInstalled && retryCount < maxRetries) {
        Log.w("KioskManager", "应用未找到，${retryDelay}ms 后重试 (${retryCount + 1}/$maxRetries): $packageName")
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
          launchAppInternal(packageName, retryCount + 1)
        }, retryDelay)
        return
      }
      
      // 如果重试次数用完还是找不到，报告错误
      if (!isInstalled) {
        Log.e("KioskManager", "应用未找到，已达到最大重试次数 ($maxRetries): $packageName")
        onStatusEvent("launch_failed", packageName, "应用未找到，可能安装失败")
        return
      }
      
      // 检查是否为 Device Owner
      val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
      val isDeviceOwner = dpm.isDeviceOwnerApp(context.packageName)
      
      // 方式1: 使用标准 Intent 启动（优先尝试）
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
          Log.i("KioskManager", "✓ 使用标准 Intent 启动成功")
        }
      } catch (e: Exception) {
        Log.w("KioskManager", "标准 Intent 启动失败: ${e.message}")
      }
      
      // 方式2: 如果标准方式失败且是 Device Owner，使用 shell 命令启动（更可靠）
      if (!launchSuccess && isDeviceOwner) {
        try {
          Log.i("KioskManager", "尝试使用 shell 命令启动应用")
          
          // 如果已知主 Activity，直接启动
          if (mainActivity != null) {
            val command = "am start -n $mainActivity -a android.intent.action.MAIN -c android.intent.category.LAUNCHER"
            Log.i("KioskManager", "执行命令: $command")
            val process = Runtime.getRuntime().exec(command)
            val exitCode = process.waitFor()
            if (exitCode == 0) {
              launchSuccess = true
              Log.i("KioskManager", "✓ 使用 shell 命令启动成功 (主 Activity)")
            } else {
              val errorReader = BufferedReader(InputStreamReader(process.errorStream))
              val error = errorReader.readText()
              Log.w("KioskManager", "Shell 命令启动失败 (exitCode=$exitCode): $error")
            }
          } else {
            // 如果不知道主 Activity，使用包名启动
            val command = "am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n $packageName/.MainActivity"
            Log.i("KioskManager", "执行命令: $command")
            val process = Runtime.getRuntime().exec(command)
            var exitCode = process.waitFor()
            
            if (exitCode != 0) {
              // 尝试其他常见的主 Activity 名称
              val commonActivities = listOf("MainActivity", "SplashActivity", "LauncherActivity", "Main")
              for (activityName in commonActivities) {
                val command2 = "am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n $packageName/.$activityName"
                Log.i("KioskManager", "尝试命令: $command2")
                val process2 = Runtime.getRuntime().exec(command2)
                exitCode = process2.waitFor()
                if (exitCode == 0) {
                  launchSuccess = true
                  Log.i("KioskManager", "✓ 使用 shell 命令启动成功 ($activityName)")
                  break
                }
              }
            } else {
              launchSuccess = true
              Log.i("KioskManager", "✓ 使用 shell 命令启动成功 (MainActivity)")
            }
            
            if (!launchSuccess) {
              // 最后尝试：使用包名启动（让系统自动选择 Activity）
              val command3 = "am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER $packageName"
              Log.i("KioskManager", "执行命令: $command3")
              val process3 = Runtime.getRuntime().exec(command3)
              exitCode = process3.waitFor()
              if (exitCode == 0) {
                launchSuccess = true
                Log.i("KioskManager", "✓ 使用 shell 命令启动成功 (自动选择)")
              }
            }
          }
        } catch (e: Exception) {
          Log.w("KioskManager", "Shell 命令启动失败: ${e.message}")
        }
      }
      
      // 方式3: 如果前两种方式都失败，尝试使用 PackageManager 查询所有 Activity
      if (!launchSuccess) {
        try {
          Log.i("KioskManager", "尝试查询所有 Activity 并启动")
          val intent = Intent(Intent.ACTION_MAIN)
          intent.addCategory(Intent.CATEGORY_LAUNCHER)
          intent.setPackage(packageName)
          
          val activities = packageManager.queryIntentActivities(intent, 0)
          if (activities.isNotEmpty()) {
            val activityInfo = activities[0].activityInfo
            val component = ComponentName(activityInfo.packageName, activityInfo.name)
            val launchIntent = Intent(Intent.ACTION_MAIN).apply {
              addCategory(Intent.CATEGORY_LAUNCHER)
              setComponent(component)
              addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
              addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
              addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
              addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            }
            context.startActivity(launchIntent)
            launchSuccess = true
            Log.i("KioskManager", "✓ 通过查询 Activity 启动成功: ${activityInfo.name}")
          }
        } catch (e: Exception) {
          Log.w("KioskManager", "查询 Activity 启动失败: ${e.message}")
        }
      }
      
      if (launchSuccess) {
        Log.i("KioskManager", "=== 应用启动成功 ===")
        Log.i("KioskManager", "包名: $packageName")
        Log.i("KioskManager", "重试次数: $retryCount")
        Log.i("KioskManager", "是否为 Device Owner: $isDeviceOwner")
        Log.i("KioskManager", "==================")
        
        // 发送启动成功事件
        onStatusEvent("launched", packageName, "应用启动成功")
      } else {
        Log.e("KioskManager", "所有启动方式都失败: $packageName")
        onStatusEvent("launch_failed", packageName, "所有启动方式都失败")
      }
    } catch (e: Exception) {
      Log.e("KioskManager", "Failed to launch app: ${e.message}", e)
      onStatusEvent("launch_failed", packageName, "启动失败: ${e.message}")
    }
  }
}

