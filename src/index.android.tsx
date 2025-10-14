import KioskManagerTurboModule from './NativeKioskManager';
import type { KioskManagerType, DownloadProgress } from './KioskManager.type';
import { NativeEventEmitter, NativeModules } from 'react-native';

// 创建事件发射器
const eventEmitter = new NativeEventEmitter(NativeModules.KioskManager);

// 存储进度监听器
const progressListeners: Set<(progress: DownloadProgress) => void> = new Set();

// 监听原生事件
eventEmitter.addListener('KioskManagerDownloadProgress', (progress: DownloadProgress) => {
  progressListeners.forEach(listener => listener(progress));
});

const KioskManager: KioskManagerType = {
  startKiosk: () => KioskManagerTurboModule?.startKiosk(),
  stopKiosk: () => KioskManagerTurboModule?.stopKiosk(),
  enableBootAutoStart: (enabled: boolean) =>
    KioskManagerTurboModule?.enableBootAutoStart(enabled),
  isBootAutoStartEnabled: () =>
    KioskManagerTurboModule?.isBootAutoStartEnabled(),
  setupLockTaskPackage: () => KioskManagerTurboModule?.setupLockTaskPackage(),
  requestDeviceAdmin: () => KioskManagerTurboModule?.requestDeviceAdmin(),
  clearDeviceOwner: () => KioskManagerTurboModule?.clearDeviceOwner(),
  isDeviceOwner: () => KioskManagerTurboModule?.isDeviceOwner(),
  
  // APK 更新相关方法
  downloadApk: (url: string) => KioskManagerTurboModule?.downloadApk(url),
  installApk: (filePath: string) => KioskManagerTurboModule?.installApk(filePath),
  downloadAndInstallApk: (url: string) => KioskManagerTurboModule?.downloadAndInstallApk(url),
  checkInstallPermission: () => KioskManagerTurboModule?.checkInstallPermission(),
  requestInstallPermission: () => KioskManagerTurboModule?.requestInstallPermission(),
  
  // 事件监听器
  addDownloadProgressListener: (callback: (progress: DownloadProgress) => void) => {
    progressListeners.add(callback);
  },
  removeDownloadProgressListener: (callback: (progress: DownloadProgress) => void) => {
    progressListeners.delete(callback);
  },
  
  // 文件管理方法
  getDownloadedFiles: () => KioskManagerTurboModule?.getDownloadedFiles(),
  deleteDownloadedFile: (filePath: string) => KioskManagerTurboModule?.deleteDownloadedFile(filePath),
  clearAllDownloadedFiles: () => KioskManagerTurboModule?.clearAllDownloadedFiles(),
};

export default KioskManager;
