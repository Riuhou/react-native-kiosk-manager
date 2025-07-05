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
};

export default KioskManager;
