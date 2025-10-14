export interface DownloadResult {
  filePath: string;
  fileName: string;
  fileSize: number;
}

export interface DownloadProgress {
  progress: number;        // 进度百分比 (0-100)
  bytesRead: number;      // 已下载字节数
  totalBytes: number;     // 总字节数
  percentage: number;     // 进度百分比 (0.0-100.0)
}

export interface KioskManagerType {
  startKiosk: () => void;
  stopKiosk: () => void;
  enableBootAutoStart: (enabled: boolean) => void;
  isBootAutoStartEnabled: () => Promise<boolean>;
  setupLockTaskPackage: () => Promise<boolean>;
  requestDeviceAdmin: () => Promise<boolean>;
  clearDeviceOwner: () => Promise<boolean>;
  isDeviceOwner: () => Promise<boolean>;
  
  // APK 更新相关方法
  downloadApk: (url: string) => Promise<DownloadResult>;
  installApk: (filePath: string) => Promise<boolean>;
  downloadAndInstallApk: (url: string) => Promise<boolean>;
  checkInstallPermission: () => Promise<boolean>;
  requestInstallPermission: () => Promise<boolean>;
}
