import type {
  KioskManagerType,
  DownloadResult,
  DownloadProgress,
  DownloadedFile,
  InstallStatus,
  ScheduledPowerSettings,
} from './KioskManager.type';

const warnedMethods = new Set<keyof KioskManagerType>();

const warn = (method: keyof KioskManagerType) => {
  if (!warnedMethods.has(method)) {
    console.warn(`KioskManager: 方法 ${String(method)} 仅支持 Android 平台。`);
    warnedMethods.add(method);
  }
};

const unsupportedVoid = (method: keyof KioskManagerType) => {
  warn(method);
};

const unsupportedPromise = <T,>(method: keyof KioskManagerType, fallback: T): Promise<T> => {
  warn(method);
  return Promise.resolve(fallback);
};

const unsupportedReject = <T,>(method: keyof KioskManagerType): Promise<T> => {
  warn(method);
  return Promise.reject(new Error(`KioskManager: 方法 ${String(method)} 仅支持 Android 平台。`));
};

const KioskManager: KioskManagerType = {
  startKiosk: () => unsupportedVoid('startKiosk'),
  stopKiosk: () => unsupportedVoid('stopKiosk'),
  enableBootAutoStart: (_enabled: boolean) => {
    unsupportedVoid('enableBootAutoStart');
  },
  isBootAutoStartEnabled: () => unsupportedPromise('isBootAutoStartEnabled', false),
  setupLockTaskPackage: () => unsupportedPromise('setupLockTaskPackage', false),
  requestDeviceAdmin: () => unsupportedPromise('requestDeviceAdmin', false),
  clearDeviceOwner: () => unsupportedPromise('clearDeviceOwner', false),
  isDeviceOwner: () => unsupportedPromise('isDeviceOwner', false),

  hasWriteSettingsPermission: () => unsupportedPromise('hasWriteSettingsPermission', false),
  requestWriteSettingsPermission: () => unsupportedPromise('requestWriteSettingsPermission', false),
  setSystemBrightness: (_value: number) => unsupportedPromise('setSystemBrightness', false),
  getSystemBrightness: () => unsupportedPromise('getSystemBrightness', 0),
  setAppBrightness: (_value: number) => {
    unsupportedVoid('setAppBrightness');
  },
  resetAppBrightness: () => unsupportedVoid('resetAppBrightness'),
  getAppBrightness: () => unsupportedPromise('getAppBrightness', -1),
  setVolume: (_stream, _value: number) => unsupportedPromise('setVolume', false),
  getVolume: (_stream) => unsupportedPromise('getVolume', 0),
  getMaxVolume: (_stream) => unsupportedPromise('getMaxVolume', 0),
  setGlobalVolume: (_value: number) => unsupportedPromise('setGlobalVolume', false),
  getGlobalVolume: () => unsupportedPromise('getGlobalVolume', 0),
  setMute: (_stream, _muted: boolean) => unsupportedPromise('setMute', false),
  isMuted: (_stream) => unsupportedPromise('isMuted', false),
  setGlobalMute: (_muted: boolean) => unsupportedPromise('setGlobalMute', false),
  isGlobalMuted: () => unsupportedPromise('isGlobalMuted', false),
  getRingerMode: () => unsupportedPromise('getRingerMode', 'normal' as const),
  setRingerMode: (_mode: 'silent' | 'vibrate' | 'normal') => unsupportedPromise('setRingerMode', false),
  hasNotificationPolicyAccess: () => unsupportedPromise('hasNotificationPolicyAccess', false),
  requestNotificationPolicyAccess: () => unsupportedPromise('requestNotificationPolicyAccess', false),

  startObservingSystemAv: () => unsupportedVoid('startObservingSystemAv'),
  stopObservingSystemAv: () => unsupportedVoid('stopObservingSystemAv'),
  addSystemBrightnessListener: (_cb: (v: number) => void) => {
    unsupportedVoid('addSystemBrightnessListener');
  },
  removeSystemBrightnessListener: (_cb: (v: number) => void) => {
    unsupportedVoid('removeSystemBrightnessListener');
  },
  addVolumeChangedListener: (_cb: (d: { stream: string; index: number; max: number; value: number }) => void) => {
    unsupportedVoid('addVolumeChangedListener');
  },
  removeVolumeChangedListener: (_cb: (d: { stream: string; index: number; max: number; value: number }) => void) => {
    unsupportedVoid('removeVolumeChangedListener');
  },
  addGlobalVolumeChangedListener: (_cb: (v: number) => void) => {
    unsupportedVoid('addGlobalVolumeChangedListener');
  },
  removeGlobalVolumeChangedListener: (_cb: (v: number) => void) => {
    unsupportedVoid('removeGlobalVolumeChangedListener');
  },
  addRingerModeChangedListener: (_cb: (m: 'silent' | 'vibrate' | 'normal') => void) => {
    unsupportedVoid('addRingerModeChangedListener');
  },
  removeRingerModeChangedListener: (_cb: (m: 'silent' | 'vibrate' | 'normal') => void) => {
    unsupportedVoid('removeRingerModeChangedListener');
  },

  downloadApk: (_url: string) => unsupportedReject<DownloadResult>('downloadApk'),
  installApk: (_filePath: string) => unsupportedPromise('installApk', false),
  downloadAndInstallApk: (_url: string) => unsupportedPromise('downloadAndInstallApk', false),
  checkInstallPermission: () => unsupportedPromise('checkInstallPermission', false),
  requestInstallPermission: () => unsupportedPromise('requestInstallPermission', false),

  silentInstallApk: (_filePath: string) => unsupportedPromise('silentInstallApk', false),
  downloadAndSilentInstallApk: (_url: string) => unsupportedPromise('downloadAndSilentInstallApk', false),
  silentInstallAndLaunchApk: (_filePath: string) => unsupportedPromise('silentInstallAndLaunchApk', false),
  downloadAndSilentInstallAndLaunchApk: (_url: string) =>
    unsupportedPromise('downloadAndSilentInstallAndLaunchApk', false),
  systemSilentInstallApk: (_filePath: string) => unsupportedPromise('systemSilentInstallApk', false),
  isAppInstalled: (_packageName: string) => unsupportedPromise('isAppInstalled', false),
  launchApp: (_packageName: string) => unsupportedPromise('launchApp', false),

  addDownloadProgressListener: (_callback: (progress: DownloadProgress) => void) => {
    unsupportedVoid('addDownloadProgressListener');
  },
  removeDownloadProgressListener: (_callback: (progress: DownloadProgress) => void) => {
    unsupportedVoid('removeDownloadProgressListener');
  },
  addInstallStatusListener: (_callback: (status: InstallStatus) => void) => {
    unsupportedVoid('addInstallStatusListener');
  },
  removeInstallStatusListener: (_callback: (status: InstallStatus) => void) => {
    unsupportedVoid('removeInstallStatusListener');
  },

  getDownloadedFiles: () => unsupportedPromise<DownloadedFile[]>('getDownloadedFiles', []),
  deleteDownloadedFile: (_filePath: string) => unsupportedPromise('deleteDownloadedFile', false),
  clearAllDownloadedFiles: () => unsupportedPromise('clearAllDownloadedFiles', 0),

  setScheduledShutdown: (_hour: number, _minute: number, _repeat: boolean) =>
    unsupportedPromise('setScheduledShutdown', false),
  cancelScheduledShutdown: () => unsupportedPromise('cancelScheduledShutdown', false),
  getScheduledShutdown: () => unsupportedPromise<ScheduledPowerSettings | null>('getScheduledShutdown', null),
  performShutdown: () => unsupportedPromise('performShutdown', false),
  setScheduledBoot: (_hour: number, _minute: number, _repeat: boolean) =>
    unsupportedPromise('setScheduledBoot', false),
  cancelScheduledBoot: () => unsupportedPromise('cancelScheduledBoot', false),
  getScheduledBoot: () => unsupportedPromise<ScheduledPowerSettings | null>('getScheduledBoot', null),
};

export default KioskManager;
