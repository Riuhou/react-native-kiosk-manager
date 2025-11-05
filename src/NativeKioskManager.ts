import type { TurboModule } from 'react-native';
import { TurboModuleRegistry } from 'react-native';

export interface DownloadResult {
  filePath: string;
  fileName: string;
  fileSize: number;
}

export interface DownloadedFile {
  fileName: string;
  filePath: string;
  fileSize: number;
  lastModified: number;
  canRead: boolean;
  canWrite: boolean;
}

export interface Spec extends TurboModule {
  startKiosk(): void;
  stopKiosk(): void;
  enableBootAutoStart(enabled: boolean): void;
  isBootAutoStartEnabled(): Promise<boolean>;
  requestDeviceAdmin(): Promise<boolean>;
  setupLockTaskPackage(): Promise<boolean>;
  clearDeviceOwner(): Promise<boolean>;
  isDeviceOwner(): Promise<boolean>;
  
  // 亮度与音量控制（Android）
  hasWriteSettingsPermission(): Promise<boolean>;
  requestWriteSettingsPermission(): Promise<boolean>;
  setSystemBrightness(value: number): Promise<boolean>;
  getSystemBrightness(): Promise<number>;
  setAppBrightness(value: number): void;
  getAppBrightness(): Promise<number>;
  resetAppBrightness(): void;
  setVolume(stream: string, value: number): Promise<boolean>;
  getVolume(stream: string): Promise<number>;
  getMaxVolume(stream: string): Promise<number>;
  setGlobalVolume(value: number): Promise<boolean>;
  getGlobalVolume(): Promise<number>;
  setMute(stream: string, muted: boolean): Promise<boolean>;
  isMuted(stream: string): Promise<boolean>;
  setGlobalMute(muted: boolean): Promise<boolean>;
  isGlobalMuted(): Promise<boolean>;
  // 系统铃声模式与免打扰
  getRingerMode(): Promise<'silent' | 'vibrate' | 'normal'>;
  setRingerMode(mode: 'silent' | 'vibrate' | 'normal'): Promise<boolean>;
  hasNotificationPolicyAccess(): Promise<boolean>;
  requestNotificationPolicyAccess(): Promise<boolean>;
  // 亮度/音量观察
  startObservingSystemAv(): void;
  stopObservingSystemAv(): void;
  
  // APK 更新相关方法
  downloadApk(url: string): Promise<DownloadResult>;
  installApk(filePath: string): Promise<boolean>;
  downloadAndInstallApk(url: string): Promise<boolean>;
  checkInstallPermission(): Promise<boolean>;
  requestInstallPermission(): Promise<boolean>;
  
  // 静默安装相关方法
  silentInstallApk(filePath: string): Promise<boolean>;
  downloadAndSilentInstallApk(url: string): Promise<boolean>;
  silentInstallAndLaunchApk(filePath: string): Promise<boolean>;
  downloadAndSilentInstallAndLaunchApk(url: string): Promise<boolean>;
  systemSilentInstallApk(filePath: string): Promise<boolean>;
  
  // 应用启动方法
  isAppInstalled(packageName: string): Promise<boolean>;
  launchApp(packageName: string): Promise<boolean>;
  
  // 文件管理方法
  getDownloadedFiles(): Promise<DownloadedFile[]>;
  deleteDownloadedFile(filePath: string): Promise<boolean>;
  clearAllDownloadedFiles(): Promise<number>;
}

export default TurboModuleRegistry.getEnforcing<Spec>('KioskManager');
