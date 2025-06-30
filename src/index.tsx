import KioskManagerTurboModule from './NativeKioskManager';

export default {
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
