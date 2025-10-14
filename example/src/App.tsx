import { useState, useEffect } from 'react';
import {
  Text,
  View,
  StyleSheet,
  Alert,
  useColorScheme,
  ScrollView,
  Dimensions,
  TouchableOpacity,
  StatusBar,
  TextInput,
} from 'react-native';
import KioskManager, { type DownloadResult, type DownloadProgress } from 'react-native-kiosk-manager';

export default function App() {
  const [bootAutoStart, setBootAutoStart] = useState<boolean | null>(null);
  const [isDeviceOwner, setIsDeviceOwner] = useState<boolean | null>(null);
  const [isLockTaskPackageSetup, setIsLockTaskPackageSetup] = useState<
    boolean | null
  >(null);
  const [apkUrl, setApkUrl] = useState<string>('https://assets.driver-day.com/LATEST/apk/dd.apk');
  const [downloadResult, setDownloadResult] = useState<DownloadResult | null>(null);
  const [isInstalling, setIsInstalling] = useState<boolean>(false);
  const [downloadProgress, setDownloadProgress] = useState<DownloadProgress | null>(null);
  const [isDownloading, setIsDownloading] = useState<boolean>(false);
  const colorScheme = useColorScheme();
  const isDarkMode = colorScheme === 'dark';

  // 获取屏幕尺寸用于响应式设计
  const { width, height } = Dimensions.get('window');
  const isTablet = width >= 768;
  const isLandscape = width > height;

  // 设置下载进度监听器
  useEffect(() => {
    const handleProgress = (progress: DownloadProgress) => {
      setDownloadProgress(progress);
    };

    KioskManager.addDownloadProgressListener(handleProgress);

    return () => {
      KioskManager.removeDownloadProgressListener(handleProgress);
    };
  }, []);

  const handleStartKiosk = () => {
    KioskManager.startKiosk();
    Alert.alert('Success', 'Kiosk mode started');
  };

  const handleStopKiosk = () => {
    KioskManager.stopKiosk();
    Alert.alert('Success', 'Kiosk mode stopped');
  };

  const handleCheckBootAutoStart = async () => {
    try {
      const isEnabled = await KioskManager.isBootAutoStartEnabled();
      setBootAutoStart(isEnabled);
    } catch (error) {
      Alert.alert('Error', 'Failed to check boot auto start status');
    }
  };

  const handleRequestDeviceAdmin = async () => {
    try {
      await KioskManager.requestDeviceAdmin();
      Alert.alert('Success', 'Device admin requested');
    } catch (error) {
      Alert.alert('Error', 'Failed to request device admin');
    }
  };

  const handleSetupLockTaskPackage = async () => {
    try {
      await KioskManager.setupLockTaskPackage();
      Alert.alert('Success', 'Lock task package setup completed');
      setIsLockTaskPackageSetup(true);
    } catch (error) {
      Alert.alert(
        'Error',
        'Failed to setup lock task package. Make sure the app is device owner.'
      );
      setIsLockTaskPackageSetup(false);
    }
  };

  const handleDisableBootAutoStart = async () => {
    try {
      await KioskManager.enableBootAutoStart(false);
      Alert.alert('Success', 'Boot auto start disabled');
      setBootAutoStart(false);
    } catch (error) {
      Alert.alert('Error', 'Failed to disable boot auto start');
    }
  };

  const handleCheckDeviceOwner = async () => {
    try {
      const isOwner = await KioskManager.isDeviceOwner();
      setIsDeviceOwner(isOwner);
    } catch (error) {
      Alert.alert('Error', 'Failed to check device owner status');
    }
  };

  const handleClearDeviceOwner = async () => {
    try {
      Alert.alert(
        'Warning',
        'Are you sure you want to clear device owner? This will remove all device admin privileges.',
        [
          {
            text: 'Cancel',
            style: 'cancel',
          },
          {
            text: 'Clear',
            style: 'destructive',
            onPress: async () => {
              try {
                await KioskManager.clearDeviceOwner();
                Alert.alert('Success', 'Device owner cleared successfully');
                setIsDeviceOwner(false);
                setIsLockTaskPackageSetup(false);
              } catch (error) {
                Alert.alert('Error', 'Failed to clear device owner');
              }
            },
          },
        ]
      );
    } catch (error) {
      Alert.alert('Error', 'Failed to clear device owner');
    }
  };

  // APK 更新相关函数
  const handleDownloadApk = async () => {
    if (!apkUrl.trim()) {
      Alert.alert('Error', 'Please enter a valid APK URL');
      return;
    }

    setIsDownloading(true);
    setDownloadProgress(null);
    setDownloadResult(null);

    try {
      const result = await KioskManager.downloadApk(apkUrl);
      setDownloadResult(result);
      setDownloadProgress(null);
      Alert.alert('Success', `APK downloaded successfully!\nFile: ${result.fileName}\nSize: ${(result.fileSize / 1024 / 1024).toFixed(2)} MB`);
    } catch (error) {
      Alert.alert('Error', `Failed to download APK: ${error}`);
    } finally {
      setIsDownloading(false);
    }
  };

  const handleInstallApk = async () => {
    if (!downloadResult) {
      Alert.alert('Error', 'No APK file to install');
      return;
    }

    setIsInstalling(true);
    try {
      await KioskManager.installApk(downloadResult.filePath);
      Alert.alert('Success', 'APK installation started');
    } catch (error) {
      Alert.alert('Error', `Failed to install APK: ${error}`);
    } finally {
      setIsInstalling(false);
    }
  };

  const handleDownloadAndInstall = async () => {
    if (!apkUrl.trim()) {
      Alert.alert('Error', 'Please enter a valid APK URL');
      return;
    }

    setIsInstalling(true);
    try {
      await KioskManager.downloadAndInstallApk(apkUrl);
      Alert.alert('Success', 'APK download and installation started');
    } catch (error) {
      Alert.alert('Error', `Failed to download and install APK: ${error}`);
    } finally {
      setIsInstalling(false);
    }
  };

  const handleCheckInstallPermission = async () => {
    try {
      const hasPermission = await KioskManager.checkInstallPermission();
      Alert.alert('Install Permission', hasPermission ? 'Permission granted' : 'Permission required');
    } catch (error) {
      Alert.alert('Error', `Failed to check install permission: ${error}`);
    }
  };

  const handleRequestInstallPermission = async () => {
    try {
      await KioskManager.requestInstallPermission();
      Alert.alert('Success', 'Install permission request sent');
    } catch (error) {
      Alert.alert('Error', `Failed to request install permission: ${error}`);
    }
  };

  return (
    <View
      style={[
        styles.container,
        isDarkMode ? styles.darkContainer : styles.lightContainer,
      ]}
    >
      <StatusBar barStyle={isDarkMode ? 'light-content' : 'dark-content'} />
      <ScrollView
        style={styles.scrollView}
        contentContainerStyle={[
          styles.scrollContent,
          isTablet && styles.tabletContent,
          isLandscape && styles.landscapeContent,
        ]}
        showsVerticalScrollIndicator={false}
      >
        <View style={[styles.header, isTablet && styles.tabletHeader]}>
          <Text
            style={[
              styles.title,
              isDarkMode ? styles.darkText : styles.lightText,
            ]}
          >
            Kiosk Manager Example
          </Text>
          <Text
            style={[
              styles.subtitle,
              isDarkMode ? styles.darkSubtext : styles.lightSubtext,
            ]}
          >
            {isTablet ? '平板模式' : '手机模式'} •{' '}
            {isLandscape ? '横屏' : '竖屏'}
          </Text>
        </View>

        <View style={[styles.buttonGrid, isTablet && styles.tabletButtonGrid]}>
          <TouchableOpacity
            style={[
              styles.actionButton,
              styles.primaryButton,
              isDarkMode && styles.darkButton,
              isTablet && styles.tabletActionButton,
            ]}
            onPress={handleStartKiosk}
          >
            <Text style={[styles.buttonText, styles.primaryButtonText]}>
              启动Kiosk模式
            </Text>
          </TouchableOpacity>

          <TouchableOpacity
            style={[
              styles.actionButton,
              styles.secondaryButton,
              isDarkMode && styles.darkButton,
              isTablet && styles.tabletActionButton,
            ]}
            onPress={handleStopKiosk}
          >
            <Text style={[styles.buttonText, styles.secondaryButtonText]}>
              停止Kiosk模式
            </Text>
          </TouchableOpacity>

          <TouchableOpacity
            style={[
              styles.actionButton,
              styles.infoButton,
              isDarkMode && styles.darkButton,
              isTablet && styles.tabletActionButton,
            ]}
            onPress={handleCheckBootAutoStart}
          >
            <Text style={[styles.buttonText, styles.infoButtonText]}>
              检查开机自启
            </Text>
          </TouchableOpacity>

          <TouchableOpacity
            style={[
              styles.actionButton,
              styles.warningButton,
              isDarkMode && styles.darkButton,
              isTablet && styles.tabletActionButton,
            ]}
            onPress={handleRequestDeviceAdmin}
          >
            <Text style={[styles.buttonText, styles.warningButtonText]}>
              请求设备管理员
            </Text>
          </TouchableOpacity>

          <TouchableOpacity
            style={[
              styles.actionButton,
              styles.infoButton,
              isDarkMode && styles.darkButton,
              isTablet && styles.tabletActionButton,
            ]}
            onPress={handleCheckDeviceOwner}
          >
            <Text style={[styles.buttonText, styles.infoButtonText]}>
              检查设备所有者
            </Text>
          </TouchableOpacity>

          <TouchableOpacity
            style={[
              styles.actionButton,
              styles.successButton,
              isDarkMode && styles.darkButton,
              isTablet && styles.tabletActionButton,
            ]}
            onPress={handleSetupLockTaskPackage}
          >
            <Text style={[styles.buttonText, styles.successButtonText]}>
              设置锁定任务包
            </Text>
          </TouchableOpacity>

          <TouchableOpacity
            style={[
              styles.actionButton,
              styles.dangerButton,
              isDarkMode && styles.darkButton,
              isTablet && styles.tabletActionButton,
            ]}
            onPress={handleClearDeviceOwner}
          >
            <Text style={[styles.buttonText, styles.dangerButtonText]}>
              清除设备所有者
            </Text>
          </TouchableOpacity>

          <TouchableOpacity
            style={[
              styles.actionButton,
              styles.warningButton,
              isDarkMode && styles.darkButton,
              isTablet && styles.tabletActionButton,
            ]}
            onPress={handleDisableBootAutoStart}
          >
            <Text style={[styles.buttonText, styles.warningButtonText]}>
              禁用开机自启
            </Text>
          </TouchableOpacity>
        </View>

        <View
          style={[styles.statusSection, isTablet && styles.tabletStatusSection]}
        >
          <Text
            style={[
              styles.statusTitle,
              isDarkMode ? styles.darkText : styles.lightText,
            ]}
          >
            状态信息
          </Text>

          {bootAutoStart !== null && (
            <View
              style={[styles.statusItem, isDarkMode && styles.darkStatusItem]}
            >
              <Text
                style={[
                  styles.statusLabel,
                  isDarkMode ? styles.darkText : styles.lightText,
                ]}
              >
                开机自启:
              </Text>
              <Text
                style={[
                  styles.statusValue,
                  bootAutoStart ? styles.statusEnabled : styles.statusDisabled,
                  isDarkMode && styles.darkText,
                ]}
              >
                {bootAutoStart ? '已启用' : '已禁用'}
              </Text>
            </View>
          )}

          {isDeviceOwner !== null && (
            <View
              style={[styles.statusItem, isDarkMode && styles.darkStatusItem]}
            >
              <Text
                style={[
                  styles.statusLabel,
                  isDarkMode ? styles.darkText : styles.lightText,
                ]}
              >
                设备所有者:
              </Text>
              <Text
                style={[
                  styles.statusValue,
                  isDeviceOwner ? styles.statusEnabled : styles.statusDisabled,
                  isDarkMode && styles.darkText,
                ]}
              >
                {isDeviceOwner ? '已激活' : '未激活'}
              </Text>
            </View>
          )}

          {isLockTaskPackageSetup !== null && (
            <View
              style={[styles.statusItem, isDarkMode && styles.darkStatusItem]}
            >
              <Text
                style={[
                  styles.statusLabel,
                  isDarkMode ? styles.darkText : styles.lightText,
                ]}
              >
                锁定任务包:
              </Text>
              <Text
                style={[
                  styles.statusValue,
                  isLockTaskPackageSetup
                    ? styles.statusEnabled
                    : styles.statusDisabled,
                  isDarkMode && styles.darkText,
                ]}
              >
                {isLockTaskPackageSetup ? '已设置' : '未设置'}
              </Text>
            </View>
          )}
        </View>

        {/* APK 更新功能部分 */}
        <View
          style={[styles.section, isTablet && styles.tabletSection]}
        >
          <Text
            style={[
              styles.sectionTitle,
              isDarkMode ? styles.darkText : styles.lightText,
            ]}
          >
            APK 自动更新
          </Text>

          <View style={styles.inputContainer}>
            <Text
              style={[
                styles.inputLabel,
                isDarkMode ? styles.darkText : styles.lightText,
              ]}
            >
              APK 下载链接:
            </Text>
            <TextInput
              style={[
                styles.textInput,
                isDarkMode && styles.darkTextInput,
              ]}
              value={apkUrl}
              onChangeText={setApkUrl}
              placeholder="输入 APK 文件的 URL"
              placeholderTextColor={isDarkMode ? '#666' : '#999'}
              multiline
            />
          </View>

          <View style={[styles.buttonGrid, isTablet && styles.tabletButtonGrid]}>
            <TouchableOpacity
              style={[
                styles.compactButton,
                styles.primaryButton,
                isDarkMode && styles.darkButton,
                isDownloading && styles.disabledButton,
              ]}
              onPress={handleDownloadApk}
              disabled={isDownloading}
            >
              <Text style={[styles.compactButtonText, styles.primaryButtonText]}>
                {isDownloading ? '下载中...' : '下载 APK'}
              </Text>
            </TouchableOpacity>

            <TouchableOpacity
              style={[
                styles.compactButton,
                styles.successButton,
                isDarkMode && styles.darkButton,
                !downloadResult && styles.disabledButton,
              ]}
              onPress={handleInstallApk}
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
              <Text
                style={[
                  styles.progressText,
                  isDarkMode ? styles.darkText : styles.lightText,
                ]}
              >
                下载进度: {downloadProgress.progress}%
              </Text>
              <View style={styles.progressBarContainer}>
                <View
                  style={[
                    styles.progressBar,
                    { width: `${downloadProgress.progress}%` },
                  ]}
                />
              </View>
              <Text
                style={[
                  styles.progressDetails,
                  isDarkMode ? styles.darkText : styles.lightText,
                ]}
              >
                {((downloadProgress.bytesRead / 1024 / 1024).toFixed(2))} MB / {((downloadProgress.totalBytes / 1024 / 1024).toFixed(2))} MB
              </Text>
            </View>
          )}

          <View style={[styles.buttonGrid, isTablet && styles.tabletButtonGrid]}>
            <TouchableOpacity
              style={[
                styles.compactButton,
                styles.warningButton,
                isDarkMode && styles.darkButton,
                isInstalling && styles.disabledButton,
              ]}
              onPress={handleDownloadAndInstall}
              disabled={isInstalling}
            >
              <Text style={[styles.compactButtonText, styles.warningButtonText]}>
                {isInstalling ? '处理中...' : '下载并安装'}
              </Text>
            </TouchableOpacity>

            <TouchableOpacity
              style={[
                styles.compactButton,
                styles.infoButton,
                isDarkMode && styles.darkButton,
              ]}
              onPress={handleCheckInstallPermission}
            >
              <Text style={[styles.compactButtonText, styles.infoButtonText]}>
                检查安装权限
              </Text>
            </TouchableOpacity>

            <TouchableOpacity
              style={[
                styles.compactButton,
                styles.infoButton,
                isDarkMode && styles.darkButton,
              ]}
              onPress={handleRequestInstallPermission}
            >
              <Text style={[styles.compactButtonText, styles.infoButtonText]}>
                请求安装权限
              </Text>
            </TouchableOpacity>
          </View>

          {downloadResult && (
            <View
              style={[styles.statusItem, isDarkMode && styles.darkStatusItem]}
            >
              <Text
                style={[
                  styles.statusLabel,
                  isDarkMode ? styles.darkText : styles.lightText,
                ]}
              >
                下载结果:
              </Text>
              <Text
                style={[
                  styles.statusValue,
                  isDarkMode && styles.darkText,
                ]}
              >
                {downloadResult.fileName} ({(downloadResult.fileSize / 1024 / 1024).toFixed(2)} MB)
              </Text>
            </View>
          )}
        </View>
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  lightContainer: {
    backgroundColor: '#f8f9fa',
  },
  darkContainer: {
    backgroundColor: '#121212',
  },
  scrollView: {
    flex: 1,
  },
  scrollContent: {
    padding: 20,
    paddingBottom: 40,
  },
  tabletContent: {
    paddingHorizontal: 40,
    paddingVertical: 30,
  },
  landscapeContent: {
    paddingHorizontal: 30,
  },
  header: {
    alignItems: 'center',
    marginBottom: 30,
  },
  tabletHeader: {
    marginBottom: 40,
  },
  title: {
    fontSize: 28,
    fontWeight: 'bold',
    textAlign: 'center',
    marginBottom: 8,
  },
  subtitle: {
    fontSize: 16,
    textAlign: 'center',
    opacity: 0.7,
  },
  lightSubtext: {
    color: '#666666',
  },
  darkSubtext: {
    color: '#cccccc',
  },
  lightText: {
    color: '#212529',
  },
  darkText: {
    color: '#ffffff',
  },
  buttonGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    justifyContent: 'space-between',
    gap: 12,
    marginBottom: 16,
  },
  tabletButtonGrid: {
    gap: 16,
  },
  actionButton: {
    paddingVertical: 16,
    paddingHorizontal: 24,
    borderRadius: 12,
    alignItems: 'center',
    justifyContent: 'center',
    minHeight: 56,
    shadowColor: '#000',
    shadowOffset: {
      width: 0,
      height: 2,
    },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 3,
  },
  compactButton: {
    paddingVertical: 12,
    paddingHorizontal: 16,
    borderRadius: 8,
    alignItems: 'center',
    justifyContent: 'center',
    minHeight: 44,
    flex: 1,
    shadowColor: '#000',
    shadowOffset: {
      width: 0,
      height: 1,
    },
    shadowOpacity: 0.08,
    shadowRadius: 2,
    elevation: 2,
  },
  tabletActionButton: {
    flex: 1,
    minWidth: '45%',
    maxWidth: '48%',
  },
  buttonText: {
    fontSize: 16,
    fontWeight: '600',
    textAlign: 'center',
  },
  compactButtonText: {
    fontSize: 14,
    fontWeight: '600',
    textAlign: 'center',
  },
  // 按钮颜色变体
  primaryButton: {
    backgroundColor: '#007AFF',
  },
  primaryButtonText: {
    color: '#ffffff',
  },
  secondaryButton: {
    backgroundColor: '#6C757D',
  },
  secondaryButtonText: {
    color: '#ffffff',
  },
  successButton: {
    backgroundColor: '#28A745',
  },
  successButtonText: {
    color: '#ffffff',
  },
  warningButton: {
    backgroundColor: '#FFC107',
  },
  warningButtonText: {
    color: '#212529',
  },
  dangerButton: {
    backgroundColor: '#DC3545',
  },
  dangerButtonText: {
    color: '#ffffff',
  },
  infoButton: {
    backgroundColor: '#17A2B8',
  },
  infoButtonText: {
    color: '#ffffff',
  },
  darkButton: {
    shadowOpacity: 0.3,
    shadowRadius: 6,
    elevation: 5,
  },
  statusSection: {
    marginTop: 30,
    padding: 20,
    borderRadius: 12,
    backgroundColor: 'rgba(0,0,0,0.05)',
  },
  tabletStatusSection: {
    marginTop: 40,
    padding: 30,
  },
  statusTitle: {
    fontSize: 20,
    fontWeight: 'bold',
    marginBottom: 16,
    textAlign: 'center',
  },
  statusItem: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: 8,
    paddingHorizontal: 12,
    marginVertical: 2,
    borderRadius: 6,
    backgroundColor: 'rgba(255,255,255,0.7)',
  },
  darkStatusItem: {
    backgroundColor: 'rgba(255,255,255,0.1)',
  },
  statusLabel: {
    fontSize: 14,
    fontWeight: '500',
    flex: 1,
  },
  statusValue: {
    fontSize: 14,
    fontWeight: '600',
    paddingHorizontal: 8,
    paddingVertical: 3,
    borderRadius: 4,
  },
  statusEnabled: {
    backgroundColor: '#d4edda',
    color: '#155724',
  },
  statusDisabled: {
    backgroundColor: '#f8d7da',
    color: '#721c24',
  },
  
  // APK 更新相关样式
  section: {
    marginTop: 24,
    padding: 16,
    borderRadius: 12,
    backgroundColor: 'rgba(0,0,0,0.05)',
  },
  progressContainer: {
    marginTop: 16,
    marginBottom: 16,
    padding: 16,
    backgroundColor: 'rgba(0,0,0,0.05)',
    borderRadius: 8,
  },
  progressText: {
    fontSize: 14,
    fontWeight: '600',
    marginBottom: 8,
    textAlign: 'center',
  },
  progressBarContainer: {
    height: 6,
    backgroundColor: 'rgba(0,0,0,0.1)',
    borderRadius: 3,
    overflow: 'hidden',
    marginBottom: 6,
  },
  progressBar: {
    height: '100%',
    backgroundColor: '#007AFF',
    borderRadius: 3,
  },
  progressDetails: {
    fontSize: 12,
    textAlign: 'center',
    opacity: 0.7,
  },
  tabletSection: {
    marginTop: 32,
    padding: 24,
  },
  sectionTitle: {
    fontSize: 20,
    fontWeight: 'bold',
    marginBottom: 16,
    textAlign: 'center',
  },
  inputContainer: {
    marginBottom: 16,
  },
  inputLabel: {
    fontSize: 16,
    fontWeight: '600',
    marginBottom: 8,
  },
  textInput: {
    borderWidth: 1,
    borderColor: '#ddd',
    borderRadius: 8,
    padding: 12,
    fontSize: 16,
    backgroundColor: '#fff',
    minHeight: 80,
    textAlignVertical: 'top',
  },
  darkTextInput: {
    borderColor: '#555',
    backgroundColor: '#333',
    color: '#fff',
  },
  buttonRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 12,
    gap: 12,
  },
  disabledButton: {
    opacity: 0.5,
  },
});
