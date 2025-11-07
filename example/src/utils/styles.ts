import { StyleSheet, useColorScheme } from 'react-native';

export const useAppStyles = () => {
  const colorScheme = useColorScheme();
  const isDarkMode = colorScheme === 'dark';

  return {
    isDarkMode,
    styles: StyleSheet.create({
      container: {
        flex: 1,
        backgroundColor: isDarkMode ? '#121212' : '#f8f9fa',
      },
      scrollView: {
        flex: 1,
      },
      scrollContent: {
        padding: 20,
        paddingBottom: 40,
      },
      header: {
        alignItems: 'center',
        marginBottom: 30,
      },
      title: {
        fontSize: 28,
        fontWeight: 'bold',
        textAlign: 'center',
        marginBottom: 8,
        color: isDarkMode ? '#ffffff' : '#212529',
      },
      subtitle: {
        fontSize: 16,
        textAlign: 'center',
        opacity: 0.7,
        color: isDarkMode ? '#cccccc' : '#666666',
      },
      section: {
        marginTop: 24,
        padding: 16,
        borderRadius: 12,
        backgroundColor: isDarkMode ? 'rgba(255,255,255,0.05)' : 'rgba(0,0,0,0.05)',
      },
      sectionTitle: {
        fontSize: 20,
        fontWeight: 'bold',
        marginBottom: 16,
        textAlign: 'center',
        color: isDarkMode ? '#ffffff' : '#212529',
      },
      buttonGrid: {
        flexDirection: 'row',
        flexWrap: 'wrap',
        justifyContent: 'space-between',
        gap: 12,
        marginBottom: 16,
      },
      buttonRow: {
        flexDirection: 'row',
        justifyContent: 'space-between',
        marginBottom: 12,
        gap: 12,
      },
      actionButton: {
        paddingVertical: 16,
        paddingHorizontal: 24,
        borderRadius: 12,
        alignItems: 'center',
        justifyContent: 'center',
        minHeight: 56,
        shadowColor: '#000',
        shadowOffset: { width: 0, height: 2 },
        shadowOpacity: 0.1,
        shadowRadius: 4,
        elevation: 3,
      },
      compactButton: {
        paddingVertical: 12,
        paddingHorizontal: 16,
        borderRadius: 8,
        alignItems: 'center',
        justifyContent: 'center',
        minHeight: 44,
        flex: 1,
        shadowColor: '#000',
        shadowOffset: { width: 0, height: 1 },
        shadowOpacity: 0.08,
        shadowRadius: 2,
        elevation: 2,
      },
      buttonText: {
        fontSize: 16,
        fontWeight: '600',
        textAlign: 'center',
      },
      compactButtonText: {
        fontSize: 14,
        fontWeight: '600',
        textAlign: 'center',
      },
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
      disabledButton: {
        opacity: 0.5,
      },
      statusSection: {
        marginTop: 30,
        padding: 20,
        borderRadius: 12,
        backgroundColor: isDarkMode ? 'rgba(255,255,255,0.05)' : 'rgba(0,0,0,0.05)',
      },
      statusItem: {
        flexDirection: 'row',
        justifyContent: 'space-between',
        alignItems: 'center',
        paddingVertical: 8,
        paddingHorizontal: 12,
        marginVertical: 2,
        borderRadius: 6,
        backgroundColor: isDarkMode ? 'rgba(255,255,255,0.1)' : 'rgba(255,255,255,0.7)',
      },
      statusLabel: {
        fontSize: 14,
        fontWeight: '500',
        flex: 1,
        color: isDarkMode ? '#ffffff' : '#212529',
      },
      statusValue: {
        fontSize: 14,
        fontWeight: '600',
        paddingHorizontal: 8,
        paddingVertical: 3,
        borderRadius: 4,
        color: isDarkMode ? '#ffffff' : '#212529',
      },
      statusEnabled: {
        backgroundColor: '#d4edda',
        color: '#155724',
      },
      statusDisabled: {
        backgroundColor: '#f8d7da',
        color: '#721c24',
      },
      inputContainer: {
        marginBottom: 16,
      },
      inputLabel: {
        fontSize: 16,
        fontWeight: '600',
        marginBottom: 8,
        color: isDarkMode ? '#ffffff' : '#212529',
      },
      textInput: {
        borderWidth: 1,
        borderColor: isDarkMode ? '#555' : '#ddd',
        borderRadius: 8,
        padding: 12,
        fontSize: 16,
        backgroundColor: isDarkMode ? '#333' : '#fff',
        color: isDarkMode ? '#fff' : '#212529',
        minHeight: 80,
        textAlignVertical: 'top',
      },
      progressContainer: {
        marginTop: 16,
        marginBottom: 16,
        padding: 16,
        backgroundColor: isDarkMode ? 'rgba(255,255,255,0.05)' : 'rgba(0,0,0,0.05)',
        borderRadius: 8,
      },
      progressText: {
        fontSize: 14,
        fontWeight: '600',
        marginBottom: 8,
        textAlign: 'center',
        color: isDarkMode ? '#ffffff' : '#212529',
      },
      progressBarContainer: {
        height: 6,
        backgroundColor: isDarkMode ? 'rgba(255,255,255,0.1)' : 'rgba(0,0,0,0.1)',
        borderRadius: 3,
        overflow: 'hidden',
        marginBottom: 6,
      },
      progressBar: {
        height: '100%',
        backgroundColor: '#007AFF',
        borderRadius: 3,
      },
      progressDetails: {
        fontSize: 12,
        textAlign: 'center',
        opacity: 0.7,
        color: isDarkMode ? '#cccccc' : '#666666',
      },
    }),
    colors: {
      text: isDarkMode ? '#ffffff' : '#212529',
      subtext: isDarkMode ? '#cccccc' : '#666666',
      background: isDarkMode ? '#121212' : '#f8f9fa',
      card: isDarkMode ? 'rgba(255,255,255,0.05)' : 'rgba(0,0,0,0.05)',
    },
  };
};

