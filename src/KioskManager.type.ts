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

/**
 * KioskManager 接口定义
 * 
 * 提供完整的 Android Kiosk 模式管理功能，包括：
 * - Kiosk 模式控制（启动/停止、开机自启）
 * - 设备管理员和设备所有者管理
 * - 系统亮度和音量控制
 * - APK 下载和安装（普通安装和静默安装）
 * - 下载文件管理
 */
export interface KioskManagerType {
  /**
   * 启动 Kiosk 模式
   * 
   * 将应用锁定到前台运行，用户无法退出应用或访问其他应用。
   * 需要在设备管理员权限和设备所有者权限下才能正常工作。
   * 
   * @example
   * // 示例代码：
   * // 在确保已获得必要权限后启动 Kiosk 模式
   * const isOwner = await KioskManager.isDeviceOwner();
   * if (isOwner) {
   *   KioskManager.startKiosk();
   * } else {
   *   console.warn('需要设备所有者权限');
   * }
   
   */
  startKiosk: () => void;

  /**
   * 停止 Kiosk 模式
   * 
   * 退出 Kiosk 模式，允许用户正常使用设备。
   * 
   * @example
   * // 示例代码：
   * // 退出 Kiosk 模式
   * KioskManager.stopKiosk();
   
   */
  stopKiosk: () => void;

  /**
   * 设置开机自动启动 Kiosk 模式
   * 
   * @param enabled - 是否启用开机自动启动，true 为启用，false 为禁用
   * 
   * @example
   * // 示例代码：
   * // 启用开机自动启动
   * KioskManager.enableBootAutoStart(true);
   * 
   * // 禁用开机自动启动
   * KioskManager.enableBootAutoStart(false);
   
   */
  enableBootAutoStart: (enabled: boolean) => void;

  /**
   * 检查是否启用了开机自动启动
   * 
   * @returns Promise<boolean> - 返回 true 表示已启用，false 表示未启用
   * 
   * @example
   * // 示例代码：
   * const enabled = await KioskManager.isBootAutoStartEnabled();
   * console.log('开机自动启动状态:', enabled);
   
   */
  isBootAutoStartEnabled: () => Promise<boolean>;

  /**
   * 设置应用为锁定任务包
   * 
   * 这是启动 Kiosk 模式的前提条件之一。调用此方法后，应用才能进入锁定任务模式。
   * 需要设备管理员权限。
   * 
   * @returns Promise<boolean> - 返回 true 表示设置成功，false 表示失败
   * 
   * @example
   * // 示例代码：
   * const success = await KioskManager.setupLockTaskPackage();
   * if (success) {
   *   console.log('锁定任务包设置成功');
   *   KioskManager.startKiosk();
   * } else {
   *   console.error('锁定任务包设置失败');
   * }
   
   */
  setupLockTaskPackage: () => Promise<boolean>;

  /**
   * 请求设备管理员权限
   * 
   * 弹出系统对话框，请求用户授予设备管理员权限。
   * 这是使用 Kiosk 功能的基本权限要求。
   * 
   * @returns Promise<boolean> - 返回 true 表示用户授予了权限，false 表示拒绝
   * 
   * @example
   * // 示例代码：
   * const granted = await KioskManager.requestDeviceAdmin();
   * if (granted) {
   *   console.log('设备管理员权限已授予');
   *   // 继续设置锁定任务包
   *   await KioskManager.setupLockTaskPackage();
   * } else {
   *   console.warn('用户拒绝了设备管理员权限');
   * }
   
   */
  requestDeviceAdmin: () => Promise<boolean>;

  /**
   * 清除设备所有者权限
   * 
   * 移除应用的设备所有者权限。此操作需要应用已经是设备所有者。
   * 注意：清除后可能需要重新配置设备。
   * 
   * @returns Promise<boolean> - 返回 true 表示清除成功，false 表示失败
   * 
   * @example
   * // 示例代码：
   * // 谨慎使用，清除设备所有者权限
   * const success = await KioskManager.clearDeviceOwner();
   * if (success) {
   *   console.log('设备所有者权限已清除');
   * }
   
   */
  clearDeviceOwner: () => Promise<boolean>;

  /**
   * 检查应用是否为设备所有者
   * 
   * 设备所有者权限是 Android 企业设备管理中的最高权限级别，
   * 允许应用进行系统级配置和管理。
   * 
   * @returns Promise<boolean> - 返回 true 表示是设备所有者，false 表示不是
   * 
   * @example
   * // 示例代码：
   * const isOwner = await KioskManager.isDeviceOwner();
   * if (isOwner) {
   *   console.log('应用拥有设备所有者权限');
   *   // 可以进行高级操作，如静默安装等
   * } else {
   *   console.log('应用不是设备所有者');
   * }
   
   */
  isDeviceOwner: () => Promise<boolean>;
  
  // ========== 亮度与音量控制（Android） ==========

  /**
   * 检查是否有修改系统设置的权限
   * 
   * 修改系统亮度需要 WRITE_SETTINGS 权限，Android 6.0+ 需要用户手动授予。
   * 
   * @returns Promise<boolean> - 返回 true 表示有权限，false 表示没有
   * 
   * @example
   * // 示例代码：
   * const hasPermission = await KioskManager.hasWriteSettingsPermission();
   * if (!hasPermission) {
   *   // 请求权限
   *   await KioskManager.requestWriteSettingsPermission();
   * }
   
   */
  hasWriteSettingsPermission: () => Promise<boolean>;

  /**
   * 请求修改系统设置的权限
   * 
   * 会打开系统设置页面，引导用户手动授予 WRITE_SETTINGS 权限。
   * 
   * @returns Promise<boolean> - 返回 true 表示用户授予了权限，false 表示拒绝
   * 
   * @example
   * // 示例代码：
   * const granted = await KioskManager.requestWriteSettingsPermission();
   * if (granted) {
   *   // 现在可以修改系统亮度
   *   await KioskManager.setSystemBrightness(128);
   * }
   
   */
  requestWriteSettingsPermission: () => Promise<boolean>;

  /**
   * 设置系统亮度
   * 
   * 修改整个系统的屏幕亮度。需要 WRITE_SETTINGS 权限。
   * 
   * @param value - 亮度值，范围 0-255，0 为最暗，255 为最亮
   * @returns Promise<boolean> - 返回 true 表示设置成功，false 表示失败（通常是因为权限不足）
   * 
   * @example
   * // 示例代码：
   * // 设置系统亮度为中等（128/255）
   * const success = await KioskManager.setSystemBrightness(128);
   * 
   * // 设置系统亮度为最亮
   * await KioskManager.setSystemBrightness(255);
   * 
   * // 设置系统亮度为最暗
   * await KioskManager.setSystemBrightness(0);
   
   */
  setSystemBrightness: (value: number) => Promise<boolean>;

  /**
   * 获取当前系统亮度
   * 
   * @returns Promise<number> - 返回当前系统亮度值，范围 0-255
   * 
   * @example
   * // 示例代码：
   * const brightness = await KioskManager.getSystemBrightness();
   * console.log('当前系统亮度:', brightness, '/255');
   
   */
  getSystemBrightness: () => Promise<number>;

  /**
   * 设置应用内亮度
   * 
   * 只影响当前应用的屏幕亮度，不影响系统设置。
   * 设置后应用会覆盖系统亮度设置。
   * 
   * @param value - 亮度值，范围 0-1，0 为最暗，1 为最亮
   * 
   * @example
   * // 示例代码：
   * // 设置应用亮度为 80%
   * KioskManager.setAppBrightness(0.8);
   * 
   * // 设置应用亮度为 50%
   * KioskManager.setAppBrightness(0.5);
   
   */
  setAppBrightness: (value: number) => void;

  /**
   * 获取当前应用亮度设置
   * 
   * @returns Promise<number> - 返回应用亮度值
   *   - 如果返回 -1，表示应用跟随系统亮度
   *   - 如果返回 0-1 之间的值，表示应用自定义的亮度（相对于系统最大亮度）
   * 
   * @example
   * // 示例代码：
   * const appBrightness = await KioskManager.getAppBrightness();
   * if (appBrightness === -1) {
   *   console.log('应用亮度：跟随系统');
   * } else {
   *   console.log('应用亮度：', (appBrightness * 100).toFixed(0) + '%');
   * }
   
   */
  getAppBrightness: () => Promise<number>;

  /**
   * 重置应用亮度为跟随系统
   * 
   * 清除应用的自定义亮度设置，恢复跟随系统亮度。
   * 
   * @example
   * // 示例代码：
   * // 重置应用亮度设置
   * KioskManager.resetAppBrightness();
   * console.log('应用亮度已恢复跟随系统');
   
   */
  resetAppBrightness: () => void;

  /**
   * 设置指定音频流的音量
   * 
   * @param stream - 音频流类型：
   *   - 'music': 媒体音量（音乐、视频等）
   *   - 'ring': 铃声音量
   *   - 'alarm': 闹钟音量
   *   - 'notification': 通知音量
   *   - 'system': 系统音量（系统提示音）
   *   - 'voice_call': 通话音量
   *   - 'dtmf': 拨号音音量
   * @param value - 音量值，范围 0-1，0 为静音，1 为最大音量
   * @returns Promise<boolean> - 返回 true 表示设置成功，false 表示失败
   * 
   * @example
   * // 示例代码：
   * // 设置媒体音量为 50%
   * await KioskManager.setVolume('music', 0.5);
   * 
   * // 设置铃声为最大音量
   * await KioskManager.setVolume('ring', 1.0);
   * 
   * // 静音通知
   * await KioskManager.setVolume('notification', 0);
   
   */
  setVolume: (stream: 'music' | 'ring' | 'alarm' | 'notification' | 'system' | 'voice_call' | 'dtmf', value: number) => Promise<boolean>;

  /**
   * 获取指定音频流的当前音量
   * 
   * @param stream - 音频流类型（同 setVolume）
   * @returns Promise<number> - 返回当前音量值，范围 0-1
   * 
   * @example
   * // 示例代码：
   * // 获取媒体音量
   * const musicVolume = await KioskManager.getVolume('music');
   * console.log('媒体音量:', (musicVolume * 100).toFixed(0) + '%');
   * 
   * // 获取铃声音量
   * const ringVolume = await KioskManager.getVolume('ring');
   
   */
  getVolume: (stream: 'music' | 'ring' | 'alarm' | 'notification' | 'system' | 'voice_call' | 'dtmf') => Promise<number>;

  /**
   * 获取指定音频流的最大音量级别
   * 
   * 不同设备的最大音量级别可能不同，此方法返回设备支持的最大级别。
   * 
   * @param stream - 音频流类型（同 setVolume）
   * @returns Promise<number> - 返回最大音量级别（设备相关，通常为 15 或更大）
   * 
   * @example
   * // 示例代码：
   * const maxVolume = await KioskManager.getMaxVolume('music');
   * console.log('媒体最大音量级别:', maxVolume);
   
   */
  getMaxVolume: (stream: 'music' | 'ring' | 'alarm' | 'notification' | 'system' | 'voice_call' | 'dtmf') => Promise<number>;

  /**
   * 设置全局音量（统一设置多个音频流）
   * 
   * 同时对多个音频流（music、ring、notification、system）设置相同的音量比例。
   * 
   * @param value - 音量值，范围 0-1
   * @returns Promise<boolean> - 返回 true 表示设置成功
   * 
   * @example
   * // 示例代码：
   * // 统一设置所有音频流为 70% 音量
   * await KioskManager.setGlobalVolume(0.7);
   
   */
  setGlobalVolume: (value: number) => Promise<boolean>;

  /**
   * 获取全局音量
   * 
   * 返回多个音频流的平均音量值（0-1）。
   * 
   * @returns Promise<number> - 返回全局音量平均值，范围 0-1
   * 
   * @example
   * // 示例代码：
   * const globalVol = await KioskManager.getGlobalVolume();
   * console.log('全局音量:', (globalVol * 100).toFixed(0) + '%');
   
   */
  getGlobalVolume: () => Promise<number>;

  /**
   * 设置指定音频流的静音状态
   * 
   * @param stream - 音频流类型（同 setVolume）
   * @param muted - true 为静音，false 为取消静音
   * @returns Promise<boolean> - 返回 true 表示设置成功
   * 
   * @example
   * // 示例代码：
   * // 静音媒体
   * await KioskManager.setMute('music', true);
   * 
   * // 取消静音
   * await KioskManager.setMute('music', false);
   
   */
  setMute: (stream: 'music' | 'ring' | 'alarm' | 'notification' | 'system' | 'voice_call' | 'dtmf', muted: boolean) => Promise<boolean>;

  /**
   * 检查指定音频流是否静音
   * 
   * @param stream - 音频流类型（同 setVolume）
   * @returns Promise<boolean> - 返回 true 表示已静音，false 表示未静音
   * 
   * @example
   * // 示例代码：
   * const isMuted = await KioskManager.isMuted('music');
   * console.log('媒体是否静音:', isMuted);
   
   */
  isMuted: (stream: 'music' | 'ring' | 'alarm' | 'notification' | 'system' | 'voice_call' | 'dtmf') => Promise<boolean>;

  /**
   * 设置全局静音状态
   * 
   * 统一设置多个音频流（music、ring、notification、system）的静音状态。
   * 
   * @param muted - true 为全部静音，false 为取消静音
   * @returns Promise<boolean> - 返回 true 表示设置成功
   * 
   * @example
   * // 示例代码：
   * // 全部静音
   * await KioskManager.setGlobalMute(true);
   * 
   * // 取消全部静音
   * await KioskManager.setGlobalMute(false);
   
   */
  setGlobalMute: (muted: boolean) => Promise<boolean>;

  /**
   * 检查全局是否静音
   * 
   * @returns Promise<boolean> - 返回 true 表示全局已静音，false 表示未静音
   * 
   * @example
   * // 示例代码：
   * const isGlobalMuted = await KioskManager.isGlobalMuted();
   * console.log('全局静音状态:', isGlobalMuted);
   
   */
  isGlobalMuted: () => Promise<boolean>;

  // ========== 系统铃声模式与免打扰 ==========

  /**
   * 获取当前铃声模式
   * 
   * @returns Promise<'silent' | 'vibrate' | 'normal'> - 返回当前铃声模式
   *   - 'silent': 静音模式
   *   - 'vibrate': 振动模式
   *   - 'normal': 正常模式
   * 
   * @example
   * // 示例代码：
   * const mode = await KioskManager.getRingerMode();
   * console.log('当前铃声模式:', mode);
   
   */
  getRingerMode: () => Promise<'silent' | 'vibrate' | 'normal'>;

  /**
   * 设置铃声模式
   * 
   * @param mode - 铃声模式：'silent'（静音）、'vibrate'（振动）、'normal'（正常）
   * @returns Promise<boolean> - 返回 true 表示设置成功，false 表示失败（可能需要通知策略访问权限）
   * 
   * @example
   * // 示例代码：
   * // 设置为静音模式
   * await KioskManager.setRingerMode('silent');
   * 
   * // 设置为振动模式
   * await KioskManager.setRingerMode('vibrate');
   * 
   * // 恢复正常模式
   * await KioskManager.setRingerMode('normal');
   
   */
  setRingerMode: (mode: 'silent' | 'vibrate' | 'normal') => Promise<boolean>;

  /**
   * 检查是否有通知策略访问权限
   * 
   * 设置铃声模式和免打扰模式需要此权限（Android 6.0+）。
   * 
   * @returns Promise<boolean> - 返回 true 表示有权限，false 表示没有
   * 
   * @example
   * // 示例代码：
   * const hasAccess = await KioskManager.hasNotificationPolicyAccess();
   * if (!hasAccess) {
   *   await KioskManager.requestNotificationPolicyAccess();
   * }
   
   */
  hasNotificationPolicyAccess: () => Promise<boolean>;

  /**
   * 请求通知策略访问权限
   * 
   * 打开系统设置页面，引导用户授予通知策略访问权限。
   * 
   * @returns Promise<boolean> - 返回 true 表示用户授予了权限，false 表示拒绝
   * 
   * @example
   * // 示例代码：
   * const granted = await KioskManager.requestNotificationPolicyAccess();
   * if (granted) {
   *   // 现在可以设置铃声模式
   *   await KioskManager.setRingerMode('silent');
   * }
   
   */
  requestNotificationPolicyAccess: () => Promise<boolean>;

  // ========== 亮度/音量观察 ==========

  /**
   * 开始观察系统亮度和音量变化
   * 
   * 调用此方法后，系统会开始监听亮度和音量的变化事件。
   * 需要配合相应的监听器使用。
   * 
   * @example
   * // 示例代码：
   * // 开始观察
   * KioskManager.startObservingSystemAv();
   * 
   * // 添加监听器
   * const brightnessListener = (brightness: number) => {
   *   console.log('系统亮度变化:', brightness);
   * };
   * KioskManager.addSystemBrightnessListener(brightnessListener);
   
   */
  startObservingSystemAv: () => void;

  /**
   * 停止观察系统亮度和音量变化
   * 
   * 停止监听系统亮度和音量的变化事件，节省资源。
   * 
   * @example
   * // 示例代码：
   * // 停止观察
   * KioskManager.stopObservingSystemAv();
   
   */
  stopObservingSystemAv: () => void;

  /**
   * 添加系统亮度变化监听器
   * 
   * 当系统亮度发生变化时，会调用回调函数。
   * 注意：需要先调用 startObservingSystemAv() 开始观察。
   * 
   * @param cb - 回调函数，参数为新的亮度值（0-255）
   * 
   * @example
   * // 示例代码：
   * const handleBrightnessChange = (brightness: number) => {
   *   console.log('系统亮度已更改为:', brightness);
   * };
   * 
   * KioskManager.startObservingSystemAv();
   * KioskManager.addSystemBrightnessListener(handleBrightnessChange);
   * 
   * // 组件卸载时记得移除监听器
   * // KioskManager.removeSystemBrightnessListener(handleBrightnessChange);
   
   */
  addSystemBrightnessListener: (cb: (v: number) => void) => void;

  /**
   * 移除系统亮度变化监听器
   * 
   * @param cb - 要移除的回调函数（必须与添加时传入的是同一个函数引用）
   * 
   * @example
   * // 示例代码：
   * const listener = (brightness: number) => { // ... 处理逻辑 };
   * KioskManager.addSystemBrightnessListener(listener);
   * 
   * // 移除监听器
   * KioskManager.removeSystemBrightnessListener(listener);
   
   */
  removeSystemBrightnessListener: (cb: (v: number) => void) => void;

  /**
   * 添加音量变化监听器
   * 
   * 当任何音频流的音量发生变化时，会调用回调函数。
   * 注意：需要先调用 startObservingSystemAv() 开始观察。
   * 
   * @param cb - 回调函数，参数包含：
   *   - stream: 音频流类型（字符串）
   *   - index: 当前音量级别
   *   - max: 最大音量级别
   *   - value: 音量值（0-1）
   * 
   * @example
   * // 示例代码：
   * const handleVolumeChange = (data: {
   *   stream: string;
   *   index: number;
   *   max: number;
   *   value: number;
   * }) => {
   *   console.log('音量变化 - 流: ' + data.stream + ', 值: ' + (data.value * 100).toFixed(0) + '%');
   * };
   * 
   * KioskManager.startObservingSystemAv();
   * KioskManager.addVolumeChangedListener(handleVolumeChange);
   * 
   * // 组件卸载时记得移除
   * // KioskManager.removeVolumeChangedListener(handleVolumeChange);
   
   */
  addVolumeChangedListener: (cb: (d: { stream: string; index: number; max: number; value: number }) => void) => void;

  /**
   * 移除音量变化监听器
   * 
   * @param cb - 要移除的回调函数（必须与添加时传入的是同一个函数引用）
   * 
   * @example
   * // 示例代码：
   * const listener = (data) => { // ... 处理逻辑 };
   * KioskManager.addVolumeChangedListener(listener);
   * 
   * // 移除监听器
   * KioskManager.removeVolumeChangedListener(listener);
   
   */
  removeVolumeChangedListener: (cb: (d: { stream: string; index: number; max: number; value: number }) => void) => void;

  /**
   * 添加全局音量变化监听器
   * 
   * 监听全局音量的平均值变化（多个音频流的平均值）。
   * 注意：需要先调用 startObservingSystemAv() 开始观察。
   * 
   * @param cb - 回调函数，参数为全局音量值（0-1）
   * 
   * @example
   * // 示例代码：
   * const handleGlobalVolumeChange = (volume: number) => {
   *   console.log('全局音量:', (volume * 100).toFixed(0) + '%');
   * };
   * 
   * KioskManager.startObservingSystemAv();
   * KioskManager.addGlobalVolumeChangedListener(handleGlobalVolumeChange);
   
   */
  addGlobalVolumeChangedListener: (cb: (v: number) => void) => void;

  /**
   * 移除全局音量变化监听器
   * 
   * @param cb - 要移除的回调函数（必须与添加时传入的是同一个函数引用）
   */
  removeGlobalVolumeChangedListener: (cb: (v: number) => void) => void;

  /**
   * 添加铃声模式变化监听器
   * 
   * 当系统铃声模式（静音/振动/正常）发生变化时，会调用回调函数。
   * 注意：需要先调用 startObservingSystemAv() 开始观察。
   * 
   * @param cb - 回调函数，参数为新的铃声模式
   * 
   * @example
   * // 示例代码：
   * const handleRingerModeChange = (mode: 'silent' | 'vibrate' | 'normal') => {
   *   console.log('铃声模式已更改为:', mode);
   * };
   * 
   * KioskManager.startObservingSystemAv();
   * KioskManager.addRingerModeChangedListener(handleRingerModeChange);
   */
  
  addRingerModeChangedListener: (cb: (m: 'silent' | 'vibrate' | 'normal') => void) => void;

  /**
   * 移除铃声模式变化监听器
   * 
   * @param cb - 要移除的回调函数（必须与添加时传入的是同一个函数引用）
   */
  removeRingerModeChangedListener: (cb: (m: 'silent' | 'vibrate' | 'normal') => void) => void;
  
  // ========== APK 更新相关方法 ==========

  /**
   * 下载 APK 文件
   * 
   * 从指定 URL 下载 APK 文件到应用的下载目录。
   * 可以通过 addDownloadProgressListener 监听下载进度。
   * 
   * @param url - APK 文件的下载 URL（HTTP/HTTPS）
   * @returns Promise<DownloadResult> - 下载结果，包含：
   *   - filePath: 下载文件的完整路径
   *   - fileName: 文件名
   *   - fileSize: 文件大小（字节）
   * 
   * @example
   * // 示例代码：
   * // 添加下载进度监听
   * const progressListener = (progress: DownloadProgress) => {
   *   console.log('下载进度: ' + progress.percentage.toFixed(1) + '%');
   * };
   * KioskManager.addDownloadProgressListener(progressListener);
   * 
   * // 开始下载
   * try {
   *   const result = await KioskManager.downloadApk('https://example.com/app.apk');
   *   console.log('下载成功:', result.filePath);
   * } catch (error) {
   *   console.error('下载失败:', error);
   * }
   * 
   * // 移除监听器
   * KioskManager.removeDownloadProgressListener(progressListener);
   
   */
  downloadApk: (url: string) => Promise<DownloadResult>;

  /**
   * 安装 APK 文件
   * 
   * 安装指定路径的 APK 文件。会弹出系统安装界面，需要用户确认。
   * 注意：需要先调用 requestInstallPermission() 获取安装权限（Android 8.0+）。
   * 
   * @param filePath - APK 文件的完整路径
   * @returns Promise<boolean> - 返回 true 表示安装请求已发送，false 表示失败
   *   注意：此方法只是发起安装请求，实际安装结果需要用户在系统界面确认
   * 
   * @example
   * // 示例代码：
   * // 检查并请求安装权限
   * const hasPermission = await KioskManager.checkInstallPermission();
   * if (!hasPermission) {
   *   await KioskManager.requestInstallPermission();
   * }
   * 
   * // 安装 APK
   * const success = await KioskManager.installApk('/path/to/app.apk');
   * if (success) {
   *   console.log('安装请求已发送，等待用户确认');
   * }
   
   */
  installApk: (filePath: string) => Promise<boolean>;

  /**
   * 下载并安装 APK（两步操作）
   * 
   * 先下载 APK，下载成功后自动弹出安装界面。
   * 这是一个便捷方法，等同于 downloadApk() + installApk()。
   * 
   * @param url - APK 文件的下载 URL
   * @returns Promise<boolean> - 返回 true 表示下载成功且安装请求已发送，false 表示失败
   * 
   * @example
   * // 示例代码：
   * // 确保有安装权限
   * await KioskManager.requestInstallPermission();
   * 
   * // 下载并安装
   * const success = await KioskManager.downloadAndInstallApk('https://example.com/app.apk');
   * if (success) {
   *   console.log('下载完成，等待用户确认安装');
   * }
   
   */
  downloadAndInstallApk: (url: string) => Promise<boolean>;

  /**
   * 检查是否有安装 APK 的权限
   * 
   * Android 8.0+ 需要用户授予"未知来源应用安装"权限。
   * 
   * @returns Promise<boolean> - 返回 true 表示有权限，false 表示没有
   * 
   * @example
   * // 示例代码：
   * const hasPermission = await KioskManager.checkInstallPermission();
   * if (!hasPermission) {
   *   console.log('需要安装权限');
   *   await KioskManager.requestInstallPermission();
   * }
   
   */
  checkInstallPermission: () => Promise<boolean>;

  /**
   * 请求安装 APK 的权限
   * 
   * 打开系统设置页面，引导用户授予"未知来源应用安装"权限。
   * 
   * @returns Promise<boolean> - 返回 true 表示用户授予了权限，false 表示拒绝
   * 
   * @example
   * // 示例代码：
   * const granted = await KioskManager.requestInstallPermission();
   * if (granted) {
   *   console.log('安装权限已授予');
   *   // 现在可以安装 APK
   * } else {
   *   console.warn('用户拒绝了安装权限');
   * }
   
   */
  requestInstallPermission: () => Promise<boolean>;
  
  // ========== 静默安装相关方法 ==========

  /**
   * 静默安装 APK（需要设备所有者权限）
   * 
   * 在不显示安装界面的情况下安装 APK，需要应用拥有设备所有者权限。
   * 这是企业设备管理场景下的常用功能。
   * 
   * @param filePath - APK 文件的完整路径
   * @returns Promise<boolean> - 返回 true 表示安装成功，false 表示失败
   * 
   * @example
   * // 示例代码：
   * // 确保应用是设备所有者
   * const isOwner = await KioskManager.isDeviceOwner();
   * if (isOwner) {
   *   const success = await KioskManager.silentInstallApk('/path/to/app.apk');
   *   if (success) {
   *     console.log('静默安装成功');
   *   }
   * } else {
   *   console.error('需要设备所有者权限');
   * }
   
   */
  silentInstallApk: (filePath: string) => Promise<boolean>;

  /**
   * 下载并静默安装 APK
   * 
   * 先下载 APK，然后使用设备所有者权限进行静默安装。
   * 
   * @param url - APK 文件的下载 URL
   * @returns Promise<boolean> - 返回 true 表示下载并安装成功，false 表示失败
   * 
   * @example
   * // 示例代码：
   * // 需要设备所有者权限
   * const success = await KioskManager.downloadAndSilentInstallApk('https://example.com/app.apk');
   * if (success) {
   *   console.log('下载并静默安装成功');
   * }
   
   */
  downloadAndSilentInstallApk: (url: string) => Promise<boolean>;

  /**
   * 静默安装并启动 APK
   * 
   * 静默安装 APK 后，自动启动安装的应用。
   * 需要设备所有者权限。
   * 
   * @param filePath - APK 文件的完整路径
   * @returns Promise<boolean> - 返回 true 表示安装并启动成功，false 表示失败
   * 
   * @example
   * // 示例代码：
   * const success = await KioskManager.silentInstallAndLaunchApk('/path/to/app.apk');
   * if (success) {
   *   console.log('应用已安装并启动');
   * }
   
   */
  silentInstallAndLaunchApk: (filePath: string) => Promise<boolean>;

  /**
   * 下载、静默安装并启动 APK
   * 
   * 完成下载、安装和启动的完整流程。
   * 需要设备所有者权限。
   * 
   * @param url - APK 文件的下载 URL
   * @returns Promise<boolean> - 返回 true 表示全部操作成功，false 表示失败
   * 
   * @example
   * // 示例代码：
   * const success = await KioskManager.downloadAndSilentInstallAndLaunchApk('https://example.com/app.apk');
   * if (success) {
   *   console.log('应用已下载、安装并启动');
   * }
   
   */
  downloadAndSilentInstallAndLaunchApk: (url: string) => Promise<boolean>;

  /**
   * 系统级静默安装 APK
   * 
   * 使用系统级的静默安装方法，需要设备所有者权限。
   * 这是最底层的安装方法，适用于某些特殊场景。
   * 
   * @param filePath - APK 文件的完整路径
   * @returns Promise<boolean> - 返回 true 表示安装成功，false 表示失败
   * 
   * @example
   * // 示例代码：
   * // 需要设备所有者权限
   * const success = await KioskManager.systemSilentInstallApk('/path/to/app.apk');
   
   */
  systemSilentInstallApk: (filePath: string) => Promise<boolean>;
  
  // ========== 事件监听器 ==========

  /**
   * 添加下载进度监听器
   * 
   * 监听 APK 下载的进度变化。每当下载进度更新时，会调用回调函数。
   * 
   * @param callback - 回调函数，参数为下载进度信息：
   *   - progress: 进度百分比（0-100）
   *   - bytesRead: 已下载字节数
   *   - totalBytes: 总字节数
   *   - percentage: 进度百分比（0.0-100.0）
   * 
   * @example
   * // 示例代码：
   * const progressListener = (progress: DownloadProgress) => {
   *   console.log('下载进度: ' + progress.percentage.toFixed(1) + '%');
   *   console.log('已下载: ' + (progress.bytesRead / 1024 / 1024).toFixed(2) + ' MB');
   *   console.log('总大小: ' + (progress.totalBytes / 1024 / 1024).toFixed(2) + ' MB');
   * };
   * 
   * KioskManager.addDownloadProgressListener(progressListener);
   * 
   * // 开始下载
   * await KioskManager.downloadApk('https://example.com/app.apk');
   * 
   * // 下载完成后记得移除监听器
   * // KioskManager.removeDownloadProgressListener(progressListener);
   
   */
  addDownloadProgressListener: (callback: (progress: DownloadProgress) => void) => void;

  /**
   * 移除下载进度监听器
   * 
   * @param callback - 要移除的回调函数（必须与添加时传入的是同一个函数引用）
   * 
   * @example
   * // 示例代码：
   * const listener = (progress) => { // ... 处理逻辑 };
   * KioskManager.addDownloadProgressListener(listener);
   * 
   * // 移除监听器
   * KioskManager.removeDownloadProgressListener(listener);
   
   */
  removeDownloadProgressListener: (callback: (progress: DownloadProgress) => void) => void;
  
  // ========== 文件管理方法 ==========

  /**
   * 获取所有已下载的文件列表
   * 
   * 返回应用下载目录中的所有文件信息。
   * 
   * @returns Promise<DownloadedFile[]> - 返回文件列表，每个文件包含：
   *   - fileName: 文件名
   *   - filePath: 文件完整路径
   *   - fileSize: 文件大小（字节）
   *   - lastModified: 最后修改时间（时间戳）
   *   - canRead: 是否可读
   *   - canWrite: 是否可写
   * 
   * @example
   * // 示例代码：
   * const files = await KioskManager.getDownloadedFiles();
   * console.log('找到 ' + files.length + ' 个文件:');
   * files.forEach(file => {
   *   console.log('- ' + file.fileName + ' (' + (file.fileSize / 1024 / 1024).toFixed(2) + ' MB)');
   * });
   
   */
  getDownloadedFiles: () => Promise<DownloadedFile[]>;

  /**
   * 删除指定的已下载文件
   * 
   * @param filePath - 要删除的文件完整路径
   * @returns Promise<boolean> - 返回 true 表示删除成功，false 表示失败
   * 
   * @example
   * // 示例代码：
   * const success = await KioskManager.deleteDownloadedFile('/path/to/file.apk');
   * if (success) {
   *   console.log('文件删除成功');
   * }
   
   */
  deleteDownloadedFile: (filePath: string) => Promise<boolean>;

  /**
   * 清空所有已下载的文件
   * 
   * 删除下载目录中的所有文件。
   * 
   * @returns Promise<number> - 返回成功删除的文件数量
   * 
   * @example
   * // 示例代码：
   * const deletedCount = await KioskManager.clearAllDownloadedFiles();
   * console.log('已删除 ' + deletedCount + ' 个文件');
   
   */
  clearAllDownloadedFiles: () => Promise<number>;
}
