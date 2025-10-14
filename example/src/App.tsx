import { useState } from 'react';
import {
  Text,
  View,
  StyleSheet,
  Alert,
  useColorScheme,
  ScrollView,
  Dimensions,
  TouchableOpacity,
  StatusBar,
} from 'react-native';
import KioskManager from 'react-native-kiosk-manager';

export default function App() {
  const [bootAutoStart, setBootAutoStart] = useState<boolean | null>(null);
  const [isDeviceOwner, setIsDeviceOwner] = useState<boolean | null>(null);
  const [isLockTaskPackageSetup, setIsLockTaskPackageSetup] = useState<
    boolean | null
  >(null);
  const colorScheme = useColorScheme();
  const isDarkMode = colorScheme === 'dark';

  // 获取屏幕尺寸用于响应式设计
  const { width, height } = Dimensions.get('window');
  const isTablet = width >= 768;
  const isLandscape = width > height;

  const handleStartKiosk = () => {
    KioskManager.startKiosk();
    Alert.alert('Success', 'Kiosk mode started');
  };

  const handleStopKiosk = () => {
    KioskManager.stopKiosk();
    Alert.alert('Success', 'Kiosk mode stopped');
  };

  const handleCheckBootAutoStart = async () => {
    try {
      const isEnabled = await KioskManager.isBootAutoStartEnabled();
      setBootAutoStart(isEnabled);
    } catch (error) {
      Alert.alert('Error', 'Failed to check boot auto start status');
    }
  };

  const handleRequestDeviceAdmin = async () => {
    try {
      await KioskManager.requestDeviceAdmin();
      Alert.alert('Success', 'Device admin requested');
    } catch (error) {
      Alert.alert('Error', 'Failed to request device admin');
    }
  };

  const handleSetupLockTaskPackage = async () => {
    try {
      await KioskManager.setupLockTaskPackage();
      Alert.alert('Success', 'Lock task package setup completed');
      setIsLockTaskPackageSetup(true);
    } catch (error) {
      Alert.alert(
        'Error',
        'Failed to setup lock task package. Make sure the app is device owner.'
      );
      setIsLockTaskPackageSetup(false);
    }
  };

  const handleDisableBootAutoStart = async () => {
    try {
      await KioskManager.enableBootAutoStart(false);
      Alert.alert('Success', 'Boot auto start disabled');
      setBootAutoStart(false);
    } catch (error) {
      Alert.alert('Error', 'Failed to disable boot auto start');
    }
  };

  const handleCheckDeviceOwner = async () => {
    try {
      const isOwner = await KioskManager.isDeviceOwner();
      setIsDeviceOwner(isOwner);
    } catch (error) {
      Alert.alert('Error', 'Failed to check device owner status');
    }
  };

  const handleClearDeviceOwner = async () => {
    try {
      Alert.alert(
        'Warning',
        'Are you sure you want to clear device owner? This will remove all device admin privileges.',
        [
          {
            text: 'Cancel',
            style: 'cancel',
          },
          {
            text: 'Clear',
            style: 'destructive',
            onPress: async () => {
              try {
                await KioskManager.clearDeviceOwner();
                Alert.alert('Success', 'Device owner cleared successfully');
                setIsDeviceOwner(false);
                setIsLockTaskPackageSetup(false);
              } catch (error) {
                Alert.alert('Error', 'Failed to clear device owner');
              }
            },
          },
        ]
      );
    } catch (error) {
      Alert.alert('Error', 'Failed to clear device owner');
    }
  };

  return (
    <View
      style={[
        styles.container,
        isDarkMode ? styles.darkContainer : styles.lightContainer,
      ]}
    >
      <StatusBar barStyle={isDarkMode ? 'light-content' : 'dark-content'} />
      <ScrollView
        style={styles.scrollView}
        contentContainerStyle={[
          styles.scrollContent,
          isTablet && styles.tabletContent,
          isLandscape && styles.landscapeContent,
        ]}
        showsVerticalScrollIndicator={false}
      >
        <View style={[styles.header, isTablet && styles.tabletHeader]}>
          <Text
            style={[
              styles.title,
              isDarkMode ? styles.darkText : styles.lightText,
            ]}
          >
            Kiosk Manager Example
          </Text>
          <Text
            style={[
              styles.subtitle,
              isDarkMode ? styles.darkSubtext : styles.lightSubtext,
            ]}
          >
            {isTablet ? '平板模式' : '手机模式'} •{' '}
            {isLandscape ? '横屏' : '竖屏'}
          </Text>
        </View>

        <View style={[styles.buttonGrid, isTablet && styles.tabletButtonGrid]}>
          <TouchableOpacity
            style={[
              styles.actionButton,
              styles.primaryButton,
              isDarkMode && styles.darkButton,
              isTablet && styles.tabletActionButton,
            ]}
            onPress={handleStartKiosk}
          >
            <Text style={[styles.buttonText, styles.primaryButtonText]}>
              启动Kiosk模式
            </Text>
          </TouchableOpacity>

          <TouchableOpacity
            style={[
              styles.actionButton,
              styles.secondaryButton,
              isDarkMode && styles.darkButton,
              isTablet && styles.tabletActionButton,
            ]}
            onPress={handleStopKiosk}
          >
            <Text style={[styles.buttonText, styles.secondaryButtonText]}>
              停止Kiosk模式
            </Text>
          </TouchableOpacity>

          <TouchableOpacity
            style={[
              styles.actionButton,
              styles.infoButton,
              isDarkMode && styles.darkButton,
              isTablet && styles.tabletActionButton,
            ]}
            onPress={handleCheckBootAutoStart}
          >
            <Text style={[styles.buttonText, styles.infoButtonText]}>
              检查开机自启
            </Text>
          </TouchableOpacity>

          <TouchableOpacity
            style={[
              styles.actionButton,
              styles.warningButton,
              isDarkMode && styles.darkButton,
              isTablet && styles.tabletActionButton,
            ]}
            onPress={handleRequestDeviceAdmin}
          >
            <Text style={[styles.buttonText, styles.warningButtonText]}>
              请求设备管理员
            </Text>
          </TouchableOpacity>

          <TouchableOpacity
            style={[
              styles.actionButton,
              styles.infoButton,
              isDarkMode && styles.darkButton,
              isTablet && styles.tabletActionButton,
            ]}
            onPress={handleCheckDeviceOwner}
          >
            <Text style={[styles.buttonText, styles.infoButtonText]}>
              检查设备所有者
            </Text>
          </TouchableOpacity>

          <TouchableOpacity
            style={[
              styles.actionButton,
              styles.successButton,
              isDarkMode && styles.darkButton,
              isTablet && styles.tabletActionButton,
            ]}
            onPress={handleSetupLockTaskPackage}
          >
            <Text style={[styles.buttonText, styles.successButtonText]}>
              设置锁定任务包
            </Text>
          </TouchableOpacity>

          <TouchableOpacity
            style={[
              styles.actionButton,
              styles.dangerButton,
              isDarkMode && styles.darkButton,
              isTablet && styles.tabletActionButton,
            ]}
            onPress={handleClearDeviceOwner}
          >
            <Text style={[styles.buttonText, styles.dangerButtonText]}>
              清除设备所有者
            </Text>
          </TouchableOpacity>

          <TouchableOpacity
            style={[
              styles.actionButton,
              styles.warningButton,
              isDarkMode && styles.darkButton,
              isTablet && styles.tabletActionButton,
            ]}
            onPress={handleDisableBootAutoStart}
          >
            <Text style={[styles.buttonText, styles.warningButtonText]}>
              禁用开机自启
            </Text>
          </TouchableOpacity>
        </View>

        <View
          style={[styles.statusSection, isTablet && styles.tabletStatusSection]}
        >
          <Text
            style={[
              styles.statusTitle,
              isDarkMode ? styles.darkText : styles.lightText,
            ]}
          >
            状态信息
          </Text>

          {bootAutoStart !== null && (
            <View
              style={[styles.statusItem, isDarkMode && styles.darkStatusItem]}
            >
              <Text
                style={[
                  styles.statusLabel,
                  isDarkMode ? styles.darkText : styles.lightText,
                ]}
              >
                开机自启:
              </Text>
              <Text
                style={[
                  styles.statusValue,
                  bootAutoStart ? styles.statusEnabled : styles.statusDisabled,
                  isDarkMode && styles.darkText,
                ]}
              >
                {bootAutoStart ? '已启用' : '已禁用'}
              </Text>
            </View>
          )}

          {isDeviceOwner !== null && (
            <View
              style={[styles.statusItem, isDarkMode && styles.darkStatusItem]}
            >
              <Text
                style={[
                  styles.statusLabel,
                  isDarkMode ? styles.darkText : styles.lightText,
                ]}
              >
                设备所有者:
              </Text>
              <Text
                style={[
                  styles.statusValue,
                  isDeviceOwner ? styles.statusEnabled : styles.statusDisabled,
                  isDarkMode && styles.darkText,
                ]}
              >
                {isDeviceOwner ? '已激活' : '未激活'}
              </Text>
            </View>
          )}

          {isLockTaskPackageSetup !== null && (
            <View
              style={[styles.statusItem, isDarkMode && styles.darkStatusItem]}
            >
              <Text
                style={[
                  styles.statusLabel,
                  isDarkMode ? styles.darkText : styles.lightText,
                ]}
              >
                锁定任务包:
              </Text>
              <Text
                style={[
                  styles.statusValue,
                  isLockTaskPackageSetup
                    ? styles.statusEnabled
                    : styles.statusDisabled,
                  isDarkMode && styles.darkText,
                ]}
              >
                {isLockTaskPackageSetup ? '已设置' : '未设置'}
              </Text>
            </View>
          )}
        </View>
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  lightContainer: {
    backgroundColor: '#f8f9fa',
  },
  darkContainer: {
    backgroundColor: '#121212',
  },
  scrollView: {
    flex: 1,
  },
  scrollContent: {
    padding: 20,
    paddingBottom: 40,
  },
  tabletContent: {
    paddingHorizontal: 40,
    paddingVertical: 30,
  },
  landscapeContent: {
    paddingHorizontal: 30,
  },
  header: {
    alignItems: 'center',
    marginBottom: 30,
  },
  tabletHeader: {
    marginBottom: 40,
  },
  title: {
    fontSize: 28,
    fontWeight: 'bold',
    textAlign: 'center',
    marginBottom: 8,
  },
  subtitle: {
    fontSize: 16,
    textAlign: 'center',
    opacity: 0.7,
  },
  lightSubtext: {
    color: '#666666',
  },
  darkSubtext: {
    color: '#cccccc',
  },
  lightText: {
    color: '#212529',
  },
  darkText: {
    color: '#ffffff',
  },
  buttonGrid: {
    gap: 16,
  },
  tabletButtonGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    justifyContent: 'space-between',
    gap: 20,
  },
  actionButton: {
    paddingVertical: 16,
    paddingHorizontal: 24,
    borderRadius: 12,
    alignItems: 'center',
    justifyContent: 'center',
    minHeight: 56,
    shadowColor: '#000',
    shadowOffset: {
      width: 0,
      height: 2,
    },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 3,
  },
  tabletActionButton: {
    flex: 1,
    minWidth: '45%',
    maxWidth: '48%',
  },
  buttonText: {
    fontSize: 16,
    fontWeight: '600',
    textAlign: 'center',
  },
  // 按钮颜色变体
  primaryButton: {
    backgroundColor: '#007AFF',
  },
  primaryButtonText: {
    color: '#ffffff',
  },
  secondaryButton: {
    backgroundColor: '#6C757D',
  },
  secondaryButtonText: {
    color: '#ffffff',
  },
  successButton: {
    backgroundColor: '#28A745',
  },
  successButtonText: {
    color: '#ffffff',
  },
  warningButton: {
    backgroundColor: '#FFC107',
  },
  warningButtonText: {
    color: '#212529',
  },
  dangerButton: {
    backgroundColor: '#DC3545',
  },
  dangerButtonText: {
    color: '#ffffff',
  },
  infoButton: {
    backgroundColor: '#17A2B8',
  },
  infoButtonText: {
    color: '#ffffff',
  },
  darkButton: {
    shadowOpacity: 0.3,
    shadowRadius: 6,
    elevation: 5,
  },
  statusSection: {
    marginTop: 30,
    padding: 20,
    borderRadius: 12,
    backgroundColor: 'rgba(0,0,0,0.05)',
  },
  tabletStatusSection: {
    marginTop: 40,
    padding: 30,
  },
  statusTitle: {
    fontSize: 20,
    fontWeight: 'bold',
    marginBottom: 16,
    textAlign: 'center',
  },
  statusItem: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: 12,
    paddingHorizontal: 16,
    marginVertical: 4,
    borderRadius: 8,
    backgroundColor: 'rgba(255,255,255,0.7)',
  },
  darkStatusItem: {
    backgroundColor: 'rgba(255,255,255,0.1)',
  },
  statusLabel: {
    fontSize: 16,
    fontWeight: '500',
    flex: 1,
  },
  statusValue: {
    fontSize: 16,
    fontWeight: '600',
    paddingHorizontal: 12,
    paddingVertical: 4,
    borderRadius: 6,
  },
  statusEnabled: {
    backgroundColor: '#d4edda',
    color: '#155724',
  },
  statusDisabled: {
    backgroundColor: '#f8d7da',
    color: '#721c24',
  },
});
