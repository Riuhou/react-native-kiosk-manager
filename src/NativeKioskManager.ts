import type { TurboModule } from 'react-native';
import { TurboModuleRegistry } from 'react-native';

export interface Spec extends TurboModule {
  startKiosk(): void;
  stopKiosk(): void;
  enableBootAutoStart(enabled: boolean): void;
  isBootAutoStartEnabled(): Promise<boolean>;
  requestDeviceAdmin(): Promise<boolean>;
  setupLockTaskPackage(): Promise<boolean>;
  clearDeviceOwner(): Promise<boolean>;
  isDeviceOwner(): Promise<boolean>;
}

export default TurboModuleRegistry.getEnforcing<Spec>('KioskManager');
