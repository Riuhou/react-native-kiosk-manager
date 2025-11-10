import { Dimensions, Text, TextInput, TouchableOpacity, View } from 'react-native';
import type { DownloadProgress, DownloadResult, InstallStatus } from 'react-native-kiosk-manager';
import { useAppStyles } from '../utils/styles';

interface ApkUpdateTabProps {
  apkUrl: string;
  packageName: string;
  downloadResult: DownloadResult | null;
  isDownloading: boolean;
  isInstalling: boolean;
  isLaunching: boolean;
  isCheckingInstall: boolean;
  isAppInstalledStatus: boolean | null;
  downloadProgress: DownloadProgress | null;
  installStatus: InstallStatus | null;
  ringerMode: 'silent' | 'vibrate' | 'normal' | null;
  hasDndAccess: boolean | null;
  onApkUrlChange: (url: string) => void;
  onPackageNameChange: (name: string) => void;
  onDownloadApk: () => void;
  onInstallApk: (filePath: string, fileName: string) => void;
  onDownloadAndInstall: () => void;
  onDownloadAndSilentInstall: () => void;
  onDownloadAndSilentInstallAndLaunch: () => void;
  onCheckInstallPermission: () => void;
  onRequestInstallPermission: () => void;
  onCheckAppInstalled: () => void;
  onLaunchApp: () => void;
}

export default function ApkUpdateTab(props: ApkUpdateTabProps) {
  const {
    apkUrl,
    packageName,
    downloadResult,
    isDownloading,
    isInstalling,
    isLaunching,
    isCheckingInstall,
    isAppInstalledStatus,
    downloadProgress,
    installStatus,
    ringerMode,
    hasDndAccess,
    onApkUrlChange,
    onPackageNameChange,
    onDownloadApk,
    onInstallApk,
    onDownloadAndInstall,
    onDownloadAndSilentInstall,
    onDownloadAndSilentInstallAndLaunch,
    onCheckInstallPermission,
    onRequestInstallPermission,
    onCheckAppInstalled,
    onLaunchApp,
  } = props;

  const { isDarkMode, styles, colors } = useAppStyles();
  const { width } = Dimensions.get('window');
  const isTablet = width >= 768;

  const getStatusText = (status: string) => {
    const statusMap: Record<string, string> = {
      installing: '正在安装...',
      installed: '安装成功',
      launching: '正在启动...',
      launched: '启动成功',
      failed: '安装失败',
      cancelled: '已取消',
      blocked: '被阻止',
      conflict: '安装冲突',
      incompatible: '不兼容',
      invalid: '无效APK',
      storage_error: '存储空间不足',
      timeout: '超时',
      error: '错误',
      launch_failed: '启动失败',
    };
    return statusMap[status] || status;
  };

  const getStatusColor = (status: string) => {
    if (status === 'installed' || status === 'launched') return '#4CAF50';
    if (['failed', 'cancelled', 'blocked', 'conflict', 'incompatible', 'invalid', 'storage_error', 'launch_failed'].includes(status)) {
      return '#F44336';
    }
    return '#2196F3';
  };

  return (
    <View style={[styles.section, isTablet && { marginTop: 32, padding: 24 }]}>
      <Text style={styles.sectionTitle}>APK 自动更新</Text>

      <View style={styles.inputContainer}>
        <Text style={styles.inputLabel}>APK 下载链接:</Text>
        <TextInput
          style={styles.textInput}
          value={apkUrl}
          onChangeText={onApkUrlChange}
          placeholder="输入 APK 文件的 URL"
          placeholderTextColor={isDarkMode ? '#666' : '#999'}
          multiline
        />
      </View>

      <View style={styles.statusItem}>
        <Text style={styles.statusLabel}>系统铃声模式:</Text>
        <Text style={styles.statusValue}>{ringerMode ?? '-'}</Text>
      </View>
      <View style={styles.statusItem}>
        <Text style={styles.statusLabel}>免打扰权限:</Text>
        <Text style={[styles.statusValue, hasDndAccess ? styles.statusEnabled : styles.statusDisabled]}>
          {hasDndAccess ? '已授权' : '未授权'}
        </Text>
      </View>

      <View style={[styles.buttonGrid, isTablet && { gap: 16 }]}>
        <TouchableOpacity
          style={[styles.compactButton, styles.primaryButton, isDownloading && styles.disabledButton]}
          onPress={onDownloadApk}
          disabled={isDownloading}
        >
          <Text style={[styles.compactButtonText, styles.primaryButtonText]}>
            {isDownloading ? '下载中...' : '下载 APK'}
          </Text>
        </TouchableOpacity>

        <TouchableOpacity
          style={[styles.compactButton, styles.successButton, (!downloadResult || isInstalling) && styles.disabledButton]}
          onPress={() => {
            if (downloadResult) {
              onInstallApk(downloadResult.filePath, downloadResult.fileName);
            }
          }}
          disabled={!downloadResult || isInstalling}
        >
          <Text style={[styles.compactButtonText, styles.successButtonText]}>
            {isInstalling ? '安装中...' : '安装 APK'}
          </Text>
        </TouchableOpacity>
      </View>

      {/* 下载进度显示 */}
      {isDownloading && downloadProgress && (
        <View style={styles.progressContainer}>
          <Text style={styles.progressText}>下载进度: {downloadProgress.progress}%</Text>
          <View style={styles.progressBarContainer}>
            <View style={[styles.progressBar, { width: `${downloadProgress.progress}%` }]} />
          </View>
          <Text style={styles.progressDetails}>
            {(downloadProgress.bytesRead / 1024 / 1024).toFixed(2)} MB /{' '}
            {(downloadProgress.totalBytes / 1024 / 1024).toFixed(2)} MB
          </Text>
        </View>
      )}

      {/* 安装状态显示 */}
      {installStatus && (
        <View style={[styles.statusItem, { marginTop: 10, marginBottom: 10, padding: 12 }]}>
          <Text style={[styles.statusLabel, { marginBottom: 5 }]}>安装状态:</Text>
          <Text style={[styles.statusValue, { color: getStatusColor(installStatus.status), fontWeight: 'bold', marginBottom: 5 }]}>
            {getStatusText(installStatus.status)}
          </Text>
          {installStatus.message && (
            <Text style={[styles.progressDetails, { marginTop: 3 }]}>{installStatus.message}</Text>
          )}
          {installStatus.packageName && (
            <Text style={[styles.progressDetails, { marginTop: 3 }]}>包名: {installStatus.packageName}</Text>
          )}
          {installStatus.progress !== undefined && (
            <View style={{ marginTop: 10 }}>
              <View style={styles.progressBarContainer}>
                <View style={[styles.progressBar, { width: `${installStatus.progress}%` }]} />
              </View>
              <Text style={[styles.progressDetails, { marginTop: 5, textAlign: 'center' }]}>
                {installStatus.progress}%
              </Text>
            </View>
          )}
        </View>
      )}

      <View style={[styles.buttonGrid, isTablet && { gap: 16 }]}>
        <TouchableOpacity
          style={[styles.compactButton, styles.warningButton, isInstalling && styles.disabledButton]}
          onPress={onDownloadAndInstall}
          disabled={isInstalling}
        >
          <Text style={[styles.compactButtonText, styles.warningButtonText]}>
            {isInstalling ? '处理中...' : '下载并安装'}
          </Text>
        </TouchableOpacity>

        <TouchableOpacity
          style={[styles.compactButton, styles.dangerButton, isInstalling && styles.disabledButton]}
          onPress={onDownloadAndSilentInstall}
          disabled={isInstalling}
        >
          <Text style={[styles.compactButtonText, styles.dangerButtonText]}>
            {isInstalling ? '处理中...' : '下载并静默安装'}
          </Text>
        </TouchableOpacity>

        <TouchableOpacity
          style={[styles.compactButton, styles.successButton, isInstalling && styles.disabledButton]}
          onPress={onDownloadAndSilentInstallAndLaunch}
          disabled={isInstalling}
        >
          <Text style={[styles.compactButtonText, styles.successButtonText]}>
            {isInstalling ? '处理中...' : '下载并静默安装并启动'}
          </Text>
        </TouchableOpacity>

        <TouchableOpacity style={[styles.compactButton, styles.infoButton]} onPress={onCheckInstallPermission}>
          <Text style={[styles.compactButtonText, styles.infoButtonText]}>检查安装权限</Text>
        </TouchableOpacity>

        <TouchableOpacity style={[styles.compactButton, styles.infoButton]} onPress={onRequestInstallPermission}>
          <Text style={[styles.compactButtonText, styles.infoButtonText]}>请求安装权限</Text>
        </TouchableOpacity>
      </View>

      {downloadResult && (
        <View style={styles.statusItem}>
          <Text style={styles.statusLabel}>下载结果:</Text>
          <Text style={styles.statusValue}>
            {downloadResult.fileName} ({(downloadResult.fileSize / 1024 / 1024).toFixed(2)} MB)
          </Text>
        </View>
      )}

      {/* 启动应用功能 */}
      <View style={{ height: 1, backgroundColor: isDarkMode ? '#333' : '#e0e0e0', marginVertical: 20 }} />
      <Text style={[styles.sectionTitle, { marginTop: 10, marginBottom: 10, fontSize: 18 }]}>启动应用</Text>
      <Text style={[styles.inputLabel, { fontSize: 12, marginBottom: 10, color: colors.subtext }]}>
        说明: 输入应用包名来启动已安装的应用
      </Text>
      <Text style={[styles.inputLabel, { fontSize: 12, marginBottom: 10, color: colors.subtext }]}>
        示例: com.android.settings (设置), com.android.chrome (Chrome浏览器)
      </Text>

      <View style={styles.inputContainer}>
        <Text style={styles.inputLabel}>应用包名:</Text>
        <TextInput
          style={[styles.textInput, { minHeight: 44 }]}
          value={packageName}
          onChangeText={onPackageNameChange}
          placeholder="输入应用包名，例如: com.android.settings"
          placeholderTextColor={isDarkMode ? '#666' : '#999'}
        />
      </View>

      {isAppInstalledStatus !== null && (
        <View style={[styles.statusItem, { marginTop: 10, marginBottom: 10 }]}>
          <Text style={styles.statusLabel}>安装状态:</Text>
          <Text style={[styles.statusValue, isAppInstalledStatus ? styles.statusEnabled : styles.statusDisabled]}>
            {isAppInstalledStatus ? '已安装' : '未安装'}
          </Text>
        </View>
      )}

      <View style={[styles.buttonGrid, isTablet && { gap: 16 }, { marginTop: 10 }]}>
        <TouchableOpacity
          style={[styles.compactButton, styles.infoButton, isCheckingInstall && styles.disabledButton]}
          onPress={onCheckAppInstalled}
          disabled={isCheckingInstall}
        >
          <Text style={[styles.compactButtonText, styles.infoButtonText]}>
            {isCheckingInstall ? '检查中...' : '检查安装状态'}
          </Text>
        </TouchableOpacity>

        <TouchableOpacity
          style={[styles.compactButton, styles.primaryButton, (isLaunching || isCheckingInstall) && styles.disabledButton]}
          onPress={onLaunchApp}
          disabled={isLaunching || isCheckingInstall}
        >
          <Text style={[styles.compactButtonText, styles.primaryButtonText]}>
            {isLaunching ? '启动中...' : '启动应用'}
          </Text>
        </TouchableOpacity>
      </View>
    </View>
  );
}

