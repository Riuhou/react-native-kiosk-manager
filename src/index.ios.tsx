import type { KioskManagerType, DownloadProgress } from './KioskManager.type';

// iOS平台的空实现，因为Kiosk模式功能仅在Android上可用
const KioskManager: KioskManagerType = {
  startKiosk: () => {
    console.warn('KioskManager: startKiosk is not supported on iOS');
  },
  stopKiosk: () => {
    console.warn('KioskManager: stopKiosk is not supported on iOS');
  },
  enableBootAutoStart: (_enabled: boolean) => {
    console.warn('KioskManager: enableBootAutoStart is not supported on iOS');
  },
  isBootAutoStartEnabled: () => {
    console.warn('KioskManager: isBootAutoStartEnabled is not supported on iOS');
    return Promise.resolve(false);
  },
  setupLockTaskPackage: () => {
    console.warn('KioskManager: setupLockTaskPackage is not supported on iOS');
    return Promise.resolve(false);
  },
  requestDeviceAdmin: () => {
    console.warn('KioskManager: requestDeviceAdmin is not supported on iOS');
    return Promise.resolve(false);
  },
  clearDeviceOwner: () => {
    console.warn('KioskManager: clearDeviceOwner is not supported on iOS');
    return Promise.resolve(false);
  },
  isDeviceOwner: () => {
    console.warn('KioskManager: isDeviceOwner is not supported on iOS');
    return Promise.resolve(false);
  },
  
  // APK 更新相关方法
  downloadApk: (_url: string) => {
    console.warn('KioskManager: downloadApk is not supported on iOS');
    return Promise.reject(new Error('APK download is not supported on iOS'));
  },
  installApk: (_filePath: string) => {
    console.warn('KioskManager: installApk is not supported on iOS');
    return Promise.resolve(false);
  },
  downloadAndInstallApk: (_url: string) => {
    console.warn('KioskManager: downloadAndInstallApk is not supported on iOS');
    return Promise.resolve(false);
  },
  checkInstallPermission: () => {
    console.warn('KioskManager: checkInstallPermission is not supported on iOS');
    return Promise.resolve(false);
  },
  requestInstallPermission: () => {
    console.warn('KioskManager: requestInstallPermission is not supported on iOS');
    return Promise.resolve(false);
  },
  
  // 静默安装相关方法
  silentInstallApk: (_filePath: string) => {
    console.warn('KioskManager: silentInstallApk is not supported on iOS');
    return Promise.resolve(false);
  },
  downloadAndSilentInstallApk: (_url: string) => {
    console.warn('KioskManager: downloadAndSilentInstallApk is not supported on iOS');
    return Promise.resolve(false);
  },
  silentInstallAndLaunchApk: (_filePath: string) => {
    console.warn('KioskManager: silentInstallAndLaunchApk is not supported on iOS');
    return Promise.resolve(false);
  },
  downloadAndSilentInstallAndLaunchApk: (_url: string) => {
    console.warn('KioskManager: downloadAndSilentInstallAndLaunchApk is not supported on iOS');
    return Promise.resolve(false);
  },
  systemSilentInstallApk: (_filePath: string) => {
    console.warn('KioskManager: systemSilentInstallApk is not supported on iOS');
    return Promise.resolve(false);
  },
  
  // 事件监听器
  addDownloadProgressListener: (_callback: (progress: DownloadProgress) => void) => {
    console.warn('KioskManager: addDownloadProgressListener is not supported on iOS');
  },
  removeDownloadProgressListener: (_callback: (progress: DownloadProgress) => void) => {
    console.warn('KioskManager: removeDownloadProgressListener is not supported on iOS');
  },
  
  // 文件管理方法
  getDownloadedFiles: () => {
    console.warn('KioskManager: getDownloadedFiles is not supported on iOS');
    return Promise.resolve([]);
  },
  deleteDownloadedFile: (_filePath: string) => {
    console.warn('KioskManager: deleteDownloadedFile is not supported on iOS');
    return Promise.resolve(false);
  },
  clearAllDownloadedFiles: () => {
    console.warn('KioskManager: clearAllDownloadedFiles is not supported on iOS');
    return Promise.resolve(0);
  },
};

export default KioskManager;
