import { useState, useEffect, useRef } from 'react';
import {
  Text,
  View,
  ScrollView,
  Dimensions,
  StatusBar,
} from 'react-native';
import KioskManager, {
  type DownloadResult,
  type DownloadProgress,
  type DownloadedFile,
  type InstallStatus,
  type ScheduledPowerSettings,
} from 'react-native-kiosk-manager';
import TabBar, { type TabKey } from './components/TabBar';
import KioskControlTab from './components/KioskControlTab';
import ApkUpdateTab from './components/ApkUpdateTab';
import FileManagementTab from './components/FileManagementTab';
import BrightnessVolumeTab from './components/BrightnessVolumeTab';
import PowerScheduleTab from './components/PowerScheduleTab';
import { useAppStyles } from './utils/styles';
import { createSafeAlert } from './utils/safeAlert';

export default function App() {
  // 用于跟踪组件是否已挂载，避免在卸载后显示 Alert
  const isMountedRef = useRef(true);
  const safeAlert = createSafeAlert(isMountedRef);

  const { isDarkMode, styles } = useAppStyles();
  const { width, height } = Dimensions.get('window');
  const isTablet = width >= 768;
  const isLandscape = width > height;

  // Kiosk 控制相关状态
  const [bootAutoStart, setBootAutoStart] = useState<boolean | null>(null);
  const [isDeviceOwner, setIsDeviceOwner] = useState<boolean | null>(null);
  const [isLockTaskPackageSetup, setIsLockTaskPackageSetup] = useState<boolean | null>(null);

  // APK 更新相关状态
  const [apkUrl, setApkUrl] = useState<string>('https://assets.driver-day.com/LATEST/apk/kiosk2.apk');
  const [downloadResult, setDownloadResult] = useState<DownloadResult | null>(null);
  const [isInstalling, setIsInstalling] = useState<boolean>(false);
  const [packageName, setPackageName] = useState<string>('com.kioskmanager.example');
  const [isLaunching, setIsLaunching] = useState<boolean>(false);
  const [isAppInstalledStatus, setIsAppInstalledStatus] = useState<boolean | null>(null);
  const [isCheckingInstall, setIsCheckingInstall] = useState<boolean>(false);
  const [downloadProgress, setDownloadProgress] = useState<DownloadProgress | null>(null);
  const [isDownloading, setIsDownloading] = useState<boolean>(false);
  const [downloadedFiles, setDownloadedFiles] = useState<DownloadedFile[]>([]);
  const [isLoadingFiles, setIsLoadingFiles] = useState<boolean>(false);
  const [installStatus, setInstallStatus] = useState<InstallStatus | null>(null);

  // 亮度/音量相关状态
  const [hasWriteSettings, setHasWriteSettings] = useState<boolean | null>(null);
  const [systemBrightness, setSystemBrightness] = useState<number | null>(null);
  const [appBrightness, setAppBrightness] = useState<number | null>(null);
  const [volumes, setVolumes] = useState<Record<string, number>>({});
  const [globalVolume, setGlobalVolume] = useState<number | null>(null);
  const [mutedMap, setMutedMap] = useState<Record<string, boolean>>({});
  const [globalMuted, setGlobalMuted] = useState<boolean | null>(null);
  const [ringerMode, setRingerMode] = useState<'silent' | 'vibrate' | 'normal' | null>(null);
  const [hasDndAccess, setHasDndAccess] = useState<boolean | null>(null);

  // 定时开关机相关状态
  const [scheduledShutdown, setScheduledShutdown] = useState<ScheduledPowerSettings | null>(null);
  const [scheduledBoot, setScheduledBoot] = useState<ScheduledPowerSettings | null>(null);
  const [shutdownHour, setShutdownHour] = useState<string>('22');
  const [shutdownMinute, setShutdownMinute] = useState<string>('0');
  const [shutdownRepeat, setShutdownRepeat] = useState<boolean>(true);
  const [bootHour, setBootHour] = useState<string>('8');
  const [bootMinute, setBootMinute] = useState<string>('0');
  const [bootRepeat, setBootRepeat] = useState<boolean>(true);
  
  // 选项卡
  const [activeTab, setActiveTab] = useState<TabKey>('kiosk');

  // 组件卸载时设置标志
  useEffect(() => {
    isMountedRef.current = true;
    return () => {
      isMountedRef.current = false;
    };
  }, []);

  // 初始化检查开机自启状态
  useEffect(() => {
    const checkBootAutoStart = async () => {
      try {
        const isEnabled = await KioskManager.isBootAutoStartEnabled();
        setBootAutoStart(isEnabled);
      } catch (error) {
        console.log('Failed to check boot auto start status:', error);
      }
    };
    checkBootAutoStart();
  }, []);

  // 初始化检查定时开关机设置
  useEffect(() => {
    const checkScheduledPower = async () => {
      try {
        const shutdown = await KioskManager.getScheduledShutdown();
        setScheduledShutdown(shutdown);
        if (shutdown) {
          setShutdownHour(shutdown.hour.toString());
          setShutdownMinute(shutdown.minute.toString());
          setShutdownRepeat(shutdown.repeat);
        }
      } catch (error) {
        console.log('Failed to check scheduled shutdown:', error);
      }
      try {
        const boot = await KioskManager.getScheduledBoot();
        setScheduledBoot(boot);
        if (boot) {
          setBootHour(boot.hour.toString());
          setBootMinute(boot.minute.toString());
          setBootRepeat(boot.repeat);
        }
      } catch (error) {
        console.log('Failed to check scheduled boot:', error);
      }
    };
    checkScheduledPower();
  }, []);

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

  // 设置安装状态监听器
  useEffect(() => {
    const handleInstallStatus = (status: InstallStatus) => {
      console.log('安装状态更新:', status);
      setInstallStatus(status);
      
      if (
        status.status === 'installed' ||
        status.status === 'launched' ||
        status.status === 'failed' ||
        status.status === 'cancelled' ||
        status.status === 'blocked' ||
        status.status === 'conflict' ||
        status.status === 'incompatible' ||
        status.status === 'invalid' ||
        status.status === 'storage_error' ||
        status.status === 'launch_failed'
      ) {
        setIsInstalling(false);
      } else if (status.status === 'installing' || status.status === 'launching') {
        setIsInstalling(true);
      }
    };

    KioskManager.addInstallStatusListener(handleInstallStatus);
    return () => {
      KioskManager.removeInstallStatusListener(handleInstallStatus);
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
          [
            'music',
            'ring',
            'alarm',
            'notification',
            'system',
            'voice_call',
            'dtmf',
          ].map(async (s) => {
            try {
              const v = await KioskManager.getVolume(
                s as 'music' | 'ring' | 'alarm' | 'notification' | 'system' | 'voice_call' | 'dtmf'
              );
              return [s, v] as const;
            } catch {
              return [s, 0] as const;
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
          [
            'music',
            'ring',
            'alarm',
            'notification',
            'system',
            'voice_call',
            'dtmf',
          ].map(async (s) => {
            try {
              const m = await KioskManager.isMuted(
                s as 'music' | 'ring' | 'alarm' | 'notification' | 'system' | 'voice_call' | 'dtmf'
              );
              return [s, !!m] as const;
            } catch {
              return [s, false] as const;
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
    try {
      KioskManager.startObservingSystemAv();
    } catch {}

    // 订阅系统事件
    const onBrightness = (v: number) => {
      setSystemBrightness(v);
    };
    const onVolume = (d: { stream: string; index: number; max: number; value: number }) => {
      setVolumes((prev) => ({ ...prev, [d.stream]: Math.max(0, Math.min(1, d.value)) }));
    };
    const onGlobalVolume = (v: number) => {
      setGlobalVolume(Math.max(0, Math.min(1, v)));
    };
    const onRinger = (m: 'silent' | 'vibrate' | 'normal') => {
      setRingerMode(m);
    };
    KioskManager.addSystemBrightnessListener(onBrightness);
    KioskManager.addVolumeChangedListener(onVolume);
    KioskManager.addGlobalVolumeChangedListener(onGlobalVolume);
    KioskManager.addRingerModeChangedListener(onRinger);

    return () => {
      try {
        KioskManager.stopObservingSystemAv();
      } catch {}
      KioskManager.removeSystemBrightnessListener(onBrightness);
      KioskManager.removeVolumeChangedListener(onVolume);
      KioskManager.removeGlobalVolumeChangedListener(onGlobalVolume);
      KioskManager.removeRingerModeChangedListener(onRinger);
    };
  }, []);

  // Kiosk 控制相关函数
  const handleStartKiosk = () => {
    KioskManager.startKiosk();
    safeAlert('Success', 'Kiosk mode started');
  };

  const handleStopKiosk = () => {
    KioskManager.stopKiosk();
    safeAlert('Success', 'Kiosk mode stopped');
  };

  const handleCheckBootAutoStart = async () => {
    try {
      const isEnabled = await KioskManager.isBootAutoStartEnabled();
      setBootAutoStart(isEnabled);
    } catch (error) {
      safeAlert('Error', 'Failed to check boot auto start status');
    }
  };

  const handleRequestDeviceAdmin = async () => {
    try {
      await KioskManager.requestDeviceAdmin();
      safeAlert('Success', 'Device admin requested');
    } catch (error) {
      safeAlert('Error', 'Failed to request device admin');
    }
  };

  const handleSetupLockTaskPackage = async () => {
    try {
      await KioskManager.setupLockTaskPackage();
      safeAlert('Success', 'Lock task package setup completed');
      setIsLockTaskPackageSetup(true);
    } catch (error) {
      safeAlert('Error', 'Failed to setup lock task package. Make sure the app is device owner.');
      setIsLockTaskPackageSetup(false);
    }
  };

  const handleToggleBootAutoStart = async () => {
    try {
      let currentState = bootAutoStart;
      if (currentState === null) {
        currentState = await KioskManager.isBootAutoStartEnabled();
        setBootAutoStart(currentState);
      }
      
      const newState = !currentState;
      await KioskManager.enableBootAutoStart(newState);
      setBootAutoStart(newState);
      safeAlert('Success', newState ? '开机自启已启用' : '开机自启已禁用');
    } catch (error) {
      safeAlert('Error', '切换开机自启状态失败');
    }
  };

  const handleCheckDeviceOwner = async () => {
    try {
      const isOwner = await KioskManager.isDeviceOwner();
      setIsDeviceOwner(isOwner);
    } catch (error) {
      safeAlert('Error', 'Failed to check device owner status');
    }
  };

  const handleClearDeviceOwner = async () => {
    try {
      safeAlert(
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
                safeAlert('Success', 'Device owner cleared successfully');
                setIsDeviceOwner(false);
                setIsLockTaskPackageSetup(false);
              } catch (error) {
                safeAlert('Error', 'Failed to clear device owner');
              }
            },
          },
        ]
      );
    } catch (error) {
      safeAlert('Error', 'Failed to clear device owner');
    }
  };

  // APK 更新相关函数
  const handleDownloadApk = async () => {
    if (!apkUrl.trim()) {
      safeAlert('Error', 'Please enter a valid APK URL');
      return;
    }

    setIsDownloading(true);
    setDownloadProgress(null);
    setDownloadResult(null);

    try {
      const result = await KioskManager.downloadApk(apkUrl);
      setDownloadResult(result);
      setDownloadProgress(null);

      console.log('=== 下载完成 ===');
      console.log('文件名:', result.fileName);
      console.log('文件路径:', result.filePath);
      console.log('文件大小:', result.fileSize, '字节');

      safeAlert(
        'Success',
        `APK downloaded successfully!\nFile: ${result.fileName}\nSize: ${(result.fileSize / 1024 / 1024).toFixed(2)} MB`
      );
    } catch (error) {
      console.error('下载失败:', error);
      safeAlert('Error', `Failed to download APK: ${error}`);
    } finally {
      setIsDownloading(false);
    }
  };

  const handleDownloadAndInstall = async () => {
    if (!apkUrl.trim()) {
      safeAlert('Error', 'Please enter a valid APK URL');
      return;
    }

    setIsInstalling(true);
    try {
      console.log('=== 开始下载并安装 ===');
      console.log('下载URL:', apkUrl);

      await KioskManager.downloadAndInstallApk(apkUrl);

      console.log('=== 下载并安装完成 ===');
      safeAlert('Success', 'APK download and installation started');
    } catch (error) {
      console.error('下载并安装失败:', error);
      safeAlert('Error', `Failed to download and install APK: ${error}`);
    } finally {
      setIsInstalling(false);
    }
  };

  const handleCheckInstallPermission = async () => {
    try {
      const hasPermission = await KioskManager.checkInstallPermission();
      safeAlert('Install Permission', hasPermission ? 'Permission granted' : 'Permission required');
    } catch (error) {
      safeAlert('Error', `Failed to check install permission: ${error}`);
    }
  };

  const handleRequestInstallPermission = async () => {
    try {
      await KioskManager.requestInstallPermission();
      safeAlert('Success', 'Install permission request sent');
    } catch (error) {
      safeAlert('Error', `Failed to request install permission: ${error}`);
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
      });
    } catch (error) {
      console.error('获取文件列表失败:', error);
      safeAlert('Error', `Failed to get downloaded files: ${error}`);
    } finally {
      setIsLoadingFiles(false);
    }
  };

  const handleDeleteFile = async (filePath: string, fileName: string) => {
    try {
      console.log('=== 删除文件 ===');
      console.log('文件路径:', filePath);
      console.log('文件名:', fileName);

      await KioskManager.deleteDownloadedFile(filePath);

      const updatedFiles = downloadedFiles.filter((file) => file.filePath !== filePath);
      setDownloadedFiles(updatedFiles);

      console.log('文件删除成功:', fileName);
      safeAlert('Success', `File "${fileName}" deleted successfully`);
    } catch (error) {
      console.error('删除文件失败:', error);
      safeAlert('Error', `Failed to delete file: ${error}`);
    }
  };

  const handleClearAllFiles = async () => {
    try {
      console.log('=== 清空所有文件 ===');
      console.log('当前文件数量:', downloadedFiles.length);

      const deletedCount = await KioskManager.clearAllDownloadedFiles();
      setDownloadedFiles([]);

      console.log('成功删除', deletedCount, '个文件');
      safeAlert('Success', `Cleared ${deletedCount} files successfully`);
    } catch (error) {
      console.error('清空文件失败:', error);
      safeAlert('Error', `Failed to clear files: ${error}`);
    }
  };

  const handleInstallApk = async (filePath: string, fileName: string) => {
    try {
      console.log('=== 安装APK ===');
      console.log('文件路径:', filePath);
      console.log('文件名:', fileName);

      const hasPermission = await KioskManager.checkInstallPermission();
      if (!hasPermission) {
        safeAlert(
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
              },
            },
          ]
        );
        return;
      }

      await KioskManager.installApk(filePath);
      
      console.log('APK安装启动成功');
      safeAlert('Success', `开始安装 ${fileName}`);
    } catch (error) {
      console.error('安装APK失败:', error);
      safeAlert('Error', `Failed to install APK: ${error}`);
    }
  };

  const handleSilentInstallApk = async (filePath: string, fileName: string) => {
    try {
      console.log('=== 静默安装APK ===');
      console.log('文件路径:', filePath);
      console.log('文件名:', fileName);

      const isOwner = await KioskManager.isDeviceOwner();
      if (!isOwner) {
        safeAlert(
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
              },
            },
          ]
        );
        return;
      }

      await KioskManager.silentInstallApk(filePath);
      
      console.log('APK静默安装启动成功');
      safeAlert('Success', `开始静默安装 ${fileName}`);
    } catch (error) {
      console.error('静默安装APK失败:', error);
      safeAlert('Error', `Failed to silent install APK: ${error}`);
    }
  };

  const handleSilentInstallAndLaunchApk = async (filePath: string, fileName: string) => {
    try {
      console.log('=== 静默安装并启动APK ===');
      console.log('文件路径:', filePath);
      console.log('文件名:', fileName);

      const isOwner = await KioskManager.isDeviceOwner();
      if (!isOwner) {
        safeAlert(
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
              },
            },
          ]
        );
        return;
      }

      await KioskManager.silentInstallAndLaunchApk(filePath);
      
      console.log('APK静默安装并启动启动成功');
    } catch (error) {
      console.error('静默安装并启动APK失败:', error);
      safeAlert('Error', `Failed to silent install and launch APK: ${error}`);
    }
  };

  const handleSystemSilentInstallApk = async (filePath: string, fileName: string) => {
    try {
      console.log('=== 系统级静默安装APK ===');
      console.log('文件路径:', filePath);
      console.log('文件名:', fileName);

      const isOwner = await KioskManager.isDeviceOwner();
      if (!isOwner) {
        safeAlert(
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
              },
            },
          ]
        );
        return;
      }

      await KioskManager.systemSilentInstallApk(filePath);
      
      console.log('APK系统级静默安装启动成功');
      safeAlert('Success', `开始系统级静默安装 ${fileName}`);
    } catch (error) {
      console.error('系统级静默安装APK失败:', error);
      safeAlert('Error', `Failed to system silent install APK: ${error}`);
    }
  };

  const handleDownloadAndSilentInstall = async () => {
    if (!apkUrl.trim()) {
      safeAlert('Error', 'Please enter a valid APK URL');
      return;
    }

    setIsInstalling(true);
    try {
      console.log('=== 开始下载并静默安装 ===');
      console.log('下载URL:', apkUrl);

      await KioskManager.downloadAndSilentInstallApk(apkUrl);

      console.log('=== 下载并静默安装完成 ===');
      safeAlert('Success', 'APK download and silent installation started');
    } catch (error) {
      console.error('下载并静默安装失败:', error);
      safeAlert('Error', `Failed to download and silent install APK: ${error}`);
    } finally {
      setIsInstalling(false);
    }
  };

  const handleDownloadAndSilentInstallAndLaunch = async () => {
    if (!apkUrl.trim()) {
      safeAlert('Error', 'Please enter a valid APK URL');
      return;
    }

    setIsInstalling(true);
    try {
      console.log('=== 开始下载并静默安装并启动 ===');
      console.log('下载URL:', apkUrl);

      await KioskManager.downloadAndSilentInstallAndLaunchApk(apkUrl);

      console.log('=== 下载并静默安装并启动完成 ===');
    } catch (error) {
      console.error('下载并静默安装并启动失败:', error);
      safeAlert('Error', `Failed to download, silent install and launch APK: ${error}`);
    } finally {
      setIsInstalling(false);
    }
  };

  const handleCheckAppInstalled = async () => {
    if (!packageName.trim()) {
      safeAlert('Error', '请输入应用包名');
      return;
    }

    setIsCheckingInstall(true);
    try {
      console.log('=== 检查应用安装状态 ===');
      console.log('包名:', packageName);

      const isInstalled = await KioskManager.isAppInstalled(packageName.trim());
      setIsAppInstalledStatus(isInstalled);

      console.log('=== 检查结果 ===');
      console.log('包名:', packageName);
      console.log('安装状态:', isInstalled ? '已安装' : '未安装');

      safeAlert(
        isInstalled ? '应用已安装' : '应用未安装',
        `包名: ${packageName}\n状态: ${isInstalled ? '已安装' : '未安装'}`
      );
    } catch (error: any) {
      console.error('检查应用安装状态失败:', error);
      setIsAppInstalledStatus(null);
      safeAlert('Error', `检查失败: ${error.message || error}`);
    } finally {
      setIsCheckingInstall(false);
    }
  };

  const handleLaunchApp = async () => {
    if (!packageName.trim()) {
      safeAlert('Error', '请输入应用包名');
      return;
    }

    setIsLaunching(true);
    try {
      console.log('=== 开始启动应用 ===');
      console.log('包名:', packageName);

      const isInstalled = await KioskManager.isAppInstalled(packageName.trim());
      setIsAppInstalledStatus(isInstalled);

      if (!isInstalled) {
        safeAlert('Error', `应用未安装: ${packageName}\n\n请先安装应用后再启动。`);
        setIsLaunching(false);
        return;
      }

      const success = await KioskManager.launchApp(packageName.trim());

      if (success) {
        console.log('=== 应用启动成功 ===');
        console.log('包名:', packageName);
        safeAlert('Success', `应用启动成功: ${packageName}`);
      } else {
        console.error('应用启动失败');
        safeAlert('Error', `应用启动失败: ${packageName}`);
      }
    } catch (error: any) {
      console.error('启动应用失败:', error);
      let errorMessage = '启动应用失败';
      if (error.code === 'E_APP_NOT_FOUND') {
        errorMessage = `应用未安装: ${packageName}`;
        setIsAppInstalledStatus(false);
      } else if (error.code === 'E_NO_LAUNCH_INTENT') {
        errorMessage = `找不到启动意图: ${packageName}`;
      } else if (error.code === 'E_LAUNCH_FAILED') {
        errorMessage = `启动失败: ${error.message || packageName}`;
      } else {
        errorMessage = `启动应用失败: ${error.message || error}`;
      }
      console.error('启动应用失败:', errorMessage);
      safeAlert('Error', errorMessage);
    } finally {
      setIsLaunching(false);
    }
  };

  // 定时开关机相关函数
  const handleSetScheduledShutdown = async () => {
    try {
      const hour = parseInt(shutdownHour, 10);
      const minute = parseInt(shutdownMinute, 10);

      if (isNaN(hour) || hour < 0 || hour > 23) {
        safeAlert('错误', '小时必须在 0-23 之间');
        return;
      }
      if (isNaN(minute) || minute < 0 || minute > 59) {
        safeAlert('错误', '分钟必须在 0-59 之间');
        return;
      }

      const success = await KioskManager.setScheduledShutdown(hour, minute, shutdownRepeat);
      if (!isMountedRef.current) return;

      if (success) {
        const settings = await KioskManager.getScheduledShutdown();
        if (!isMountedRef.current) return;
        setScheduledShutdown(settings);
        safeAlert(
          '成功',
          `定时关机已设置: ${String(hour).padStart(2, '0')}:${String(minute).padStart(2, '0')}${shutdownRepeat ? ' (每天重复)' : ' (单次)'}`
        );
      } else {
        safeAlert('失败', '设置定时关机失败，请确保应用拥有设备所有者权限');
      }
    } catch (error: any) {
      if (!isMountedRef.current) return;
      safeAlert('错误', `设置定时关机失败: ${error.message || error}`);
    }
  };

  const handleCancelScheduledShutdown = async () => {
    try {
      const success = await KioskManager.cancelScheduledShutdown();
      if (!isMountedRef.current) return;

      if (success) {
        setScheduledShutdown(null);
        safeAlert('成功', '定时关机已取消');
      } else {
        safeAlert('失败', '取消定时关机失败');
      }
    } catch (error: any) {
      if (!isMountedRef.current) return;
      safeAlert('错误', `取消定时关机失败: ${error.message || error}`);
    }
  };

  const handlePerformShutdown = async () => {
    try {
      safeAlert(
        '确认关机',
        '确定要立即关机吗？',
        [
          { text: '取消', style: 'cancel' },
          {
            text: '确定',
            style: 'destructive',
            onPress: async () => {
              try {
                const success = await KioskManager.performShutdown();
                if (!isMountedRef.current) return;

                if (success) {
                  safeAlert('成功', '关机命令已执行');
                } else {
                  safeAlert('失败', '执行关机失败，请确保应用拥有设备所有者权限');
                }
              } catch (error: any) {
                if (!isMountedRef.current) return;
                safeAlert('错误', `执行关机失败: ${error.message || error}`);
              }
            },
          },
        ]
      );
    } catch (error: any) {
      if (!isMountedRef.current) return;
      safeAlert('错误', `执行关机失败: ${error.message || error}`);
    }
  };

  const handleSetScheduledBoot = async () => {
    try {
      const hour = parseInt(bootHour, 10);
      const minute = parseInt(bootMinute, 10);

      if (isNaN(hour) || hour < 0 || hour > 23) {
        safeAlert('错误', '小时必须在 0-23 之间');
        return;
      }
      if (isNaN(minute) || minute < 0 || minute > 59) {
        safeAlert('错误', '分钟必须在 0-59 之间');
        return;
      }

      const success = await KioskManager.setScheduledBoot(hour, minute, bootRepeat);
      if (!isMountedRef.current) return;

      const settings = await KioskManager.getScheduledBoot();
      if (!isMountedRef.current) return;
      setScheduledBoot(settings);

      if (success) {
        safeAlert(
          '成功',
          `定时开机已设置: ${String(hour).padStart(2, '0')}:${String(minute).padStart(2, '0')}${bootRepeat ? ' (每天重复)' : ' (单次)'}\n\n注意：定时开机功能需要硬件支持，某些设备可能不支持。`
        );
      } else {
        safeAlert('提示', '定时开机设置已保存，但设备可能不支持此功能。');
      }
    } catch (error: any) {
      if (!isMountedRef.current) return;
      safeAlert('错误', `设置定时开机失败: ${error.message || error}`);
    }
  };

  const handleCancelScheduledBoot = async () => {
    try {
      const success = await KioskManager.cancelScheduledBoot();
      if (!isMountedRef.current) return;

      if (success) {
        setScheduledBoot(null);
        safeAlert('成功', '定时开机已取消');
      } else {
        safeAlert('失败', '取消定时开机失败');
      }
    } catch (error: any) {
      if (!isMountedRef.current) return;
      safeAlert('错误', `取消定时开机失败: ${error.message || error}`);
    }
  };

  return (
    <View style={styles.container}>
      <StatusBar barStyle={isDarkMode ? 'light-content' : 'dark-content'} />
      <ScrollView
        style={styles.scrollView}
        contentContainerStyle={[
          styles.scrollContent,
          isTablet && { paddingHorizontal: 40, paddingVertical: 30 },
          isLandscape && { paddingHorizontal: 30 },
        ]}
        showsVerticalScrollIndicator={false}
      >
        <View style={[styles.header, isTablet && { marginBottom: 40 }]}>
          <Text style={styles.title}>Kiosk Manager Example</Text>
          <Text style={styles.subtitle}>
            {isTablet ? '平板模式' : '手机模式'} • {isLandscape ? '横屏' : '竖屏'}
              </Text>
              </View>

        <TabBar activeTab={activeTab} onTabChange={setActiveTab} isDarkMode={isDarkMode} />

        {activeTab === 'kiosk' && (
          <KioskControlTab
            bootAutoStart={bootAutoStart}
            isDeviceOwner={isDeviceOwner}
            isLockTaskPackageSetup={isLockTaskPackageSetup}
            onStartKiosk={handleStartKiosk}
            onStopKiosk={handleStopKiosk}
            onCheckBootAutoStart={handleCheckBootAutoStart}
            onRequestDeviceAdmin={handleRequestDeviceAdmin}
            onCheckDeviceOwner={handleCheckDeviceOwner}
            onSetupLockTaskPackage={handleSetupLockTaskPackage}
            onClearDeviceOwner={handleClearDeviceOwner}
            onToggleBootAutoStart={handleToggleBootAutoStart}
          />
        )}

        {activeTab === 'update' && (
          <ApkUpdateTab
            apkUrl={apkUrl}
            packageName={packageName}
            downloadResult={downloadResult}
            isDownloading={isDownloading}
            isInstalling={isInstalling}
            isLaunching={isLaunching}
            isCheckingInstall={isCheckingInstall}
            isAppInstalledStatus={isAppInstalledStatus}
            downloadProgress={downloadProgress}
            installStatus={installStatus}
            ringerMode={ringerMode}
            hasDndAccess={hasDndAccess}
            onApkUrlChange={setApkUrl}
            onPackageNameChange={(text) => {
                setPackageName(text);
              setIsAppInstalledStatus(null);
            }}
            onDownloadApk={handleDownloadApk}
            onInstallApk={handleInstallApk}
            onDownloadAndInstall={handleDownloadAndInstall}
            onDownloadAndSilentInstall={handleDownloadAndSilentInstall}
            onDownloadAndSilentInstallAndLaunch={handleDownloadAndSilentInstallAndLaunch}
            onCheckInstallPermission={handleCheckInstallPermission}
            onRequestInstallPermission={handleRequestInstallPermission}
            onCheckAppInstalled={handleCheckAppInstalled}
            onLaunchApp={handleLaunchApp}
          />
        )}

        {activeTab === 'files' && (
          <FileManagementTab
            downloadedFiles={downloadedFiles}
            isLoadingFiles={isLoadingFiles}
            onGetDownloadedFiles={handleGetDownloadedFiles}
            onClearAllFiles={handleClearAllFiles}
            onInstallApk={handleInstallApk}
            onSilentInstallApk={handleSilentInstallApk}
            onSilentInstallAndLaunchApk={handleSilentInstallAndLaunchApk}
            onSystemSilentInstallApk={handleSystemSilentInstallApk}
            onDeleteFile={handleDeleteFile}
          />
        )}

        {activeTab === 'av' && (
          <BrightnessVolumeTab
            hasWriteSettings={hasWriteSettings}
            systemBrightness={systemBrightness}
            appBrightness={appBrightness}
            volumes={volumes}
            globalVolume={globalVolume}
            mutedMap={mutedMap}
            globalMuted={globalMuted}
            ringerMode={ringerMode}
            hasDndAccess={hasDndAccess}
            onHasWriteSettingsChange={setHasWriteSettings}
            onSystemBrightnessChange={setSystemBrightness}
            onAppBrightnessChange={setAppBrightness}
            onVolumesChange={setVolumes}
            onGlobalVolumeChange={setGlobalVolume}
            onMutedMapChange={setMutedMap}
            onGlobalMutedChange={setGlobalMuted}
            onRingerModeChange={setRingerMode}
            onHasDndAccessChange={setHasDndAccess}
          />
        )}

        {activeTab === 'power' && (
          <PowerScheduleTab
            scheduledShutdown={scheduledShutdown}
            scheduledBoot={scheduledBoot}
            shutdownHour={shutdownHour}
            shutdownMinute={shutdownMinute}
            shutdownRepeat={shutdownRepeat}
            bootHour={bootHour}
            bootMinute={bootMinute}
            bootRepeat={bootRepeat}
            onShutdownHourChange={setShutdownHour}
            onShutdownMinuteChange={setShutdownMinute}
            onShutdownRepeatChange={setShutdownRepeat}
            onBootHourChange={setBootHour}
            onBootMinuteChange={setBootMinute}
            onBootRepeatChange={setBootRepeat}
            onSetScheduledShutdown={handleSetScheduledShutdown}
            onCancelScheduledShutdown={handleCancelScheduledShutdown}
            onPerformShutdown={handlePerformShutdown}
            onSetScheduledBoot={handleSetScheduledBoot}
            onCancelScheduledBoot={handleCancelScheduledBoot}
          />
        )}
      </ScrollView>
    </View>
  );
}
