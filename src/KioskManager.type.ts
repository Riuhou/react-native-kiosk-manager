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

export interface DownloadedFile {
  fileName: string;       // 文件名
  filePath: string;       // 文件完整路径
  fileSize: number;      // 文件大小（字节）
  lastModified: number;   // 最后修改时间（时间戳）
  canRead: boolean;      // 是否可读
  canWrite: boolean;     // 是否可写
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
  
  // 事件监听器
  addDownloadProgressListener: (callback: (progress: DownloadProgress) => void) => void;
  removeDownloadProgressListener: (callback: (progress: DownloadProgress) => void) => void;
  
  // 文件管理方法
  getDownloadedFiles: () => Promise<DownloadedFile[]>;
  deleteDownloadedFile: (filePath: string) => Promise<boolean>;
  clearAllDownloadedFiles: () => Promise<number>;
}
