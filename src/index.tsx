import { Platform } from 'react-native';
import type { KioskManagerType } from './KioskManager.type';

let KioskManager: KioskManagerType;
if (Platform.OS === 'android') {
  KioskManager = require('./index.android').default;
} else {
  KioskManager = require('./index.ios').default;
}

export default KioskManager;
