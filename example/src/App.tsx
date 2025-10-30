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
import KioskManager, {
  type DownloadResult,
  type DownloadProgress,
  type DownloadedFile,
} from 'react-native-kiosk-manager';

export default function App() {
  const [bootAutoStart, setBootAutoStart] = useState<boolean | null>(null);
  const [isDeviceOwner, setIsDeviceOwner] = useState<boolean | null>(null);
  const [isLockTaskPackageSetup, setIsLockTaskPackageSetup] = useState<
    boolean | null
  >(null);
  const [apkUrl, setApkUrl] = useState<string>(
    'https://assets.driver-day.com/LATEST/apk/dd.apk'
  );
  const [downloadResult, setDownloadResult] = useState<DownloadResult | null>(
    null
  );
  const [isInstalling, setIsInstalling] = useState<boolean>(false);
  const [downloadProgress, setDownloadProgress] =
    useState<DownloadProgress | null>(null);
  const [isDownloading, setIsDownloading] = useState<boolean>(false);
  const [downloadedFiles, setDownloadedFiles] = useState<DownloadedFile[]>([]);
  const [isLoadingFiles, setIsLoadingFiles] = useState<boolean>(false);
  const colorScheme = useColorScheme();
  const isDarkMode = colorScheme === 'dark';

  // 亮度/音量演示相关状态
  const [hasWriteSettings, setHasWriteSettings] = useState<boolean | null>(null);
  const [systemBrightness, setSystemBrightness] = useState<number | null>(null); // 0-255
  const [appBrightness, setAppBrightness] = useState<number | null>(null); // -1 或 0-1
  const audioStreams: Array<{
    key: 'music' | 'ring' | 'alarm' | 'notification' | 'system' | 'voice_call' | 'dtmf';
    label: string;
  }> = [
    { key: 'music', label: '媒体(music)' },
    { key: 'ring', label: '铃声(ring)' },
    { key: 'alarm', label: '闹钟(alarm)' },
    { key: 'notification', label: '通知(notification)' },
    { key: 'system', label: '系统(system)' },
    { key: 'voice_call', label: '通话(voice_call)' },
    { key: 'dtmf', label: '拨号(dtmf)' },
  ];
  const [volumes, setVolumes] = useState<Record<string, number>>({}); // 0-1 比例
  const [globalVolume, setGlobalVolume] = useState<number | null>(null);
  const [mutedMap, setMutedMap] = useState<Record<string, boolean>>({});
  const [globalMuted, setGlobalMuted] = useState<boolean | null>(null);
  const [ringerMode, setRingerMode] = useState<'silent' | 'vibrate' | 'normal' | null>(null);
  const [hasDndAccess, setHasDndAccess] = useState<boolean | null>(null);
  const [streamsOpen, setStreamsOpen] = useState<boolean>(false);

  // 获取屏幕尺寸用于响应式设计
  const { width, height } = Dimensions.get('window');
  const isTablet = width >= 768;
  const isLandscape = width > height;
  
  // 选项卡
  type TabKey = 'kiosk' | 'update' | 'files' | 'av';
  const [activeTab, setActiveTab] = useState<TabKey>('kiosk');

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

  // 初始化读取亮度与音量
  useEffect(() => {
    const init = async () => {
      try {
        const has = await KioskManager.hasWriteSettingsPermission();
        setHasWriteSettings(has);
      } catch {}
      try {
        const sb = await KioskManager.getSystemBrightness();
        setSystemBrightness(sb);
      } catch {}
      try {
        const ab = await KioskManager.getAppBrightness();
        setAppBrightness(ab);
      } catch {}
      try {
        const entries = await Promise.all(
          audioStreams.map(async (s) => {
            try {
              const v = await KioskManager.getVolume(s.key);
              return [s.key, v] as const;
            } catch {
              return [s.key, 0] as const;
            }
          })
        );
        const map: Record<string, number> = {};
        entries.forEach(([k, v]) => (map[k] = v));
        setVolumes(map);
      } catch {}
      try {
        const gv = await KioskManager.getGlobalVolume();
        setGlobalVolume(gv);
      } catch {}
      try {
        const mutes = await Promise.all(
          audioStreams.map(async (s) => {
            try {
              const m = await KioskManager.isMuted(s.key);
              return [s.key, !!m] as const;
            } catch {
              return [s.key, false] as const;
            }
          })
        );
        const mm: Record<string, boolean> = {};
        mutes.forEach(([k, v]) => (mm[k] = v));
        setMutedMap(mm);
      } catch {}
      try {
        const gm = await KioskManager.isGlobalMuted();
        setGlobalMuted(!!gm);
      } catch {}
      try {
        const rm = await KioskManager.getRingerMode();
        setRingerMode(rm);
      } catch {}
      try {
        const dnd = await KioskManager.hasNotificationPolicyAccess();
        setHasDndAccess(dnd);
      } catch {}
    };
    init();
    // 启动系统亮度/音量观察
    try { KioskManager.startObservingSystemAv(); } catch {}

    // 订阅系统事件
    const onBrightness = (v: number) => { setSystemBrightness(v); };
    const onVolume = (d: { stream: string; index: number; max: number; value: number }) => {
      setVolumes((prev) => ({ ...prev, [d.stream]: Math.max(0, Math.min(1, d.value)) }));
    };
    const onGlobalVolume = (v: number) => { setGlobalVolume(Math.max(0, Math.min(1, v))); };
    const onRinger = (m: 'silent' | 'vibrate' | 'normal') => { setRingerMode(m); };
    KioskManager.addSystemBrightnessListener(onBrightness);
    KioskManager.addVolumeChangedListener(onVolume);
    KioskManager.addGlobalVolumeChangedListener(onGlobalVolume);
    KioskManager.addRingerModeChangedListener(onRinger);

    return () => {
      try { KioskManager.stopObservingSystemAv(); } catch {}
      KioskManager.removeSystemBrightnessListener(onBrightness);
      KioskManager.removeVolumeChangedListener(onVolume);
      KioskManager.removeGlobalVolumeChangedListener(onGlobalVolume);
      KioskManager.removeRingerModeChangedListener(onRinger);
    };
  }, []);

  // 亮度与音量操作
  const handleRequestWriteSettings = async () => {
    try {
      await KioskManager.requestWriteSettingsPermission();
      const has = await KioskManager.hasWriteSettingsPermission();
      setHasWriteSettings(has);
    } catch (e) {
      Alert.alert('Error', '请求系统写入设置权限失败');
    }
  };

  const handleSetSystemBrightness = async (v: number) => {
    try {
      const clamped = Math.max(0, Math.min(255, Math.floor(v)));
      const ok = await KioskManager.setSystemBrightness(clamped);
      if (ok) setSystemBrightness(clamped);
      else Alert.alert('Error', '设置系统亮度失败');
    } catch (e) {
      Alert.alert('Error', '设置系统亮度失败');
    }
  };

  const handleSetAppBrightness = (v: number) => {
    const clamped = Math.max(0, Math.min(1, v));
    try {
      KioskManager.setAppBrightness(clamped);
      setAppBrightness(clamped);
    } catch (e) {
      Alert.alert('Error', '设置应用亮度失败');
    }
  };

  const handleSetVolume = async (
    stream: 'music' | 'ring' | 'alarm' | 'notification' | 'system' | 'voice_call' | 'dtmf',
    v: number
  ) => {
    const clamped = Math.max(0, Math.min(1, v));
    try {
      await KioskManager.setVolume(stream, clamped);
      setVolumes((prev) => ({ ...prev, [stream]: clamped }));
    } catch (e) {
      Alert.alert('Error', `设置音量失败: ${stream}`);
    }
  };

  const handleToggleMute = async (
    stream: 'music' | 'ring' | 'alarm' | 'notification' | 'system' | 'voice_call' | 'dtmf'
  ) => {
    try {
      const target = !(mutedMap[stream] ?? false);
      await KioskManager.setMute(stream, target);
      setMutedMap((prev) => ({ ...prev, [stream]: target }));
    } catch (e) {
      Alert.alert('Error', `设置静音失败: ${stream}`);
    }
  };

  const handleToggleGlobalMute = async () => {
    try {
      const target = !globalMuted;
      await KioskManager.setGlobalMute(target);
      setGlobalMuted(target);
    } catch (e) {
      Alert.alert('Error', '设置全局静音失败');
    }
  };

  const handleToggleSystemMute = async () => {
    try {
      const current = (await KioskManager.getRingerMode()) as 'silent' | 'vibrate' | 'normal';
      if (current === 'normal') {
        const granted = await KioskManager.hasNotificationPolicyAccess();
        if (!granted) {
          await KioskManager.requestNotificationPolicyAccess();
          Alert.alert('提示', '请在设置中授予“免打扰权限”，返回应用后再次点击');
          return;
        }
        await KioskManager.setRingerMode('silent');
        setRingerMode('silent');
      } else {
        await KioskManager.setRingerMode('normal');
        setRingerMode('normal');
      }
    } catch (e) {
      Alert.alert('Error', '切换系统静音失败');
    }
  };

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

      // 在控制台打印下载结果
      console.log('=== 下载完成 ===');
      console.log('文件名:', result.fileName);
      console.log('文件路径:', result.filePath);
      console.log('文件大小:', result.fileSize, '字节');
      console.log(
        '文件大小 (MB):',
        (result.fileSize / 1024 / 1024).toFixed(2),
        'MB'
      );
      console.log('==================');

      Alert.alert(
        'Success',
        `APK downloaded successfully!\nFile: ${result.fileName}\nSize: ${(result.fileSize / 1024 / 1024).toFixed(2)} MB`
      );
    } catch (error) {
      console.error('下载失败:', error);
      Alert.alert('Error', `Failed to download APK: ${error}`);
    } finally {
      setIsDownloading(false);
    }
  };


  const handleDownloadAndInstall = async () => {
    if (!apkUrl.trim()) {
      Alert.alert('Error', 'Please enter a valid APK URL');
      return;
    }

    setIsInstalling(true);
    try {
      console.log('=== 开始下载并安装 ===');
      console.log('下载URL:', apkUrl);
      console.log('==================');

      await KioskManager.downloadAndInstallApk(apkUrl);

      console.log('=== 下载并安装完成 ===');
      console.log('已启动系统安装界面');
      console.log('==================');

      Alert.alert('Success', 'APK download and installation started');
    } catch (error) {
      console.error('下载并安装失败:', error);
      Alert.alert('Error', `Failed to download and install APK: ${error}`);
    } finally {
      setIsInstalling(false);
    }
  };

  const handleCheckInstallPermission = async () => {
    try {
      const hasPermission = await KioskManager.checkInstallPermission();
      Alert.alert(
        'Install Permission',
        hasPermission ? 'Permission granted' : 'Permission required'
      );
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

  // 文件管理相关函数
  const handleGetDownloadedFiles = async () => {
    setIsLoadingFiles(true);
    try {
      const files = await KioskManager.getDownloadedFiles();
      setDownloadedFiles(files);

      console.log('=== 获取下载文件列表 ===');
      console.log('找到', files.length, '个文件');
      files.forEach((file, index) => {
        console.log(`文件 ${index + 1}:`, file.fileName);
        console.log('  路径:', file.filePath);
        console.log('  大小:', file.fileSize, '字节');
        console.log(
          '  修改时间:',
          new Date(file.lastModified).toLocaleString()
        );
        console.log('  可读:', file.canRead, '可写:', file.canWrite);
      });
      console.log('==================');

      Alert.alert('Success', `Found ${files.length} downloaded files`);
    } catch (error) {
      console.error('获取文件列表失败:', error);
      Alert.alert('Error', `Failed to get downloaded files: ${error}`);
    } finally {
      setIsLoadingFiles(false);
    }
  };

  const handleDeleteFile = async (filePath: string, fileName: string) => {
    try {
      console.log('=== 删除文件 ===');
      console.log('文件路径:', filePath);
      console.log('文件名:', fileName);
      console.log('==================');

      await KioskManager.deleteDownloadedFile(filePath);

      // 更新文件列表
      const updatedFiles = downloadedFiles.filter(
        (file) => file.filePath !== filePath
      );
      setDownloadedFiles(updatedFiles);

      console.log('文件删除成功:', fileName);
      Alert.alert('Success', `File "${fileName}" deleted successfully`);
    } catch (error) {
      console.error('删除文件失败:', error);
      Alert.alert('Error', `Failed to delete file: ${error}`);
    }
  };

  const handleClearAllFiles = async () => {
    try {
      console.log('=== 清空所有文件 ===');
      console.log('当前文件数量:', downloadedFiles.length);
      console.log('==================');

      const deletedCount = await KioskManager.clearAllDownloadedFiles();
      setDownloadedFiles([]);

      console.log('成功删除', deletedCount, '个文件');
      Alert.alert('Success', `Cleared ${deletedCount} files successfully`);
    } catch (error) {
      console.error('清空文件失败:', error);
      Alert.alert('Error', `Failed to clear files: ${error}`);
    }
  };

  const handleInstallApk = async (filePath: string, fileName: string) => {
    try {
      console.log('=== 安装APK ===');
      console.log('文件路径:', filePath);
      console.log('文件名:', fileName);
      console.log('==================');

      // 检查安装权限
      const hasPermission = await KioskManager.checkInstallPermission();
      if (!hasPermission) {
        Alert.alert(
          '需要安装权限',
          '请先授予应用安装未知应用的权限',
          [
            { text: '取消', style: 'cancel' },
            { 
              text: '去设置', 
              onPress: async () => {
                try {
                  await KioskManager.requestInstallPermission();
                } catch (error) {
                  console.error('请求安装权限失败:', error);
                }
              }
            }
          ]
        );
        return;
      }

      // 开始安装
      await KioskManager.installApk(filePath);
      
      console.log('APK安装启动成功');
      Alert.alert('Success', `开始安装 ${fileName}`);
    } catch (error) {
      console.error('安装APK失败:', error);
      Alert.alert('Error', `Failed to install APK: ${error}`);
    }
  };

  const handleSilentInstallApk = async (filePath: string, fileName: string) => {
    try {
      console.log('=== 静默安装APK ===');
      console.log('文件路径:', filePath);
      console.log('文件名:', fileName);
      console.log('==================');

      // 检查设备所有者权限
      const isOwner = await KioskManager.isDeviceOwner();
      if (!isOwner) {
        Alert.alert(
          '需要设备所有者权限',
          '静默安装需要设备所有者权限，请先设置设备所有者',
          [
            { text: '取消', style: 'cancel' },
            { 
              text: '去设置', 
              onPress: async () => {
                try {
                  await KioskManager.requestDeviceAdmin();
                } catch (error) {
                  console.error('请求设备管理员权限失败:', error);
                }
              }
            }
          ]
        );
        return;
      }

      // 开始静默安装
      await KioskManager.silentInstallApk(filePath);
      
      console.log('APK静默安装启动成功');
      Alert.alert('Success', `开始静默安装 ${fileName}`);
    } catch (error) {
      console.error('静默安装APK失败:', error);
      Alert.alert('Error', `Failed to silent install APK: ${error}`);
    }
  };

  const handleSilentInstallAndLaunchApk = async (filePath: string, fileName: string) => {
    try {
      console.log('=== 静默安装并启动APK ===');
      console.log('文件路径:', filePath);
      console.log('文件名:', fileName);
      console.log('==================');

      // 检查设备所有者权限
      const isOwner = await KioskManager.isDeviceOwner();
      if (!isOwner) {
        Alert.alert(
          '需要设备所有者权限',
          '静默安装需要设备所有者权限，请先设置设备所有者',
          [
            { text: '取消', style: 'cancel' },
            { 
              text: '去设置', 
              onPress: async () => {
                try {
                  await KioskManager.requestDeviceAdmin();
                } catch (error) {
                  console.error('请求设备管理员权限失败:', error);
                }
              }
            }
          ]
        );
        return;
      }

      // 开始静默安装并启动
      await KioskManager.silentInstallAndLaunchApk(filePath);
      
      console.log('APK静默安装并启动启动成功');
      Alert.alert('Success', `开始静默安装并启动 ${fileName}`);
    } catch (error) {
      console.error('静默安装并启动APK失败:', error);
      Alert.alert('Error', `Failed to silent install and launch APK: ${error}`);
    }
  };

  const handleSystemSilentInstallApk = async (filePath: string, fileName: string) => {
    try {
      console.log('=== 系统级静默安装APK ===');
      console.log('文件路径:', filePath);
      console.log('文件名:', fileName);
      console.log('==================');

      // 检查设备所有者权限
      const isOwner = await KioskManager.isDeviceOwner();
      if (!isOwner) {
        Alert.alert(
          '需要设备所有者权限',
          '系统级静默安装需要设备所有者权限，请先设置设备所有者',
          [
            { text: '取消', style: 'cancel' },
            { 
              text: '去设置', 
              onPress: async () => {
                try {
                  await KioskManager.requestDeviceAdmin();
                } catch (error) {
                  console.error('请求设备管理员权限失败:', error);
                }
              }
            }
          ]
        );
        return;
      }

      // 开始系统级静默安装
      await KioskManager.systemSilentInstallApk(filePath);
      
      console.log('APK系统级静默安装启动成功');
      Alert.alert('Success', `开始系统级静默安装 ${fileName}`);
    } catch (error) {
      console.error('系统级静默安装APK失败:', error);
      Alert.alert('Error', `Failed to system silent install APK: ${error}`);
    }
  };

  const handleDownloadAndSilentInstall = async () => {
    if (!apkUrl.trim()) {
      Alert.alert('Error', 'Please enter a valid APK URL');
      return;
    }

    setIsInstalling(true);
    try {
      console.log('=== 开始下载并静默安装 ===');
      console.log('下载URL:', apkUrl);
      console.log('==================');

      await KioskManager.downloadAndSilentInstallApk(apkUrl);

      console.log('=== 下载并静默安装完成 ===');
      console.log('已启动静默安装');
      console.log('==================');

      Alert.alert('Success', 'APK download and silent installation started');
    } catch (error) {
      console.error('下载并静默安装失败:', error);
      Alert.alert('Error', `Failed to download and silent install APK: ${error}`);
    } finally {
      setIsInstalling(false);
    }
  };

  const handleDownloadAndSilentInstallAndLaunch = async () => {
    if (!apkUrl.trim()) {
      Alert.alert('Error', 'Please enter a valid APK URL');
      return;
    }

    setIsInstalling(true);
    try {
      console.log('=== 开始下载并静默安装并启动 ===');
      console.log('下载URL:', apkUrl);
      console.log('==================');

      await KioskManager.downloadAndSilentInstallAndLaunchApk(apkUrl);

      console.log('=== 下载并静默安装并启动完成 ===');
      console.log('已启动静默安装，安装完成后将自动启动应用');
      console.log('==================');

      Alert.alert('Success', 'APK download, silent installation and auto-launch started');
    } catch (error) {
      console.error('下载并静默安装并启动失败:', error);
      Alert.alert('Error', `Failed to download, silent install and launch APK: ${error}`);
    } finally {
      setIsInstalling(false);
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
        {/* 顶部 Tab */}
        <View style={[styles.tabBar, isDarkMode ? styles.darkTabBar : styles.lightTabBar]}>
          {[
            { key: 'kiosk', label: '基础控制' },
            { key: 'update', label: 'APK 更新' },
            { key: 'files', label: '下载管理' },
            { key: 'av', label: '亮度与音量' },
          ].map((t: any) => (
            <TouchableOpacity
              key={t.key}
              style={[
                styles.tabButton,
                activeTab === t.key && styles.activeTabButton,
              ]}
              onPress={() => setActiveTab(t.key)}
            >
              <Text
                style={[
                  styles.tabButtonText,
                  activeTab === t.key
                    ? styles.activeTabButtonText
                    : isDarkMode
                    ? styles.darkText
                    : styles.lightText,
                ]}
              >
                {t.label}
              </Text>
            </TouchableOpacity>
          ))}
        </View>

        {activeTab === 'kiosk' && (
        <>
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
        </>
        )}

        {/* APK 更新功能部分 */}
        {activeTab === 'update' && (
        <View style={[styles.section, isTablet && styles.tabletSection]}>
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
              style={[styles.textInput, isDarkMode && styles.darkTextInput]}
              value={apkUrl}
              onChangeText={setApkUrl}
              placeholder="输入 APK 文件的 URL"
              placeholderTextColor={isDarkMode ? '#666' : '#999'}
              multiline
            />
          </View>

          <View style={[styles.statusItem, isDarkMode && styles.darkStatusItem]}>
            <Text style={[styles.statusLabel, isDarkMode ? styles.darkText : styles.lightText]}>系统铃声模式:</Text>
            <Text style={[styles.statusValue, isDarkMode && styles.darkText]}>{ringerMode ?? '-'}</Text>
          </View>
          <View style={[styles.statusItem, isDarkMode && styles.darkStatusItem]}>
            <Text style={[styles.statusLabel, isDarkMode ? styles.darkText : styles.lightText]}>免打扰权限:</Text>
            <Text style={[styles.statusValue, (hasDndAccess ? styles.statusEnabled : styles.statusDisabled), isDarkMode && styles.darkText]}>{hasDndAccess ? '已授权' : '未授权'}</Text>
          </View>

          <View
            style={[styles.buttonGrid, isTablet && styles.tabletButtonGrid]}
          >
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
              <Text
                style={[styles.compactButtonText, styles.primaryButtonText]}
              >
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
              onPress={() => {
                if (downloadResult) {
                  handleInstallApk(downloadResult.filePath, downloadResult.fileName);
                }
              }}
              disabled={!downloadResult || isInstalling}
            >
              <Text
                style={[styles.compactButtonText, styles.successButtonText]}
              >
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
                {(downloadProgress.bytesRead / 1024 / 1024).toFixed(2)} MB /{' '}
                {(downloadProgress.totalBytes / 1024 / 1024).toFixed(2)} MB
              </Text>
            </View>
          )}

          <View
            style={[styles.buttonGrid, isTablet && styles.tabletButtonGrid]}
          >
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
              <Text
                style={[styles.compactButtonText, styles.warningButtonText]}
              >
                {isInstalling ? '处理中...' : '下载并安装'}
              </Text>
            </TouchableOpacity>

            <TouchableOpacity
              style={[
                styles.compactButton,
                styles.dangerButton,
                isDarkMode && styles.darkButton,
                isInstalling && styles.disabledButton,
              ]}
              onPress={handleDownloadAndSilentInstall}
              disabled={isInstalling}
            >
              <Text
                style={[styles.compactButtonText, styles.dangerButtonText]}
              >
                {isInstalling ? '处理中...' : '下载并静默安装'}
              </Text>
            </TouchableOpacity>

            <TouchableOpacity
              style={[
                styles.compactButton,
                styles.successButton,
                isDarkMode && styles.darkButton,
                isInstalling && styles.disabledButton,
              ]}
              onPress={handleDownloadAndSilentInstallAndLaunch}
              disabled={isInstalling}
            >
              <Text
                style={[styles.compactButtonText, styles.successButtonText]}
              >
                {isInstalling ? '处理中...' : '下载并静默安装并启动'}
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
              <Text style={[styles.statusValue, isDarkMode && styles.darkText]}>
                {downloadResult.fileName} (
                {(downloadResult.fileSize / 1024 / 1024).toFixed(2)} MB)
              </Text>
            </View>
          )}
        </View>
        )}

        {/* 亮度与音量控制 */}
        {activeTab === 'av' && (
        <View style={[styles.section, isTablet && styles.tabletSection]}>
          <Text
            style={[
              styles.sectionTitle,
              isDarkMode ? styles.darkText : styles.lightText,
            ]}
          >
            亮度与音量控制（Android）
          </Text>

          {/* 系统亮度权限与设置 */}
          <View style={styles.buttonGrid}>
            <TouchableOpacity
              style={[styles.compactButton, styles.infoButton, isDarkMode && styles.darkButton]}
              onPress={async () => {
                try {
                  const has = await KioskManager.hasWriteSettingsPermission();
                  setHasWriteSettings(has);
                  Alert.alert('WRITE_SETTINGS', has ? '已授权' : '未授权');
                } catch {
                  Alert.alert('Error', '检查权限失败');
                }
              }}
            >
              <Text style={[styles.compactButtonText, styles.infoButtonText]}>检查系统写入权限</Text>
            </TouchableOpacity>

            <TouchableOpacity
              style={[styles.compactButton, styles.warningButton, isDarkMode && styles.darkButton]}
              onPress={handleRequestWriteSettings}
            >
              <Text style={[styles.compactButtonText, styles.warningButtonText]}>请求系统写入权限</Text>
            </TouchableOpacity>
            {hasWriteSettings !== null && (
              <View style={[styles.statusItem, { flex: 1 }, isDarkMode && styles.darkStatusItem]}> 
                <Text style={[styles.statusLabel, isDarkMode ? styles.darkText : styles.lightText]}>权限状态</Text>
                <Text style={[styles.statusValue, hasWriteSettings ? styles.statusEnabled : styles.statusDisabled, isDarkMode && styles.darkText]}>
                  {hasWriteSettings ? '已授权' : '未授权'}
                </Text>
              </View>
            )}
          </View>

          <View style={[styles.statusItem, isDarkMode && styles.darkStatusItem]}> 
            <Text style={[styles.statusLabel, isDarkMode ? styles.darkText : styles.lightText]}>系统亮度(0-255):</Text>
            <Text style={[styles.statusValue, isDarkMode && styles.darkText]}>{systemBrightness ?? '-'}</Text>
          </View>

            <View style={styles.buttonRow}>
            <TouchableOpacity
              style={[styles.compactButton, styles.secondaryButton, isDarkMode && styles.darkButton]}
              onPress={() => handleSetSystemBrightness(50)}
            >
              <Text style={[styles.compactButtonText, styles.secondaryButtonText]}>系统亮度 50</Text>
            </TouchableOpacity>
            <TouchableOpacity
              style={[styles.compactButton, styles.secondaryButton, isDarkMode && styles.darkButton]}
              onPress={() => handleSetSystemBrightness(128)}
            >
              <Text style={[styles.compactButtonText, styles.secondaryButtonText]}>系统亮度 128</Text>
            </TouchableOpacity>
            <TouchableOpacity
              style={[styles.compactButton, styles.secondaryButton, isDarkMode && styles.darkButton]}
              onPress={() => handleSetSystemBrightness(255)}
            >
              <Text style={[styles.compactButtonText, styles.secondaryButtonText]}>系统亮度 255</Text>
            </TouchableOpacity>
          </View>

          {/* 应用亮度 */}
          <View style={[styles.statusItem, isDarkMode && styles.darkStatusItem]}> 
            <Text style={[styles.statusLabel, isDarkMode ? styles.darkText : styles.lightText]}>应用亮度(0-1或-1):</Text>
            <Text style={[styles.statusValue, isDarkMode && styles.darkText]}>{appBrightness ?? '-'}</Text>
          </View>
          <View style={styles.buttonRow}>
            <TouchableOpacity
              style={[styles.compactButton, styles.primaryButton, isDarkMode && styles.darkButton]}
              onPress={() => handleSetAppBrightness(0)}
            >
              <Text style={[styles.compactButtonText, styles.primaryButtonText]}>应用亮度 0</Text>
            </TouchableOpacity>
            <TouchableOpacity
              style={[styles.compactButton, styles.primaryButton, isDarkMode && styles.darkButton]}
              onPress={() => handleSetAppBrightness(0.5)}
            >
              <Text style={[styles.compactButtonText, styles.primaryButtonText]}>应用亮度 0.5</Text>
            </TouchableOpacity>
            <TouchableOpacity
              style={[styles.compactButton, styles.primaryButton, isDarkMode && styles.darkButton]}
              onPress={() => handleSetAppBrightness(1)}
            >
              <Text style={[styles.compactButtonText, styles.primaryButtonText]}>应用亮度 1</Text>
            </TouchableOpacity>
            <TouchableOpacity
              style={[styles.compactButton, styles.warningButton, isDarkMode && styles.darkButton]}
              onPress={() => { try { KioskManager.resetAppBrightness(); setAppBrightness(-1); } catch {} }}
            >
              <Text style={[styles.compactButtonText, styles.warningButtonText]}>恢复系统控制</Text>
            </TouchableOpacity>
          </View>

          {/* 全局音量便捷操作 */}
          <View style={[styles.statusItem, isDarkMode && styles.darkStatusItem]}> 
            <Text style={[styles.statusLabel, isDarkMode ? styles.darkText : styles.lightText]}>全局音量(0-1):</Text>
            <Text style={[styles.statusValue, isDarkMode && styles.darkText]}>{globalVolume?.toFixed(2) ?? '-'}</Text>
          </View>

          <View style={styles.buttonRow}>
            <TouchableOpacity
              style={[styles.compactButton, styles.successButton, isDarkMode && styles.darkButton]}
              onPress={async () => { await KioskManager.setGlobalVolume(0); setGlobalVolume(0); }}
            >
              <Text style={[styles.compactButtonText, styles.successButtonText]}>全局音量 0</Text>
            </TouchableOpacity>
            <TouchableOpacity
              style={[styles.compactButton, styles.successButton, isDarkMode && styles.darkButton]}
              onPress={async () => { await KioskManager.setGlobalVolume(0.5); const v = await KioskManager.getGlobalVolume(); setGlobalVolume(v); }}
            >
              <Text style={[styles.compactButtonText, styles.successButtonText]}>全局音量 0.5</Text>
            </TouchableOpacity>
            <TouchableOpacity
              style={[styles.compactButton, styles.successButton, isDarkMode && styles.darkButton]}
              onPress={async () => { await KioskManager.setGlobalVolume(1); setGlobalVolume(1); }}
            >
              <Text style={[styles.compactButtonText, styles.successButtonText]}>全局音量 1</Text>
            </TouchableOpacity>
            <TouchableOpacity
              style={[styles.compactButton, styles.warningButton, isDarkMode && styles.darkButton]}
              onPress={handleToggleGlobalMute}
            >
              <Text style={[styles.compactButtonText, styles.warningButtonText]}>{globalMuted ? '取消全局静音' : '全局静音'}</Text>
            </TouchableOpacity>
            <TouchableOpacity
              style={[styles.compactButton, styles.secondaryButton, isDarkMode && styles.darkButton]}
              onPress={handleToggleSystemMute}
            >
              <Text style={[styles.compactButtonText, styles.secondaryButtonText]}>
                {ringerMode === 'silent' ? '系统静音：开（点我关闭）' : '系统静音：关（点我开启）'}
              </Text>
            </TouchableOpacity>
          </View>

          

          {/* 各音频流音量（Accordion） */}
          <TouchableOpacity
            style={[styles.buttonRow]}
            onPress={() => setStreamsOpen(!streamsOpen)}
          >
            <View style={[styles.statusItem, { flex: 1 }, isDarkMode && styles.darkStatusItem]}> 
              <Text style={[styles.statusLabel, isDarkMode ? styles.darkText : styles.lightText]}>
                各音频流音量
              </Text>
            </View>
            <View>
              <Text style={[styles.compactButtonText, isDarkMode ? styles.darkText : styles.lightText]}>
                {streamsOpen ? '收起 ▲' : '展开 ▼'}
              </Text>
            </View>
          </TouchableOpacity>

          {streamsOpen && (
            <>
              {audioStreams.map((s) => (
                <View key={s.key} style={styles.buttonRow}>
                  <TouchableOpacity
                    style={[styles.compactButton, styles.infoButton, isDarkMode && styles.darkButton]}
                    onPress={() => handleSetVolume(s.key, Math.max(0, (volumes[s.key] ?? 0) - 0.1))}
                  >
                    <Text style={[styles.compactButtonText, styles.infoButtonText]}>{s.label} -</Text>
                  </TouchableOpacity>
                  <View style={[styles.statusItem, { flex: 1 }, isDarkMode && styles.darkStatusItem]}>
                    <Text style={[styles.statusLabel, isDarkMode ? styles.darkText : styles.lightText]}>
                      {s.label} 音量:
                    </Text>
                    <Text style={[styles.statusValue, isDarkMode && styles.darkText]}>
                      {(volumes[s.key] ?? 0).toFixed(2)}
                    </Text>
                    <Text style={[styles.statusValue, isDarkMode && styles.darkText]}>
                      {mutedMap[s.key] ? '（已静音）' : ''}
                    </Text>
                  </View>
                  <TouchableOpacity
                    style={[styles.compactButton, styles.infoButton, isDarkMode && styles.darkButton]}
                    onPress={() => handleSetVolume(s.key, Math.min(1, (volumes[s.key] ?? 0) + 0.1))}
                  >
                    <Text style={[styles.compactButtonText, styles.infoButtonText]}>{s.label} +</Text>
                  </TouchableOpacity>
                  <TouchableOpacity
                    style={[styles.compactButton, styles.warningButton, isDarkMode && styles.darkButton]}
                    onPress={() => handleToggleMute(s.key)}
                  >
                    <Text style={[styles.compactButtonText, styles.warningButtonText]}>
                      {mutedMap[s.key] ? '取消静音' : '静音'}
                    </Text>
                  </TouchableOpacity>
                </View>
              ))}
            </>
          )}
        </View>
        )}
        {/* 文件管理部分 */}
        {activeTab === 'files' && (
        <View style={[styles.section, isTablet && styles.tabletSection]}>
          <Text
            style={[
              styles.sectionTitle,
              isDarkMode ? styles.darkText : styles.lightText,
            ]}
          >
            下载文件管理
          </Text>

          <View
            style={[styles.buttonGrid, isTablet && styles.tabletButtonGrid]}
          >
            <TouchableOpacity
              style={[
                styles.compactButton,
                styles.infoButton,
                isDarkMode && styles.darkButton,
                isLoadingFiles && styles.disabledButton,
              ]}
              onPress={handleGetDownloadedFiles}
              disabled={isLoadingFiles}
            >
              <Text style={[styles.compactButtonText, styles.infoButtonText]}>
                {isLoadingFiles ? '加载中...' : '获取文件列表'}
              </Text>
            </TouchableOpacity>

            <TouchableOpacity
              style={[
                styles.compactButton,
                styles.dangerButton,
                isDarkMode && styles.darkButton,
                downloadedFiles.length === 0 && styles.disabledButton,
              ]}
              onPress={handleClearAllFiles}
              disabled={downloadedFiles.length === 0}
            >
              <Text style={[styles.compactButtonText, styles.dangerButtonText]}>
                清空所有文件
              </Text>
            </TouchableOpacity>
          </View>

          {/* 文件列表 */}
          {downloadedFiles.length > 0 && (
            <View style={styles.fileListContainer}>
              <Text
                style={[
                  styles.fileListTitle,
                  isDarkMode ? styles.darkText : styles.lightText,
                ]}
              >
                已下载文件 ({downloadedFiles.length})
              </Text>

              {downloadedFiles.map((file) => (
                <View
                  key={file.filePath}
                  style={[styles.fileItem, isDarkMode && styles.darkFileItem]}
                >
                  <View style={styles.fileInfo}>
                    <Text
                      style={[
                        styles.fileName,
                        isDarkMode ? styles.darkText : styles.lightText,
                      ]}
                      numberOfLines={1}
                    >
                      {file.fileName}
                    </Text>
                    <Text
                      style={[
                        styles.fileDetails,
                        isDarkMode ? styles.darkText : styles.lightText,
                      ]}
                    >
                      {(file.fileSize / 1024 / 1024).toFixed(2)} MB •{' '}
                      {new Date(file.lastModified).toLocaleDateString()}
                    </Text>
                    <Text
                      style={[
                        styles.filePath,
                        isDarkMode ? styles.darkText : styles.lightText,
                      ]}
                      numberOfLines={1}
                    >
                      {file.filePath}
                    </Text>
                  </View>

                  <View style={styles.fileActions}>
                    <TouchableOpacity
                      style={[
                        styles.installButton,
                        isDarkMode && styles.darkInstallButton,
                      ]}
                      onPress={() =>
                        handleInstallApk(file.filePath, file.fileName)
                      }
                    >
                      <Text style={styles.installButtonText}>安装</Text>
                    </TouchableOpacity>
                    <TouchableOpacity
                      style={[
                        styles.silentInstallButton,
                        isDarkMode && styles.darkSilentInstallButton,
                      ]}
                      onPress={() =>
                        handleSilentInstallApk(file.filePath, file.fileName)
                      }
                    >
                      <Text style={styles.silentInstallButtonText}>静默安装</Text>
                    </TouchableOpacity>
                    <TouchableOpacity
                      style={[
                        styles.autoLaunchButton,
                        isDarkMode && styles.darkAutoLaunchButton,
                      ]}
                      onPress={() =>
                        handleSilentInstallAndLaunchApk(file.filePath, file.fileName)
                      }
                    >
                      <Text style={styles.autoLaunchButtonText}>静默安装并启动</Text>
                    </TouchableOpacity>
                    <TouchableOpacity
                      style={[
                        styles.systemInstallButton,
                        isDarkMode && styles.darkSystemInstallButton,
                      ]}
                      onPress={() =>
                        handleSystemSilentInstallApk(file.filePath, file.fileName)
                      }
                    >
                      <Text style={styles.systemInstallButtonText}>系统级静默安装</Text>
                    </TouchableOpacity>
                    <TouchableOpacity
                      style={[
                        styles.deleteButton,
                        isDarkMode && styles.darkDeleteButton,
                      ]}
                      onPress={() =>
                        handleDeleteFile(file.filePath, file.fileName)
                      }
                    >
                      <Text style={styles.deleteButtonText}>删除</Text>
                    </TouchableOpacity>
                  </View>
                </View>
              ))}
            </View>
          )}

          {downloadedFiles.length === 0 && !isLoadingFiles && (
            <View style={styles.emptyState}>
              <Text
                style={[
                  styles.emptyStateText,
                  isDarkMode ? styles.darkText : styles.lightText,
                ]}
              >
                暂无下载文件
              </Text>
            </View>
          )}
        </View>
        )}
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

  // 文件管理相关样式
  fileListContainer: {
    marginTop: 16,
  },
  fileListTitle: {
    fontSize: 16,
    fontWeight: '600',
    marginBottom: 12,
  },
  fileItem: {
    flexDirection: 'row',
    alignItems: 'center',
    padding: 12,
    marginBottom: 8,
    backgroundColor: 'rgba(255,255,255,0.7)',
    borderRadius: 8,
    borderWidth: 1,
    borderColor: 'rgba(0,0,0,0.1)',
  },
  darkFileItem: {
    backgroundColor: 'rgba(255,255,255,0.1)',
    borderColor: 'rgba(255,255,255,0.2)',
  },
  fileInfo: {
    flex: 1,
    marginRight: 12,
  },
  fileName: {
    fontSize: 14,
    fontWeight: '600',
    marginBottom: 4,
  },
  fileDetails: {
    fontSize: 12,
    opacity: 0.7,
    marginBottom: 2,
  },
  filePath: {
    fontSize: 10,
    opacity: 0.5,
    fontFamily: 'monospace',
  },
  fileActions: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  installButton: {
    paddingHorizontal: 12,
    paddingVertical: 6,
    backgroundColor: '#28a745',
    borderRadius: 6,
    marginRight: 8,
  },
  darkInstallButton: {
    backgroundColor: '#28a745',
  },
  installButtonText: {
    color: '#fff',
    fontSize: 12,
    fontWeight: '600',
  },
  silentInstallButton: {
    paddingHorizontal: 12,
    paddingVertical: 6,
    backgroundColor: '#dc3545',
    borderRadius: 6,
    marginRight: 8,
  },
  darkSilentInstallButton: {
    backgroundColor: '#dc3545',
  },
  silentInstallButtonText: {
    color: '#fff',
    fontSize: 12,
    fontWeight: '600',
  },
  autoLaunchButton: {
    paddingHorizontal: 12,
    paddingVertical: 6,
    backgroundColor: '#28a745',
    borderRadius: 6,
    marginRight: 8,
  },
  darkAutoLaunchButton: {
    backgroundColor: '#28a745',
  },
  autoLaunchButtonText: {
    color: '#fff',
    fontSize: 12,
    fontWeight: '600',
  },
  systemInstallButton: {
    paddingHorizontal: 12,
    paddingVertical: 6,
    backgroundColor: '#6f42c1',
    borderRadius: 6,
    marginRight: 8,
  },
  darkSystemInstallButton: {
    backgroundColor: '#6f42c1',
  },
  systemInstallButtonText: {
    color: '#fff',
    fontSize: 12,
    fontWeight: '600',
  },
  deleteButton: {
    paddingHorizontal: 12,
    paddingVertical: 6,
    backgroundColor: '#dc3545',
    borderRadius: 6,
  },
  darkDeleteButton: {
    backgroundColor: '#dc3545',
  },
  deleteButtonText: {
    color: '#fff',
    fontSize: 12,
    fontWeight: '600',
  },
  emptyState: {
    padding: 20,
    alignItems: 'center',
  },
  emptyStateText: {
    fontSize: 14,
    opacity: 0.6,
    fontStyle: 'italic',
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
  // Tabs
  tabBar: {
    flexDirection: 'row',
    borderRadius: 10,
    padding: 6,
    marginBottom: 16,
    gap: 8,
  },
  lightTabBar: {
    backgroundColor: 'rgba(0,0,0,0.06)',
  },
  darkTabBar: {
    backgroundColor: 'rgba(255,255,255,0.08)',
  },
  tabButton: {
    flex: 1,
    paddingVertical: 10,
    borderRadius: 8,
    alignItems: 'center',
    justifyContent: 'center',
  },
  activeTabButton: {
    backgroundColor: '#007AFF',
  },
  tabButtonText: {
    fontSize: 14,
    fontWeight: '600',
  },
  activeTabButtonText: {
    color: '#ffffff',
  },
});
