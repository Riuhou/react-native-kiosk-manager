import { useState } from 'react';
import {
  Text,
  View,
  StyleSheet,
  Button,
  Alert,
  useColorScheme,
} from 'react-native';
import KioskManager from 'react-native-kiosk-manager';

export default function App() {
  const [bootAutoStart, setBootAutoStart] = useState<boolean | null>(null);
  const [isDeviceOwner, setIsDeviceOwner] = useState<boolean | null>(null);
  const colorScheme = useColorScheme();
  const isDarkMode = colorScheme === 'dark';

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
      <Text
        style={[styles.title, isDarkMode ? styles.darkText : styles.lightText]}
      >
        Kiosk Manager Example
      </Text>

      <Button title="Start Kiosk Mode" onPress={handleStartKiosk} />
      <View style={styles.spacer} />

      <Button title="Stop Kiosk Mode" onPress={handleStopKiosk} />
      <View style={styles.spacer} />

      <Button
        title="Check Boot Auto Start"
        onPress={handleCheckBootAutoStart}
      />
      <View style={styles.spacer} />

      <Button title="Request Device Admin" onPress={handleRequestDeviceAdmin} />
      <View style={styles.spacer} />

      <Button
        title="Check Device Owner Status"
        onPress={handleCheckDeviceOwner}
      />
      <View style={styles.spacer} />

      <Button
        title="Clear Device Owner"
        onPress={handleClearDeviceOwner}
        color="#ff6b6b"
      />
      <View style={styles.spacer} />

      <Button
        title="Disable Boot Auto Start"
        onPress={handleDisableBootAutoStart}
      />
      <View style={styles.spacer} />

      {bootAutoStart !== null && (
        <Text
          style={[
            styles.statusText,
            isDarkMode ? styles.darkText : styles.lightText,
          ]}
        >
          Boot Auto Start: {bootAutoStart ? 'Enabled' : 'Disabled'}
        </Text>
      )}

      {isDeviceOwner !== null && (
        <Text
          style={[
            styles.statusText,
            isDarkMode ? styles.darkText : styles.lightText,
          ]}
        >
          Device Owner: {isDeviceOwner ? 'Active' : 'Inactive'}
        </Text>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: 20,
  },
  lightContainer: {
    backgroundColor: '#ffffff',
  },
  darkContainer: {
    backgroundColor: '#1a1a1a',
  },
  title: {
    fontSize: 20,
    fontWeight: 'bold',
    marginBottom: 30,
  },
  lightText: {
    color: '#000000',
  },
  darkText: {
    color: '#ffffff',
  },
  statusText: {
    fontSize: 16,
    marginTop: 20,
    fontWeight: '500',
  },
  spacer: {
    height: 15,
  },
});
