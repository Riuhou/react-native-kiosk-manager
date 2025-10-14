import type { TurboModule } from 'react-native';
import { TurboModuleRegistry } from 'react-native';

export interface DownloadResult {
  filePath: string;
  fileName: string;
  fileSize: number;
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
}

export default TurboModuleRegistry.getEnforcing<Spec>('KioskManager');
