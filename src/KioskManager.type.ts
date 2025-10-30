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
  
  // 亮度与音量控制（Android）
  hasWriteSettingsPermission: () => Promise<boolean>;
  requestWriteSettingsPermission: () => Promise<boolean>;
  setSystemBrightness: (value: number) => Promise<boolean>; // 0-255
  getSystemBrightness: () => Promise<number>; // 0-255
  setAppBrightness: (value: number) => void; // 0-1
  getAppBrightness: () => Promise<number>; // -1 表示跟随系统，或 0-1
  resetAppBrightness: () => void; // 恢复跟随系统亮度
  setVolume: (stream: 'music' | 'ring' | 'alarm' | 'notification' | 'system' | 'voice_call' | 'dtmf', value: number) => Promise<boolean>; // value: 0-1
  getVolume: (stream: 'music' | 'ring' | 'alarm' | 'notification' | 'system' | 'voice_call' | 'dtmf') => Promise<number>; // 0-1
  getMaxVolume: (stream: 'music' | 'ring' | 'alarm' | 'notification' | 'system' | 'voice_call' | 'dtmf') => Promise<number>; // 设备最大级别
  setGlobalVolume: (value: number) => Promise<boolean>; // 0-1，对多个音频流统一设置
  getGlobalVolume: () => Promise<number>; // 0-1，多流平均值
  setMute: (stream: 'music' | 'ring' | 'alarm' | 'notification' | 'system' | 'voice_call' | 'dtmf', muted: boolean) => Promise<boolean>;
  isMuted: (stream: 'music' | 'ring' | 'alarm' | 'notification' | 'system' | 'voice_call' | 'dtmf') => Promise<boolean>;
  setGlobalMute: (muted: boolean) => Promise<boolean>;
  isGlobalMuted: () => Promise<boolean>;
  
  // APK 更新相关方法
  downloadApk: (url: string) => Promise<DownloadResult>;
  installApk: (filePath: string) => Promise<boolean>;
  downloadAndInstallApk: (url: string) => Promise<boolean>;
  checkInstallPermission: () => Promise<boolean>;
  requestInstallPermission: () => Promise<boolean>;
  
  // 静默安装相关方法
  silentInstallApk: (filePath: string) => Promise<boolean>;
  downloadAndSilentInstallApk: (url: string) => Promise<boolean>;
  silentInstallAndLaunchApk: (filePath: string) => Promise<boolean>;
  downloadAndSilentInstallAndLaunchApk: (url: string) => Promise<boolean>;
  systemSilentInstallApk: (filePath: string) => Promise<boolean>;
  
  // 事件监听器
  addDownloadProgressListener: (callback: (progress: DownloadProgress) => void) => void;
  removeDownloadProgressListener: (callback: (progress: DownloadProgress) => void) => void;
  
  // 文件管理方法
  getDownloadedFiles: () => Promise<DownloadedFile[]>;
  deleteDownloadedFile: (filePath: string) => Promise<boolean>;
  clearAllDownloadedFiles: () => Promise<number>;
}
