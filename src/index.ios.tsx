import type { KioskManagerType } from './KioskManager.type';

const KioskManager: KioskManagerType = {
  startKiosk: () => {
    throw new Error('KioskManager 仅支持 Android');
  },
  stopKiosk: () => {
    throw new Error('KioskManager 仅支持 Android');
  },
  enableBootAutoStart: () => {
    throw new Error('KioskManager 仅支持 Android');
  },
  isBootAutoStartEnabled: () => {
    throw new Error('KioskManager 仅支持 Android');
  },
  setupLockTaskPackage: () => {
    throw new Error('KioskManager 仅支持 Android');
  },
  requestDeviceAdmin: () => {
    throw new Error('KioskManager 仅支持 Android');
  },
  clearDeviceOwner: () => {
    throw new Error('KioskManager 仅支持 Android');
  },
  isDeviceOwner: () => {
    throw new Error('KioskManager 仅支持 Android');
  },
};

export default KioskManager;
