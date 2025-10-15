import { Platform } from 'react-native';
import type { KioskManagerType, DownloadResult, DownloadProgress, DownloadedFile } from './KioskManager.type';

let KioskManager: KioskManagerType;
if (Platform.OS === 'android') {
  KioskManager = require('./index.android').default;
} else {
  KioskManager = require('./index.ios').default;
}

export type { KioskManagerType, DownloadResult, DownloadProgress, DownloadedFile };
export default KioskManager;
