import { useRef, useState } from 'react';
import { Dimensions, Text, TouchableOpacity, View } from 'react-native';
import KioskManager from 'react-native-kiosk-manager';
import { createSafeAlert } from '../utils/safeAlert';
import { useAppStyles } from '../utils/styles';

interface BrightnessVolumeTabProps {
  hasWriteSettings: boolean | null;
  systemBrightness: number | null;
  appBrightness: number | null;
  volumes: Record<string, number>;
  globalVolume: number | null;
  mutedMap: Record<string, boolean>;
  globalMuted: boolean | null;
  ringerMode: 'silent' | 'vibrate' | 'normal' | null;
  hasDndAccess: boolean | null;
  onHasWriteSettingsChange: (has: boolean) => void;
  onSystemBrightnessChange: (brightness: number) => void;
  onAppBrightnessChange: (brightness: number) => void;
  onVolumesChange: (volumes: Record<string, number>) => void;
  onGlobalVolumeChange: (volume: number) => void;
  onMutedMapChange: (mutedMap: Record<string, boolean>) => void;
  onGlobalMutedChange: (muted: boolean) => void;
  onRingerModeChange: (mode: 'silent' | 'vibrate' | 'normal') => void;
  onHasDndAccessChange: (has: boolean) => void;
}

const audioStreams: Array<{
  key: 'music' | 'ring' | 'alarm' | 'notification' | 'system' | 'voice_call' | 'dtmf';
  label: string;
}> = [
  { key: 'music', label: '媒体(music)' },
  { key: 'ring', label: '铃声(ring)' },
  { key: 'alarm', label: '闹钟(alarm)' },
  { key: 'notification', label: '通知(notification)' },
  { key: 'system', label: '系统(system)' },
  { key: 'voice_call', label: '通话(voice_call)' },
  { key: 'dtmf', label: '拨号(dtmf)' },
];

export default function BrightnessVolumeTab(props: BrightnessVolumeTabProps) {
  const {
    hasWriteSettings,
    systemBrightness,
    appBrightness,
    volumes,
    globalVolume,
    mutedMap,
    globalMuted,
    ringerMode,
    onHasWriteSettingsChange,
    onSystemBrightnessChange,
    onAppBrightnessChange,
    onVolumesChange,
    onGlobalVolumeChange,
    onMutedMapChange,
    onGlobalMutedChange,
    onRingerModeChange,
  } = props;

  const { styles, colors } = useAppStyles();
  const { width } = Dimensions.get('window');
  const isTablet = width >= 768;
  const [streamsOpen, setStreamsOpen] = useState(false);
  const isMountedRef = useRef(true);
  const safeAlert = createSafeAlert(isMountedRef);

  const handleRequestWriteSettings = async () => {
    try {
      await KioskManager.requestWriteSettingsPermission();
      const has = await KioskManager.hasWriteSettingsPermission();
      onHasWriteSettingsChange(has);
    } catch (e) {
      safeAlert('Error', '请求系统写入设置权限失败');
    }
  };

  const handleSetSystemBrightness = async (v: number) => {
    try {
      const clamped = Math.max(0, Math.min(255, Math.floor(v)));
      const ok = await KioskManager.setSystemBrightness(clamped);
      if (ok) onSystemBrightnessChange(clamped);
      else safeAlert('Error', '设置系统亮度失败');
    } catch (e) {
      safeAlert('Error', '设置系统亮度失败');
    }
  };

  const handleSetAppBrightness = (v: number) => {
    const clamped = Math.max(0, Math.min(1, v));
    try {
      KioskManager.setAppBrightness(clamped);
      onAppBrightnessChange(clamped);
    } catch (e) {
      safeAlert('Error', '设置应用亮度失败');
    }
  };

  const handleSetVolume = async (
    stream: 'music' | 'ring' | 'alarm' | 'notification' | 'system' | 'voice_call' | 'dtmf',
    v: number
  ) => {
    const clamped = Math.max(0, Math.min(1, v));
    try {
      await KioskManager.setVolume(stream, clamped);
      onVolumesChange({ ...volumes, [stream]: clamped });
    } catch (e) {
      safeAlert('Error', `设置音量失败: ${stream}`);
    }
  };

  const handleToggleMute = async (
    stream: 'music' | 'ring' | 'alarm' | 'notification' | 'system' | 'voice_call' | 'dtmf'
  ) => {
    try {
      const target = !(mutedMap[stream] ?? false);
      await KioskManager.setMute(stream, target);
      onMutedMapChange({ ...mutedMap, [stream]: target });
    } catch (e) {
      safeAlert('Error', `设置静音失败: ${stream}`);
    }
  };

  const handleToggleGlobalMute = async () => {
    try {
      const target = !globalMuted;
      await KioskManager.setGlobalMute(target);
      onGlobalMutedChange(target);
    } catch (e) {
      safeAlert('Error', '设置全局静音失败');
    }
  };

  const handleToggleSystemMute = async () => {
    try {
      const current = (await KioskManager.getRingerMode()) as 'silent' | 'vibrate' | 'normal';
      if (current === 'normal') {
        const granted = await KioskManager.hasNotificationPolicyAccess();
        if (!granted) {
          await KioskManager.requestNotificationPolicyAccess();
          safeAlert('提示', '请在设置中授予"免打扰权限"，返回应用后再次点击');
          return;
        }
        await KioskManager.setRingerMode('silent');
        onRingerModeChange('silent');
      } else {
        await KioskManager.setRingerMode('normal');
        onRingerModeChange('normal');
      }
    } catch (e) {
      safeAlert('Error', '切换系统静音失败');
    }
  };

  return (
    <View style={[styles.section, isTablet && { marginTop: 32, padding: 24 }]}>
      <Text style={styles.sectionTitle}>亮度与音量控制（Android）</Text>

      {/* 系统亮度权限与设置 */}
      <View style={styles.buttonGrid}>
        <TouchableOpacity
          style={[styles.compactButton, styles.infoButton]}
          onPress={async () => {
            try {
              const has = await KioskManager.hasWriteSettingsPermission();
              onHasWriteSettingsChange(has);
              safeAlert('WRITE_SETTINGS', has ? '已授权' : '未授权');
            } catch {
              safeAlert('Error', '检查权限失败');
            }
          }}
        >
          <Text style={[styles.compactButtonText, styles.infoButtonText]}>检查系统写入权限</Text>
        </TouchableOpacity>

        <TouchableOpacity style={[styles.compactButton, styles.warningButton]} onPress={handleRequestWriteSettings}>
          <Text style={[styles.compactButtonText, styles.warningButtonText]}>请求系统写入权限</Text>
        </TouchableOpacity>
        {hasWriteSettings !== null && (
          <View style={[styles.statusItem, { flex: 1 }]}>
            <Text style={styles.statusLabel}>权限状态</Text>
            <Text style={[styles.statusValue, hasWriteSettings ? styles.statusEnabled : styles.statusDisabled]}>
              {hasWriteSettings ? '已授权' : '未授权'}
            </Text>
          </View>
        )}
      </View>

      <View style={styles.statusItem}>
        <Text style={styles.statusLabel}>系统亮度(0-255):</Text>
        <Text style={styles.statusValue}>{systemBrightness ?? '-'}</Text>
      </View>

      <View style={styles.buttonRow}>
        <TouchableOpacity style={[styles.compactButton, styles.secondaryButton]} onPress={() => handleSetSystemBrightness(50)}>
          <Text style={[styles.compactButtonText, styles.secondaryButtonText]}>系统亮度 50</Text>
        </TouchableOpacity>
        <TouchableOpacity style={[styles.compactButton, styles.secondaryButton]} onPress={() => handleSetSystemBrightness(128)}>
          <Text style={[styles.compactButtonText, styles.secondaryButtonText]}>系统亮度 128</Text>
        </TouchableOpacity>
        <TouchableOpacity style={[styles.compactButton, styles.secondaryButton]} onPress={() => handleSetSystemBrightness(255)}>
          <Text style={[styles.compactButtonText, styles.secondaryButtonText]}>系统亮度 255</Text>
        </TouchableOpacity>
      </View>

      {/* 应用亮度 */}
      <View style={styles.statusItem}>
        <Text style={styles.statusLabel}>应用亮度(0-1或-1):</Text>
        <Text style={styles.statusValue}>{appBrightness ?? '-'}</Text>
      </View>
      <View style={styles.buttonRow}>
        <TouchableOpacity style={[styles.compactButton, styles.primaryButton]} onPress={() => handleSetAppBrightness(0)}>
          <Text style={[styles.compactButtonText, styles.primaryButtonText]}>应用亮度 0</Text>
        </TouchableOpacity>
        <TouchableOpacity style={[styles.compactButton, styles.primaryButton]} onPress={() => handleSetAppBrightness(0.5)}>
          <Text style={[styles.compactButtonText, styles.primaryButtonText]}>应用亮度 0.5</Text>
        </TouchableOpacity>
        <TouchableOpacity style={[styles.compactButton, styles.primaryButton]} onPress={() => handleSetAppBrightness(1)}>
          <Text style={[styles.compactButtonText, styles.primaryButtonText]}>应用亮度 1</Text>
        </TouchableOpacity>
        <TouchableOpacity
          style={[styles.compactButton, styles.warningButton]}
          onPress={() => {
            try {
              KioskManager.resetAppBrightness();
              onAppBrightnessChange(-1);
            } catch {}
          }}
        >
          <Text style={[styles.compactButtonText, styles.warningButtonText]}>恢复系统控制</Text>
        </TouchableOpacity>
      </View>

      {/* 全局音量便捷操作 */}
      <View style={styles.statusItem}>
        <Text style={styles.statusLabel}>全局音量(0-1):</Text>
        <Text style={styles.statusValue}>{globalVolume?.toFixed(2) ?? '-'}</Text>
      </View>

      <View style={styles.buttonRow}>
        <TouchableOpacity
          style={[styles.compactButton, styles.successButton]}
          onPress={async () => {
            await KioskManager.setGlobalVolume(0);
            onGlobalVolumeChange(0);
          }}
        >
          <Text style={[styles.compactButtonText, styles.successButtonText]}>全局音量 0</Text>
        </TouchableOpacity>
        <TouchableOpacity
          style={[styles.compactButton, styles.successButton]}
          onPress={async () => {
            await KioskManager.setGlobalVolume(0.5);
            const v = await KioskManager.getGlobalVolume();
            onGlobalVolumeChange(v);
          }}
        >
          <Text style={[styles.compactButtonText, styles.successButtonText]}>全局音量 0.5</Text>
        </TouchableOpacity>
        <TouchableOpacity
          style={[styles.compactButton, styles.successButton]}
          onPress={async () => {
            await KioskManager.setGlobalVolume(1);
            onGlobalVolumeChange(1);
          }}
        >
          <Text style={[styles.compactButtonText, styles.successButtonText]}>全局音量 1</Text>
        </TouchableOpacity>
        <TouchableOpacity style={[styles.compactButton, styles.warningButton]} onPress={handleToggleGlobalMute}>
          <Text style={[styles.compactButtonText, styles.warningButtonText]}>{globalMuted ? '取消全局静音' : '全局静音'}</Text>
        </TouchableOpacity>
        <TouchableOpacity style={[styles.compactButton, styles.secondaryButton]} onPress={handleToggleSystemMute}>
          <Text style={[styles.compactButtonText, styles.secondaryButtonText]}>
            {ringerMode === 'silent' ? '系统静音：开（点我关闭）' : '系统静音：关（点我开启）'}
          </Text>
        </TouchableOpacity>
      </View>

      {/* 各音频流音量（Accordion） */}
      <TouchableOpacity style={styles.buttonRow} onPress={() => setStreamsOpen(!streamsOpen)}>
        <View style={[styles.statusItem, { flex: 1 }]}>
          <Text style={styles.statusLabel}>各音频流音量</Text>
        </View>
        <View>
          <Text style={[styles.compactButtonText, { color: colors.text }]}>{streamsOpen ? '收起 ▲' : '展开 ▼'}</Text>
        </View>
      </TouchableOpacity>

      {streamsOpen && (
        <>
          {audioStreams.map((s) => (
            <View key={s.key} style={styles.buttonRow}>
              <TouchableOpacity
                style={[styles.compactButton, styles.infoButton]}
                onPress={() => handleSetVolume(s.key, Math.max(0, (volumes[s.key] ?? 0) - 0.1))}
              >
                <Text style={[styles.compactButtonText, styles.infoButtonText]}>{s.label} -</Text>
              </TouchableOpacity>
              <View style={[styles.statusItem, { flex: 1 }]}>
                <Text style={styles.statusLabel}>{s.label} 音量:</Text>
                <Text style={styles.statusValue}>{(volumes[s.key] ?? 0).toFixed(2)}</Text>
                <Text style={styles.statusValue}>{mutedMap[s.key] ? '（已静音）' : ''}</Text>
              </View>
              <TouchableOpacity
                style={[styles.compactButton, styles.infoButton]}
                onPress={() => handleSetVolume(s.key, Math.min(1, (volumes[s.key] ?? 0) + 0.1))}
              >
                <Text style={[styles.compactButtonText, styles.infoButtonText]}>{s.label} +</Text>
              </TouchableOpacity>
              <TouchableOpacity style={[styles.compactButton, styles.warningButton]} onPress={() => handleToggleMute(s.key)}>
                <Text style={[styles.compactButtonText, styles.warningButtonText]}>{mutedMap[s.key] ? '取消静音' : '静音'}</Text>
              </TouchableOpacity>
            </View>
          ))}
        </>
      )}
    </View>
  );
}

