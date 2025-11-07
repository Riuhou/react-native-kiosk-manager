import { Alert } from 'react-native';

/**
 * 安全的 Alert 函数，避免在组件卸载后显示 Alert
 */
export const createSafeAlert = (isMountedRef: React.MutableRefObject<boolean>) => {
  return (title: string, message?: string, buttons?: any[]) => {
    if (isMountedRef.current) {
      try {
        Alert.alert(title, message, buttons);
      } catch (error) {
        console.warn('Failed to show alert:', error);
      }
    }
  };
};

