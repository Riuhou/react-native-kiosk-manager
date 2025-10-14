import KioskManagerTurboModule from './NativeKioskManager';
import type { KioskManagerType } from './KioskManager.type';

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
};

export default KioskManager;
