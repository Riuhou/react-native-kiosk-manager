import { View, Text, TouchableOpacity, TextInput, Dimensions } from 'react-native';
import type { ScheduledPowerSettings } from 'react-native-kiosk-manager';
import { useAppStyles } from '../utils/styles';

interface PowerScheduleTabProps {
  scheduledShutdown: ScheduledPowerSettings | null;
  scheduledBoot: ScheduledPowerSettings | null;
  shutdownHour: string;
  shutdownMinute: string;
  shutdownRepeat: boolean;
  bootHour: string;
  bootMinute: string;
  bootRepeat: boolean;
  onShutdownHourChange: (hour: string) => void;
  onShutdownMinuteChange: (minute: string) => void;
  onShutdownRepeatChange: (repeat: boolean) => void;
  onBootHourChange: (hour: string) => void;
  onBootMinuteChange: (minute: string) => void;
  onBootRepeatChange: (repeat: boolean) => void;
  onSetScheduledShutdown: () => void;
  onCancelScheduledShutdown: () => void;
  onPerformShutdown: () => void;
  onSetScheduledBoot: () => void;
  onCancelScheduledBoot: () => void;
}

export default function PowerScheduleTab({
  scheduledShutdown,
  scheduledBoot,
  shutdownHour,
  shutdownMinute,
  shutdownRepeat,
  bootHour,
  bootMinute,
  bootRepeat,
  onShutdownHourChange,
  onShutdownMinuteChange,
  onShutdownRepeatChange,
  onBootHourChange,
  onBootMinuteChange,
  onBootRepeatChange,
  onSetScheduledShutdown,
  onCancelScheduledShutdown,
  onPerformShutdown,
  onSetScheduledBoot,
  onCancelScheduledBoot,
}: PowerScheduleTabProps) {
  const { isDarkMode, styles, colors } = useAppStyles();
  const { width } = Dimensions.get('window');
  const isTablet = width >= 768;

  return (
    <View style={[styles.section, isTablet && { marginTop: 32, padding: 24 }]}>
      <Text style={[styles.sectionTitle, { fontSize: 20, fontWeight: 'bold', marginBottom: 16, textAlign: 'center' }]}>
        定时开关机
      </Text>

      {/* 定时关机设置 */}
      <View style={{ marginBottom: 24 }}>
        <Text style={[styles.sectionTitle, { fontSize: 18, marginBottom: 12, textAlign: 'left' }]}>
          定时关机
        </Text>

        {scheduledShutdown && (
          <View style={[styles.statusItem, { marginBottom: 16 }]}>
            <Text style={styles.statusLabel}>当前设置:</Text>
            <Text style={[styles.statusValue, styles.statusEnabled]}>
              {String(scheduledShutdown.hour).padStart(2, '0')}:
              {String(scheduledShutdown.minute).padStart(2, '0')}
              {scheduledShutdown.repeat ? ' (每天重复)' : ' (单次)'}
            </Text>
          </View>
        )}

        <View style={styles.inputContainer}>
          <Text style={styles.inputLabel}>关机时间:</Text>
          <View style={styles.buttonRow}>
            <View style={{ flex: 1, marginRight: 8 }}>
              <Text style={[styles.inputLabel, { fontSize: 12, marginBottom: 4, color: colors.subtext }]}>
                小时 (0-23)
              </Text>
              <TextInput
                style={[styles.textInput, { minHeight: 44, textAlign: 'center' }]}
                value={shutdownHour}
                onChangeText={onShutdownHourChange}
                placeholder="22"
                placeholderTextColor={isDarkMode ? '#666' : '#999'}
                keyboardType="numeric"
              />
            </View>
            <View style={{ flex: 1, marginLeft: 8 }}>
              <Text style={[styles.inputLabel, { fontSize: 12, marginBottom: 4, color: colors.subtext }]}>
                分钟 (0-59)
              </Text>
              <TextInput
                style={[styles.textInput, { minHeight: 44, textAlign: 'center' }]}
                value={shutdownMinute}
                onChangeText={onShutdownMinuteChange}
                placeholder="0"
                placeholderTextColor={isDarkMode ? '#666' : '#999'}
                keyboardType="numeric"
              />
            </View>
          </View>
        </View>

        <View style={styles.buttonRow}>
          <TouchableOpacity
            style={[
              styles.compactButton,
              shutdownRepeat ? styles.successButton : styles.secondaryButton,
            ]}
            onPress={() => onShutdownRepeatChange(!shutdownRepeat)}
          >
            <Text
              style={[
                styles.compactButtonText,
                shutdownRepeat ? styles.successButtonText : styles.secondaryButtonText,
              ]}
            >
              {shutdownRepeat ? '每天重复' : '单次执行'}
            </Text>
          </TouchableOpacity>
        </View>

        <View style={[styles.buttonGrid, isTablet && { gap: 16 }]}>
          <TouchableOpacity
            style={[styles.compactButton, styles.primaryButton]}
            onPress={onSetScheduledShutdown}
          >
            <Text style={[styles.compactButtonText, styles.primaryButtonText]}>
              设置定时关机
            </Text>
          </TouchableOpacity>

          <TouchableOpacity
            style={[
              styles.compactButton,
              styles.warningButton,
              !scheduledShutdown && styles.disabledButton,
            ]}
            onPress={onCancelScheduledShutdown}
            disabled={!scheduledShutdown}
          >
            <Text style={[styles.compactButtonText, styles.warningButtonText]}>
              取消定时关机
            </Text>
          </TouchableOpacity>

          <TouchableOpacity
            style={[styles.compactButton, styles.dangerButton]}
            onPress={onPerformShutdown}
          >
            <Text style={[styles.compactButtonText, styles.dangerButtonText]}>
              立即关机
            </Text>
          </TouchableOpacity>
        </View>
      </View>

      {/* 分隔线 */}
      <View style={{ height: 1, backgroundColor: isDarkMode ? '#333' : '#e0e0e0', marginVertical: 20 }} />

      {/* 定时开机设置 */}
      <View>
        <Text style={[styles.sectionTitle, { fontSize: 18, marginBottom: 12, textAlign: 'left' }]}>
          定时开机
        </Text>

        <Text style={[styles.inputLabel, { fontSize: 12, marginBottom: 12, color: colors.subtext }]}>
          注意：Android 系统本身不支持定时开机，此功能依赖于设备硬件支持。某些厂商的 ROM 可能支持此功能。
        </Text>

        {scheduledBoot && (
          <View style={[styles.statusItem, { marginBottom: 16 }]}>
            <Text style={styles.statusLabel}>当前设置:</Text>
            <Text style={[styles.statusValue, styles.statusEnabled]}>
              {String(scheduledBoot.hour).padStart(2, '0')}:
              {String(scheduledBoot.minute).padStart(2, '0')}
              {scheduledBoot.repeat ? ' (每天重复)' : ' (单次)'}
            </Text>
          </View>
        )}

        <View style={styles.inputContainer}>
          <Text style={styles.inputLabel}>开机时间:</Text>
          <View style={styles.buttonRow}>
            <View style={{ flex: 1, marginRight: 8 }}>
              <Text style={[styles.inputLabel, { fontSize: 12, marginBottom: 4, color: colors.subtext }]}>
                小时 (0-23)
              </Text>
              <TextInput
                style={[styles.textInput, { minHeight: 44, textAlign: 'center' }]}
                value={bootHour}
                onChangeText={onBootHourChange}
                placeholder="8"
                placeholderTextColor={isDarkMode ? '#666' : '#999'}
                keyboardType="numeric"
              />
            </View>
            <View style={{ flex: 1, marginLeft: 8 }}>
              <Text style={[styles.inputLabel, { fontSize: 12, marginBottom: 4, color: colors.subtext }]}>
                分钟 (0-59)
              </Text>
              <TextInput
                style={[styles.textInput, { minHeight: 44, textAlign: 'center' }]}
                value={bootMinute}
                onChangeText={onBootMinuteChange}
                placeholder="0"
                placeholderTextColor={isDarkMode ? '#666' : '#999'}
                keyboardType="numeric"
              />
            </View>
          </View>
        </View>

        <View style={styles.buttonRow}>
          <TouchableOpacity
            style={[
              styles.compactButton,
              bootRepeat ? styles.successButton : styles.secondaryButton,
            ]}
            onPress={() => onBootRepeatChange(!bootRepeat)}
          >
            <Text
              style={[
                styles.compactButtonText,
                bootRepeat ? styles.successButtonText : styles.secondaryButtonText,
              ]}
            >
              {bootRepeat ? '每天重复' : '单次执行'}
            </Text>
          </TouchableOpacity>
        </View>

        <View style={[styles.buttonGrid, isTablet && { gap: 16 }]}>
          <TouchableOpacity
            style={[styles.compactButton, styles.primaryButton]}
            onPress={onSetScheduledBoot}
          >
            <Text style={[styles.compactButtonText, styles.primaryButtonText]}>
              设置定时开机
            </Text>
          </TouchableOpacity>

          <TouchableOpacity
            style={[
              styles.compactButton,
              styles.warningButton,
              !scheduledBoot && styles.disabledButton,
            ]}
            onPress={onCancelScheduledBoot}
            disabled={!scheduledBoot}
          >
            <Text style={[styles.compactButtonText, styles.warningButtonText]}>
              取消定时开机
            </Text>
          </TouchableOpacity>
        </View>
      </View>
    </View>
  );
}

