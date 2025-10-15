import { Platform } from 'react-native';
import KioskManagerAndroid from './index.android';
import KioskManagerIOS from './index.ios';
import type { KioskManagerType, DownloadResult, DownloadProgress, DownloadedFile } from './KioskManager.type';

const KioskManager = Platform.OS === 'android' ? KioskManagerAndroid : KioskManagerIOS;

export type { KioskManagerType, DownloadResult, DownloadProgress, DownloadedFile };
export default KioskManager;
