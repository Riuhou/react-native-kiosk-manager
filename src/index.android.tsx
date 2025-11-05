import type { KioskManagerType, DownloadProgress, InstallStatus } from './KioskManager.type';
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
// 安装状态监听器集合
const installStatusListeners: Set<(status: InstallStatus) => void> = new Set();
// 系统亮度/音量监听器集合
const systemBrightnessListeners: Set<(v: number) => void> = new Set();
const volumeChangedListeners: Set<(data: { stream: string; index: number; max: number; value: number }) => void> = new Set();
const globalVolumeChangedListeners: Set<(v: number) => void> = new Set();
const ringerModeChangedListeners: Set<(mode: 'silent' | 'vibrate' | 'normal') => void> = new Set();

// 监听原生事件
eventEmitter.addListener('KioskManagerDownloadProgress', (progress: DownloadProgress) => {
  progressListeners.forEach(listener => listener(progress));
});
eventEmitter.addListener('KioskManagerInstallStatus', (status: InstallStatus) => {
  installStatusListeners.forEach(listener => listener(status));
});
eventEmitter.addListener('KioskManagerSystemBrightnessChanged', (payload: { brightness: number }) => {
  const v = typeof payload?.brightness === 'number' ? payload.brightness : 0;
  systemBrightnessListeners.forEach(l => l(v));
});
eventEmitter.addListener('KioskManagerVolumeChanged', (payload: { stream: string; index: number; max: number; value: number }) => {
  if (!payload) return;
  volumeChangedListeners.forEach(l => l(payload));
});
eventEmitter.addListener('KioskManagerGlobalVolumeChanged', (payload: { value: number }) => {
  const v = typeof payload?.value === 'number' ? payload.value : 0;
  globalVolumeChangedListeners.forEach(l => l(v));
});
eventEmitter.addListener('KioskManagerRingerModeChanged', (payload: { mode: 'silent' | 'vibrate' | 'normal' }) => {
  const m = (payload?.mode ?? 'normal') as 'silent' | 'vibrate' | 'normal';
  ringerModeChangedListeners.forEach(l => l(m));
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
  
  // 亮度与音量控制（Android）
  hasWriteSettingsPermission: () => {
    if (!KioskManagerTurboModule) {
      console.warn('KioskManager: TurboModule not available');
      return Promise.resolve(false);
    }
    return KioskManagerTurboModule.hasWriteSettingsPermission();
  },
  requestWriteSettingsPermission: () => {
    if (!KioskManagerTurboModule) {
      console.warn('KioskManager: TurboModule not available');
      return Promise.resolve(false);
    }
    return KioskManagerTurboModule.requestWriteSettingsPermission();
  },
  setSystemBrightness: (value: number) => {
    if (!KioskManagerTurboModule) {
      console.warn('KioskManager: TurboModule not available');
      return Promise.resolve(false);
    }
    const v = Math.max(0, Math.min(255, Math.floor(value)));
    return KioskManagerTurboModule.setSystemBrightness(v);
  },
  getSystemBrightness: () => {
    if (!KioskManagerTurboModule) {
      console.warn('KioskManager: TurboModule not available');
      return Promise.resolve(0);
    }
    return KioskManagerTurboModule.getSystemBrightness();
  },
  setAppBrightness: (value: number) => {
    if (!KioskManagerTurboModule) {
      console.warn('KioskManager: TurboModule not available');
      return;
    }
    const v = Math.max(0, Math.min(1, value));
    KioskManagerTurboModule.setAppBrightness(v);
  },
  resetAppBrightness: () => {
    if (!KioskManagerTurboModule) {
      console.warn('KioskManager: TurboModule not available');
      return;
    }
    KioskManagerTurboModule.resetAppBrightness();
  },
  getAppBrightness: () => {
    if (!KioskManagerTurboModule) {
      console.warn('KioskManager: TurboModule not available');
      return Promise.resolve(-1);
    }
    return KioskManagerTurboModule.getAppBrightness();
  },
  setVolume: (stream, value) => {
    if (!KioskManagerTurboModule) {
      console.warn('KioskManager: TurboModule not available');
      return Promise.resolve(false);
    }
    const v = Math.max(0, Math.min(1, value));
    return KioskManagerTurboModule.setVolume(stream, v);
  },
  getVolume: (stream) => {
    if (!KioskManagerTurboModule) {
      console.warn('KioskManager: TurboModule not available');
      return Promise.resolve(0);
    }
    return KioskManagerTurboModule.getVolume(stream);
  },
  getMaxVolume: (stream) => {
    if (!KioskManagerTurboModule) {
      console.warn('KioskManager: TurboModule not available');
      return Promise.resolve(0);
    }
    return KioskManagerTurboModule.getMaxVolume(stream);
  },
  setGlobalVolume: (value: number) => {
    if (!KioskManagerTurboModule) {
      console.warn('KioskManager: TurboModule not available');
      return Promise.resolve(false);
    }
    const v = Math.max(0, Math.min(1, value));
    return KioskManagerTurboModule.setGlobalVolume(v);
  },
  getGlobalVolume: () => {
    if (!KioskManagerTurboModule) {
      console.warn('KioskManager: TurboModule not available');
      return Promise.resolve(0);
    }
    return KioskManagerTurboModule.getGlobalVolume();
  },
  setMute: (stream, muted) => {
    if (!KioskManagerTurboModule) {
      console.warn('KioskManager: TurboModule not available');
      return Promise.resolve(false);
    }
    return KioskManagerTurboModule.setMute(stream, !!muted);
  },
  isMuted: (stream) => {
    if (!KioskManagerTurboModule) {
      console.warn('KioskManager: TurboModule not available');
      return Promise.resolve(false);
    }
    return KioskManagerTurboModule.isMuted(stream);
  },
  setGlobalMute: (muted) => {
    if (!KioskManagerTurboModule) {
      console.warn('KioskManager: TurboModule not available');
      return Promise.resolve(false);
    }
    return KioskManagerTurboModule.setGlobalMute(!!muted);
  },
  isGlobalMuted: () => {
    if (!KioskManagerTurboModule) {
      console.warn('KioskManager: TurboModule not available');
      return Promise.resolve(false);
    }
    return KioskManagerTurboModule.isGlobalMuted();
  },
  getRingerMode: () => {
    if (!KioskManagerTurboModule) {
      console.warn('KioskManager: TurboModule not available');
      return Promise.resolve('normal');
    }
    return KioskManagerTurboModule.getRingerMode();
  },
  setRingerMode: (mode) => {
    if (!KioskManagerTurboModule) {
      console.warn('KioskManager: TurboModule not available');
      return Promise.resolve(false);
    }
    return KioskManagerTurboModule.setRingerMode(mode);
  },
  hasNotificationPolicyAccess: () => {
    if (!KioskManagerTurboModule) {
      console.warn('KioskManager: TurboModule not available');
      return Promise.resolve(false);
    }
    return KioskManagerTurboModule.hasNotificationPolicyAccess();
  },
  requestNotificationPolicyAccess: () => {
    if (!KioskManagerTurboModule) {
      console.warn('KioskManager: TurboModule not available');
      return Promise.resolve(false);
    }
    return KioskManagerTurboModule.requestNotificationPolicyAccess();
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
  isAppInstalled: (packageName: string) => {
    if (!KioskManagerTurboModule) {
      console.warn('KioskManager: TurboModule not available');
      return Promise.resolve(false);
    }
    return KioskManagerTurboModule.isAppInstalled(packageName);
  },
  launchApp: (packageName: string) => {
    if (!KioskManagerTurboModule) {
      console.warn('KioskManager: TurboModule not available');
      return Promise.resolve(false);
    }
    return KioskManagerTurboModule.launchApp(packageName);
  },
  // 观察系统亮度/音量变化
  startObservingSystemAv: () => {
    if (!KioskManagerTurboModule) {
      console.warn('KioskManager: TurboModule not available');
      return;
    }
    KioskManagerTurboModule.startObservingSystemAv();
  },
  stopObservingSystemAv: () => {
    if (!KioskManagerTurboModule) {
      console.warn('KioskManager: TurboModule not available');
      return;
    }
    KioskManagerTurboModule.stopObservingSystemAv();
  },
  addSystemBrightnessListener: (cb: (v: number) => void) => { systemBrightnessListeners.add(cb); },
  removeSystemBrightnessListener: (cb: (v: number) => void) => { systemBrightnessListeners.delete(cb); },
  addVolumeChangedListener: (cb: (d: { stream: string; index: number; max: number; value: number }) => void) => { volumeChangedListeners.add(cb); },
  removeVolumeChangedListener: (cb: (d: { stream: string; index: number; max: number; value: number }) => void) => { volumeChangedListeners.delete(cb); },
  addGlobalVolumeChangedListener: (cb: (v: number) => void) => { globalVolumeChangedListeners.add(cb); },
  removeGlobalVolumeChangedListener: (cb: (v: number) => void) => { globalVolumeChangedListeners.delete(cb); },
  addRingerModeChangedListener: (cb: (m: 'silent' | 'vibrate' | 'normal') => void) => { ringerModeChangedListeners.add(cb); },
  removeRingerModeChangedListener: (cb: (m: 'silent' | 'vibrate' | 'normal') => void) => { ringerModeChangedListeners.delete(cb); },
  
  // 事件监听器
  addDownloadProgressListener: (callback: (progress: DownloadProgress) => void) => {
    progressListeners.add(callback);
  },
  removeDownloadProgressListener: (callback: (progress: DownloadProgress) => void) => {
    progressListeners.delete(callback);
  },
  addInstallStatusListener: (callback: (status: InstallStatus) => void) => {
    installStatusListeners.add(callback);
  },
  removeInstallStatusListener: (callback: (status: InstallStatus) => void) => {
    installStatusListeners.delete(callback);
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
