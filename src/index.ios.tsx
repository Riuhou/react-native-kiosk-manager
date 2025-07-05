import type { KioskManagerType } from './KioskManager.type';

const notSupported = () =>
  Promise.reject(new Error('KioskManager is only supported on Android'));

const KioskManager: KioskManagerType = {
  startKiosk: () => {},
  stopKiosk: () => {},
  enableBootAutoStart: () => {},
  isBootAutoStartEnabled: notSupported,
  setupLockTaskPackage: notSupported,
  requestDeviceAdmin: notSupported,
  clearDeviceOwner: notSupported,
  isDeviceOwner: notSupported,
};

export default KioskManager;
