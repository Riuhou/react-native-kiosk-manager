import type { KioskManagerType, DownloadProgress } from './KioskManager.type';
import { NativeEventEmitter, NativeModules, Platform } from 'react-native';

// 只在Android平台上导入TurboModule
let KioskManagerTurboModule: any = null;
if (Platform.OS === 'android') {
  try {
    KioskManagerTurboModule = require('./NativeKioskManager').default;
  } catch (error) {
    console.warn('KioskManager: Failed to load TurboModule:', error);
  }
}

// 创建事件发射器
const eventEmitter = new NativeEventEmitter(NativeModules.KioskManager);

// 存储进度监听器
const progressListeners: Set<(progress: DownloadProgress) => void> = new Set();

// 监听原生事件
eventEmitter.addListener('KioskManagerDownloadProgress', (progress: DownloadProgress) => {
  progressListeners.forEach(listener => listener(progress));
});

const KioskManager: KioskManagerType = {
  startKiosk: () => {
    if (!KioskManagerTurboModule) {
      console.warn('KioskManager: TurboModule not available');
      return;
    }
    KioskManagerTurboModule.startKiosk();
  },
  stopKiosk: () => {
    if (!KioskManagerTurboModule) {
      console.warn('KioskManager: TurboModule not available');
      return;
    }
    KioskManagerTurboModule.stopKiosk();
  },
  enableBootAutoStart: (enabled: boolean) => {
    if (!KioskManagerTurboModule) {
      console.warn('KioskManager: TurboModule not available');
      return;
    }
    KioskManagerTurboModule.enableBootAutoStart(enabled);
  },
  isBootAutoStartEnabled: () => {
    if (!KioskManagerTurboModule) {
      console.warn('KioskManager: TurboModule not available');
      return Promise.resolve(false);
    }
    return KioskManagerTurboModule.isBootAutoStartEnabled();
  },
  setupLockTaskPackage: () => {
    if (!KioskManagerTurboModule) {
      console.warn('KioskManager: TurboModule not available');
      return Promise.resolve(false);
    }
    return KioskManagerTurboModule.setupLockTaskPackage();
  },
  requestDeviceAdmin: () => {
    if (!KioskManagerTurboModule) {
      console.warn('KioskManager: TurboModule not available');
      return Promise.resolve(false);
    }
    return KioskManagerTurboModule.requestDeviceAdmin();
  },
  clearDeviceOwner: () => {
    if (!KioskManagerTurboModule) {
      console.warn('KioskManager: TurboModule not available');
      return Promise.resolve(false);
    }
    return KioskManagerTurboModule.clearDeviceOwner();
  },
  isDeviceOwner: () => {
    if (!KioskManagerTurboModule) {
      console.warn('KioskManager: TurboModule not available');
      return Promise.resolve(false);
    }
    return KioskManagerTurboModule.isDeviceOwner();
  },
  
  // APK 更新相关方法
  downloadApk: (url: string) => {
    if (!KioskManagerTurboModule) {
      console.warn('KioskManager: TurboModule not available');
      return Promise.reject(new Error('TurboModule not available'));
    }
    return KioskManagerTurboModule.downloadApk(url);
  },
  installApk: (filePath: string) => {
    if (!KioskManagerTurboModule) {
      console.warn('KioskManager: TurboModule not available');
      return Promise.resolve(false);
    }
    return KioskManagerTurboModule.installApk(filePath);
  },
  downloadAndInstallApk: (url: string) => {
    if (!KioskManagerTurboModule) {
      console.warn('KioskManager: TurboModule not available');
      return Promise.resolve(false);
    }
    return KioskManagerTurboModule.downloadAndInstallApk(url);
  },
  checkInstallPermission: () => {
    if (!KioskManagerTurboModule) {
      console.warn('KioskManager: TurboModule not available');
      return Promise.resolve(false);
    }
    return KioskManagerTurboModule.checkInstallPermission();
  },
  requestInstallPermission: () => {
    if (!KioskManagerTurboModule) {
      console.warn('KioskManager: TurboModule not available');
      return Promise.resolve(false);
    }
    return KioskManagerTurboModule.requestInstallPermission();
  },
  
  // 静默安装相关方法
  silentInstallApk: (filePath: string) => {
    if (!KioskManagerTurboModule) {
      console.warn('KioskManager: TurboModule not available');
      return Promise.resolve(false);
    }
    return KioskManagerTurboModule.silentInstallApk(filePath);
  },
  downloadAndSilentInstallApk: (url: string) => {
    if (!KioskManagerTurboModule) {
      console.warn('KioskManager: TurboModule not available');
      return Promise.resolve(false);
    }
    return KioskManagerTurboModule.downloadAndSilentInstallApk(url);
  },
  silentInstallAndLaunchApk: (filePath: string) => {
    if (!KioskManagerTurboModule) {
      console.warn('KioskManager: TurboModule not available');
      return Promise.resolve(false);
    }
    return KioskManagerTurboModule.silentInstallAndLaunchApk(filePath);
  },
  downloadAndSilentInstallAndLaunchApk: (url: string) => {
    if (!KioskManagerTurboModule) {
      console.warn('KioskManager: TurboModule not available');
      return Promise.resolve(false);
    }
    return KioskManagerTurboModule.downloadAndSilentInstallAndLaunchApk(url);
  },
  systemSilentInstallApk: (filePath: string) => {
    if (!KioskManagerTurboModule) {
      console.warn('KioskManager: TurboModule not available');
      return Promise.resolve(false);
    }
    return KioskManagerTurboModule.systemSilentInstallApk(filePath);
  },
  
  // 事件监听器
  addDownloadProgressListener: (callback: (progress: DownloadProgress) => void) => {
    progressListeners.add(callback);
  },
  removeDownloadProgressListener: (callback: (progress: DownloadProgress) => void) => {
    progressListeners.delete(callback);
  },
  
  // 文件管理方法
  getDownloadedFiles: () => {
    if (!KioskManagerTurboModule) {
      console.warn('KioskManager: TurboModule not available');
      return Promise.resolve([]);
    }
    return KioskManagerTurboModule.getDownloadedFiles();
  },
  deleteDownloadedFile: (filePath: string) => {
    if (!KioskManagerTurboModule) {
      console.warn('KioskManager: TurboModule not available');
      return Promise.resolve(false);
    }
    return KioskManagerTurboModule.deleteDownloadedFile(filePath);
  },
  clearAllDownloadedFiles: () => {
    if (!KioskManagerTurboModule) {
      console.warn('KioskManager: TurboModule not available');
      return Promise.resolve(0);
    }
    return KioskManagerTurboModule.clearAllDownloadedFiles();
  },
};

export default KioskManager;
