export interface KioskManagerType {
  startKiosk: () => void;
  stopKiosk: () => void;
  enableBootAutoStart: (enabled: boolean) => void;
  isBootAutoStartEnabled: () => Promise<boolean>;
  setupLockTaskPackage: () => Promise<boolean>;
  requestDeviceAdmin: () => Promise<boolean>;
  clearDeviceOwner: () => Promise<boolean>;
  isDeviceOwner: () => Promise<boolean>;
}
