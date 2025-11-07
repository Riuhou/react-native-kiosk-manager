import { View, Text, TouchableOpacity, StyleSheet } from 'react-native';

export type TabKey = 'kiosk' | 'update' | 'files' | 'av' | 'power';

interface TabBarProps {
  activeTab: TabKey;
  onTabChange: (tab: TabKey) => void;
  isDarkMode: boolean;
}

const tabs: Array<{ key: TabKey; label: string }> = [
  { key: 'kiosk', label: '基础控制' },
  { key: 'update', label: 'APK 更新' },
  { key: 'files', label: '下载管理' },
  { key: 'av', label: '亮度与音量' },
  { key: 'power', label: '定时开关机' },
];

export default function TabBar({ activeTab, onTabChange, isDarkMode }: TabBarProps) {
  return (
    <View style={[styles.tabBar, isDarkMode ? styles.darkTabBar : styles.lightTabBar]}>
      {tabs.map((tab) => (
        <TouchableOpacity
          key={tab.key}
          style={[styles.tabButton, activeTab === tab.key && styles.activeTabButton]}
          onPress={() => onTabChange(tab.key)}
        >
          <Text
            style={[
              styles.tabButtonText,
              activeTab === tab.key
                ? styles.activeTabButtonText
                : isDarkMode
                ? styles.darkText
                : styles.lightText,
            ]}
          >
            {tab.label}
          </Text>
        </TouchableOpacity>
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  tabBar: {
    flexDirection: 'row',
    borderRadius: 10,
    padding: 6,
    marginBottom: 16,
    gap: 8,
  },
  lightTabBar: {
    backgroundColor: 'rgba(0,0,0,0.06)',
  },
  darkTabBar: {
    backgroundColor: 'rgba(255,255,255,0.08)',
  },
  tabButton: {
    flex: 1,
    paddingVertical: 10,
    borderRadius: 8,
    alignItems: 'center',
    justifyContent: 'center',
  },
  activeTabButton: {
    backgroundColor: '#007AFF',
  },
  tabButtonText: {
    fontSize: 14,
    fontWeight: '600',
  },
  activeTabButtonText: {
    color: '#ffffff',
  },
  lightText: {
    color: '#212529',
  },
  darkText: {
    color: '#ffffff',
  },
});

