import { View, Text, TouchableOpacity, Dimensions } from 'react-native';
import { useAppStyles } from '../utils/styles';

interface KioskControlTabProps {
  bootAutoStart: boolean | null;
  isDeviceOwner: boolean | null;
  isLockTaskPackageSetup: boolean | null;
  onStartKiosk: () => void;
  onStopKiosk: () => void;
  onCheckBootAutoStart: () => void;
  onRequestDeviceAdmin: () => void;
  onCheckDeviceOwner: () => void;
  onSetupLockTaskPackage: () => void;
  onClearDeviceOwner: () => void;
  onToggleBootAutoStart: () => void;
}

export default function KioskControlTab({
  bootAutoStart,
  isDeviceOwner,
  isLockTaskPackageSetup,
  onStartKiosk,
  onStopKiosk,
  onCheckBootAutoStart,
  onRequestDeviceAdmin,
  onCheckDeviceOwner,
  onSetupLockTaskPackage,
  onClearDeviceOwner,
  onToggleBootAutoStart,
}: KioskControlTabProps) {
  const { styles } = useAppStyles();
  const { width } = Dimensions.get('window');
  const isTablet = width >= 768;

  return (
    <>
      <View style={[styles.buttonGrid, isTablet && { gap: 16 }]}>
        <TouchableOpacity
          style={[
            styles.actionButton,
            styles.primaryButton,
            isTablet && { flex: 1, minWidth: '45%', maxWidth: '48%' },
          ]}
          onPress={onStartKiosk}
        >
          <Text style={[styles.buttonText, styles.primaryButtonText]}>
            启动Kiosk模式
          </Text>
        </TouchableOpacity>

        <TouchableOpacity
          style={[
            styles.actionButton,
            styles.secondaryButton,
            isTablet && { flex: 1, minWidth: '45%', maxWidth: '48%' },
          ]}
          onPress={onStopKiosk}
        >
          <Text style={[styles.buttonText, styles.secondaryButtonText]}>
            停止Kiosk模式
          </Text>
        </TouchableOpacity>

        <TouchableOpacity
          style={[
            styles.actionButton,
            styles.infoButton,
            isTablet && { flex: 1, minWidth: '45%', maxWidth: '48%' },
          ]}
          onPress={onCheckBootAutoStart}
        >
          <Text style={[styles.buttonText, styles.infoButtonText]}>
            检查开机自启
          </Text>
        </TouchableOpacity>

        <TouchableOpacity
          style={[
            styles.actionButton,
            styles.warningButton,
            isTablet && { flex: 1, minWidth: '45%', maxWidth: '48%' },
          ]}
          onPress={onRequestDeviceAdmin}
        >
          <Text style={[styles.buttonText, styles.warningButtonText]}>
            请求设备管理员
          </Text>
        </TouchableOpacity>

        <TouchableOpacity
          style={[
            styles.actionButton,
            styles.infoButton,
            isTablet && { flex: 1, minWidth: '45%', maxWidth: '48%' },
          ]}
          onPress={onCheckDeviceOwner}
        >
          <Text style={[styles.buttonText, styles.infoButtonText]}>
            检查设备所有者
          </Text>
        </TouchableOpacity>

        <TouchableOpacity
          style={[
            styles.actionButton,
            styles.successButton,
            isTablet && { flex: 1, minWidth: '45%', maxWidth: '48%' },
          ]}
          onPress={onSetupLockTaskPackage}
        >
          <Text style={[styles.buttonText, styles.successButtonText]}>
            设置锁定任务包
          </Text>
        </TouchableOpacity>

        <TouchableOpacity
          style={[
            styles.actionButton,
            styles.dangerButton,
            isTablet && { flex: 1, minWidth: '45%', maxWidth: '48%' },
          ]}
          onPress={onClearDeviceOwner}
        >
          <Text style={[styles.buttonText, styles.dangerButtonText]}>
            清除设备所有者
          </Text>
        </TouchableOpacity>

        <TouchableOpacity
          style={[
            styles.actionButton,
            bootAutoStart ? styles.successButton : styles.warningButton,
            isTablet && { flex: 1, minWidth: '45%', maxWidth: '48%' },
          ]}
          onPress={onToggleBootAutoStart}
        >
          <Text
            style={[
              styles.buttonText,
              bootAutoStart ? styles.successButtonText : styles.warningButtonText,
            ]}
          >
            {bootAutoStart === null
              ? '切换开机自启'
              : bootAutoStart
              ? '禁用开机自启'
              : '开启开机自启'}
          </Text>
        </TouchableOpacity>
      </View>

      <View style={[styles.statusSection, isTablet && { marginTop: 40, padding: 30 }]}>
        <Text style={[styles.statusLabel, { fontSize: 20, fontWeight: 'bold', marginBottom: 16, textAlign: 'center' }]}>
          状态信息
        </Text>

        {bootAutoStart !== null && (
          <View style={styles.statusItem}>
            <Text style={styles.statusLabel}>开机自启:</Text>
            <Text
              style={[
                styles.statusValue,
                bootAutoStart ? styles.statusEnabled : styles.statusDisabled,
              ]}
            >
              {bootAutoStart ? '已启用' : '已禁用'}
            </Text>
          </View>
        )}

        {isDeviceOwner !== null && (
          <View style={styles.statusItem}>
            <Text style={styles.statusLabel}>设备所有者:</Text>
            <Text
              style={[
                styles.statusValue,
                isDeviceOwner ? styles.statusEnabled : styles.statusDisabled,
              ]}
            >
              {isDeviceOwner ? '已激活' : '未激活'}
            </Text>
          </View>
        )}

        {isLockTaskPackageSetup !== null && (
          <View style={styles.statusItem}>
            <Text style={styles.statusLabel}>锁定任务包:</Text>
            <Text
              style={[
                styles.statusValue,
                isLockTaskPackageSetup ? styles.statusEnabled : styles.statusDisabled,
              ]}
            >
              {isLockTaskPackageSetup ? '已设置' : '未设置'}
            </Text>
          </View>
        )}
      </View>
    </>
  );
}

