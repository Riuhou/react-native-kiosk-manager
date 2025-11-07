package com.riuhou.kioskmanager.managers

import android.os.Environment
import android.util.Log
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.WritableMap
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * APK下载管理器 - 处理APK文件下载功能
 */
class ApkDownloadManager(
  private val reactContext: ReactApplicationContext,
  private val onProgress: (progress: Int, bytesRead: Long, totalBytes: Long) -> Unit
) {

  fun downloadApk(url: String, promise: Promise) {
    try {
      val context = reactContext
      
      // 从 URL 中提取文件名
      var fileName = extractFileNameFromUrl(url)
      val downloadsDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "apk_updates")
      if (!downloadsDir.exists()) {
        downloadsDir.mkdirs()
      }
      
      val connection = URL(url).openConnection() as HttpURLConnection
      connection.requestMethod = "GET"
      connection.connect()
      
      if (connection.responseCode != HttpURLConnection.HTTP_OK) {
        promise.reject("E_DOWNLOAD_FAILED", "HTTP error: ${connection.responseCode}")
        return
      }
      
      // 尝试从 Content-Disposition 头中获取文件名（更准确）
      fileName = extractFileNameFromContentDisposition(connection, fileName)
      
      val apkFile = File(downloadsDir, fileName)
      
      // 如果文件已存在，删除它（覆盖）
      if (apkFile.exists()) {
        val deleted = apkFile.delete()
        if (deleted) {
          Log.i("KioskManager", "已删除现有文件: $fileName")
        } else {
          Log.w("KioskManager", "无法删除现有文件: $fileName，将尝试覆盖")
        }
      }
      
      // 打印下载开始信息
      Log.i("KioskManager", "=== 开始下载 ===")
      Log.i("KioskManager", "下载URL: $url")
      Log.i("KioskManager", "目标文件名: $fileName")
      Log.i("KioskManager", "下载目录: ${downloadsDir.absolutePath}")
      Log.i("KioskManager", "完整路径: ${apkFile.absolutePath}")
      Log.i("KioskManager", "目录存在: ${downloadsDir.exists()}")
      Log.i("KioskManager", "目录可写: ${downloadsDir.canWrite()}")
      Log.i("KioskManager", "==================")
      
      val inputStream: InputStream = connection.inputStream
      val outputStream = FileOutputStream(apkFile)
      
      val buffer = ByteArray(4096)
      var bytesRead: Int
      var totalBytesRead = 0L
      val contentLength = connection.contentLength.toLong()
      var lastProgressSent = -1
      
      while (inputStream.read(buffer).also { bytesRead = it } != -1) {
        outputStream.write(buffer, 0, bytesRead)
        totalBytesRead += bytesRead
        
        // 发送进度更新（每5%发送一次，避免过于频繁）
        if (contentLength > 0) {
          val progress = (totalBytesRead * 100 / contentLength).toInt()
          if (progress >= lastProgressSent + 5 || progress == 100) {
            lastProgressSent = progress
            onProgress(progress, totalBytesRead, contentLength)
          }
        }
      }
      
      inputStream.close()
      outputStream.close()
      connection.disconnect()
      
      // 打印下载文件的详细信息
      Log.i("KioskManager", "=== 下载完成 ===")
      Log.i("KioskManager", "文件名: $fileName")
      Log.i("KioskManager", "文件路径: ${apkFile.absolutePath}")
      Log.i("KioskManager", "文件大小: ${apkFile.length()} 字节 (${apkFile.length() / 1024 / 1024} MB)")
      Log.i("KioskManager", "下载目录: ${downloadsDir.absolutePath}")
      Log.i("KioskManager", "文件是否存在: ${apkFile.exists()}")
      Log.i("KioskManager", "文件可读: ${apkFile.canRead()}")
      Log.i("KioskManager", "文件可写: ${apkFile.canWrite()}")
      Log.i("KioskManager", "==================")
      
      val result = Arguments.createMap()
      result.putString("filePath", apkFile.absolutePath)
      result.putString("fileName", fileName)
      result.putLong("fileSize", apkFile.length())
      promise.resolve(result)
      
    } catch (e: Exception) {
      Log.e("KioskManager", "Download failed: ${e.message}")
      promise.reject("E_DOWNLOAD_FAILED", "Download failed: ${e.message}")
    }
  }

  /**
   * 从 Content-Disposition HTTP 头中提取文件名
   */
  private fun extractFileNameFromContentDisposition(connection: HttpURLConnection, fallbackFileName: String): String {
    try {
      val contentDisposition = connection.getHeaderField("Content-Disposition")
      if (contentDisposition != null) {
        // Content-Disposition 格式: 
        // attachment; filename="example.apk"
        // attachment; filename=example.apk
        // attachment; filename*=UTF-8''example.apk
        
        // 先尝试匹配 filename*=UTF-8''... 格式
        val filenameStarPattern = Regex("filename\\*=([^;]+)", RegexOption.IGNORE_CASE)
        val starMatch = filenameStarPattern.find(contentDisposition)
        if (starMatch != null) {
          var extractedName = starMatch.groupValues[1].trim()
          
          // 处理 UTF-8'' 前缀
          if (extractedName.contains("''")) {
            extractedName = extractedName.substringAfter("''")
          }
          
          // 去掉引号
          extractedName = extractedName.trim('"', '\'')
          
          // URL 解码
          try {
            extractedName = java.net.URLDecoder.decode(extractedName, "UTF-8")
          } catch (e: Exception) {
            // 如果解码失败，使用原始名称
          }
          
          // 清理文件名
          extractedName = sanitizeFileName(extractedName)
          
          if (extractedName.isNotEmpty() && extractedName.endsWith(".apk", ignoreCase = true)) {
            Log.i("KioskManager", "从 Content-Disposition (filename*) 提取的文件名: $extractedName")
            return extractedName
          }
        }
        
        // 尝试匹配 filename="..." 或 filename=... 格式
        val filenamePattern = Regex("filename=([^;]+)", RegexOption.IGNORE_CASE)
        val match = filenamePattern.find(contentDisposition)
        if (match != null) {
          var extractedName = match.groupValues[1].trim()
          
          // 去掉引号
          extractedName = extractedName.trim('"', '\'')
          
          // 清理文件名
          extractedName = sanitizeFileName(extractedName)
          
          // 确保以 .apk 结尾
          if (extractedName.isNotEmpty() && extractedName.endsWith(".apk", ignoreCase = true)) {
            Log.i("KioskManager", "从 Content-Disposition (filename) 提取的文件名: $extractedName")
            return extractedName
          }
        }
      }
    } catch (e: Exception) {
      Log.w("KioskManager", "从 Content-Disposition 提取文件名失败: ${e.message}")
    }
    return fallbackFileName
  }

  /**
   * 清理文件名，移除不安全的字符
   */
  private fun sanitizeFileName(fileName: String): String {
    var cleaned = fileName
    // 移除不安全的字符，保留字母、数字、点、下划线、连字符
    cleaned = cleaned.replace(Regex("[^a-zA-Z0-9._-]"), "_")
    // 限制文件名长度
    if (cleaned.length > 255) {
      val extension = if (cleaned.endsWith(".apk", ignoreCase = true)) ".apk" else ""
      val nameWithoutExt = cleaned.substring(0, cleaned.length - extension.length)
      cleaned = "${nameWithoutExt.take(255 - extension.length)}$extension"
    }
    return cleaned
  }

  /**
   * 从 URL 中提取文件名
   * 如果 URL 中没有有效的文件名，则使用默认名称
   */
  private fun extractFileNameFromUrl(url: String): String {
    try {
      val urlObj = URL(url)
      var fileName = urlObj.path.substringAfterLast('/')
      
      // 去掉查询参数和锚点
      fileName = fileName.split('?')[0].split('#')[0]
      
      // 清理文件名
      fileName = sanitizeFileName(fileName)
      
      // 如果文件名为空或不以 .apk 结尾，使用默认名称
      if (fileName.isEmpty() || !fileName.endsWith(".apk", ignoreCase = true)) {
        val defaultName = "app_${System.currentTimeMillis()}.apk"
        Log.i("KioskManager", "无法从 URL 提取有效文件名，使用默认名称: $defaultName")
        return defaultName
      }
      
      Log.i("KioskManager", "从 URL 提取的文件名: $fileName")
      return fileName
    } catch (e: Exception) {
      Log.w("KioskManager", "提取文件名失败: ${e.message}，使用默认名称")
      return "app_${System.currentTimeMillis()}.apk"
    }
  }

  fun getDownloadedFiles(promise: Promise) {
    try {
      val context = reactContext
      val downloadsDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "apk_updates")
      
      if (!downloadsDir.exists()) {
        promise.resolve(Arguments.createArray())
        return
      }
      
      val files = downloadsDir.listFiles()?.filter { it.isFile && it.name.endsWith(".apk") } ?: emptyList()
      val fileList = Arguments.createArray()
      
      files.sortedByDescending { it.lastModified() }.forEach { file ->
        val fileInfo = Arguments.createMap()
        fileInfo.putString("fileName", file.name)
        fileInfo.putString("filePath", file.absolutePath)
        fileInfo.putLong("fileSize", file.length())
        fileInfo.putDouble("lastModified", file.lastModified().toDouble())
        fileInfo.putBoolean("canRead", file.canRead())
        fileInfo.putBoolean("canWrite", file.canWrite())
        fileList.pushMap(fileInfo)
      }
      
      Log.i("KioskManager", "=== 获取下载文件列表 ===")
      Log.i("KioskManager", "下载目录: ${downloadsDir.absolutePath}")
      Log.i("KioskManager", "找到 ${files.size} 个 APK 文件")
      files.forEach { file ->
        Log.i("KioskManager", "文件: ${file.name} (${file.length()} 字节)")
      }
      Log.i("KioskManager", "==================")
      
      promise.resolve(fileList)
    } catch (e: Exception) {
      Log.e("KioskManager", "Failed to get downloaded files: ${e.message}")
      promise.reject("E_GET_FILES_FAILED", "Failed to get downloaded files: ${e.message}")
    }
  }

  fun deleteDownloadedFile(filePath: String, promise: Promise) {
    try {
      val file = File(filePath)
      
      Log.i("KioskManager", "=== 删除文件 ===")
      Log.i("KioskManager", "文件路径: $filePath")
      Log.i("KioskManager", "文件存在: ${file.exists()}")
      Log.i("KioskManager", "文件大小: ${file.length()} 字节")
      Log.i("KioskManager", "==================")
      
      if (!file.exists()) {
        promise.reject("E_FILE_NOT_FOUND", "File not found: $filePath")
        return
      }
      
      if (!file.canWrite()) {
        promise.reject("E_FILE_NOT_WRITABLE", "File is not writable: $filePath")
        return
      }
      
      val deleted = file.delete()
      if (deleted) {
        Log.i("KioskManager", "文件删除成功: $filePath")
        promise.resolve(true)
      } else {
        Log.e("KioskManager", "文件删除失败: $filePath")
        promise.reject("E_DELETE_FAILED", "Failed to delete file: $filePath")
      }
    } catch (e: Exception) {
      Log.e("KioskManager", "Failed to delete file: ${e.message}")
      promise.reject("E_DELETE_FAILED", "Failed to delete file: ${e.message}")
    }
  }

  fun clearAllDownloadedFiles(promise: Promise) {
    try {
      val context = reactContext
      val downloadsDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "apk_updates")
      
      if (!downloadsDir.exists()) {
        promise.resolve(0)
        return
      }
      
      val files = downloadsDir.listFiles()?.filter { it.isFile && it.name.endsWith(".apk") } ?: emptyList()
      var deletedCount = 0
      
      Log.i("KioskManager", "=== 清空下载文件 ===")
      Log.i("KioskManager", "下载目录: ${downloadsDir.absolutePath}")
      Log.i("KioskManager", "找到 ${files.size} 个 APK 文件")
      
      files.forEach { file ->
        if (file.delete()) {
          deletedCount++
          Log.i("KioskManager", "已删除: ${file.name}")
        } else {
          Log.e("KioskManager", "删除失败: ${file.name}")
        }
      }
      
      Log.i("KioskManager", "成功删除 $deletedCount 个文件")
      Log.i("KioskManager", "==================")
      
      promise.resolve(deletedCount)
    } catch (e: Exception) {
      Log.e("KioskManager", "Failed to clear downloaded files: ${e.message}")
      promise.reject("E_CLEAR_FILES_FAILED", "Failed to clear downloaded files: ${e.message}")
    }
  }
}

