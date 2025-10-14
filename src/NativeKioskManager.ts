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
  
  // 文件管理方法
  getDownloadedFiles(): Promise<DownloadedFile[]>;
  deleteDownloadedFile(filePath: string): Promise<boolean>;
  clearAllDownloadedFiles(): Promise<number>;
}

export default TurboModuleRegistry.getEnforcing<Spec>('KioskManager');
