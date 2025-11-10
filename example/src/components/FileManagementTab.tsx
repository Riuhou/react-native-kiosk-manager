import { Dimensions, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import type { DownloadedFile } from 'react-native-kiosk-manager';
import { useAppStyles } from '../utils/styles';

interface FileManagementTabProps {
  downloadedFiles: DownloadedFile[];
  isLoadingFiles: boolean;
  onGetDownloadedFiles: () => void;
  onClearAllFiles: () => void;
  onInstallApk: (filePath: string, fileName: string) => void;
  onSilentInstallApk: (filePath: string, fileName: string) => void;
  onSilentInstallAndLaunchApk: (filePath: string, fileName: string) => void;
  onSystemSilentInstallApk: (filePath: string, fileName: string) => void;
  onDeleteFile: (filePath: string, fileName: string) => void;
}

export default function FileManagementTab(props: FileManagementTabProps) {
  const {
    downloadedFiles,
    isLoadingFiles,
    onGetDownloadedFiles,
    onClearAllFiles,
    onInstallApk,
    onSilentInstallApk,
    onSilentInstallAndLaunchApk,
    onSystemSilentInstallApk,
    onDeleteFile,
  } = props;

  const { isDarkMode, styles, colors } = useAppStyles();
  const { width } = Dimensions.get('window');
  const isTablet = width >= 768;

  return (
    <View style={[styles.section, isTablet && { marginTop: 32, padding: 24 }]}>
      <Text style={styles.sectionTitle}>下载文件管理</Text>

      <View style={[styles.buttonGrid, isTablet && { gap: 16 }]}>
        <TouchableOpacity
          style={[styles.compactButton, styles.infoButton, isLoadingFiles && styles.disabledButton]}
          onPress={onGetDownloadedFiles}
          disabled={isLoadingFiles}
        >
          <Text style={[styles.compactButtonText, styles.infoButtonText]}>
            {isLoadingFiles ? '加载中...' : '获取文件列表'}
          </Text>
        </TouchableOpacity>

        <TouchableOpacity
          style={[styles.compactButton, styles.dangerButton, downloadedFiles.length === 0 && styles.disabledButton]}
          onPress={onClearAllFiles}
          disabled={downloadedFiles.length === 0}
        >
          <Text style={[styles.compactButtonText, styles.dangerButtonText]}>清空所有文件</Text>
        </TouchableOpacity>
      </View>

      {/* 文件列表 */}
      {downloadedFiles.length > 0 && (
        <View style={fileStyles.fileListContainer}>
          <Text style={[fileStyles.fileListTitle, { color: colors.text }]}>
            已下载文件 ({downloadedFiles.length})
          </Text>

          {downloadedFiles.map((file) => (
            <View key={file.filePath} style={[fileStyles.fileItem, isDarkMode && fileStyles.darkFileItem]}>
              <View style={fileStyles.fileInfo}>
                <Text style={[fileStyles.fileName, { color: colors.text }]} numberOfLines={1}>
                  {file.fileName}
                </Text>
                <Text style={[fileStyles.fileDetails, { color: colors.text }]}>
                  {(file.fileSize / 1024 / 1024).toFixed(2)} MB •{' '}
                  {new Date(file.lastModified).toLocaleDateString()}
                </Text>
                <Text style={[fileStyles.filePath, { color: colors.text }]} numberOfLines={1}>
                  {file.filePath}
                </Text>
              </View>

              <View style={fileStyles.fileActions}>
                <TouchableOpacity
                  style={[fileStyles.actionButton, { backgroundColor: '#28a745' }]}
                  onPress={() => onInstallApk(file.filePath, file.fileName)}
                >
                  <Text style={fileStyles.actionButtonText}>安装</Text>
                </TouchableOpacity>
                <TouchableOpacity
                  style={[fileStyles.actionButton, { backgroundColor: '#dc3545' }]}
                  onPress={() => onSilentInstallApk(file.filePath, file.fileName)}
                >
                  <Text style={fileStyles.actionButtonText}>静默安装</Text>
                </TouchableOpacity>
                <TouchableOpacity
                  style={[fileStyles.actionButton, { backgroundColor: '#28a745' }]}
                  onPress={() => onSilentInstallAndLaunchApk(file.filePath, file.fileName)}
                >
                  <Text style={fileStyles.actionButtonText}>静默安装并启动</Text>
                </TouchableOpacity>
                <TouchableOpacity
                  style={[fileStyles.actionButton, { backgroundColor: '#6f42c1' }]}
                  onPress={() => onSystemSilentInstallApk(file.filePath, file.fileName)}
                >
                  <Text style={fileStyles.actionButtonText}>系统级静默安装</Text>
                </TouchableOpacity>
                <TouchableOpacity
                  style={[fileStyles.actionButton, { backgroundColor: '#dc3545' }]}
                  onPress={() => onDeleteFile(file.filePath, file.fileName)}
                >
                  <Text style={fileStyles.actionButtonText}>删除</Text>
                </TouchableOpacity>
              </View>
            </View>
          ))}
        </View>
      )}

      {downloadedFiles.length === 0 && !isLoadingFiles && (
        <View style={fileStyles.emptyState}>
          <Text style={[fileStyles.emptyStateText, { color: colors.text }]}>暂无下载文件</Text>
        </View>
      )}
    </View>
  );
}

const fileStyles = StyleSheet.create({
  fileListContainer: {
    marginTop: 16,
  },
  fileListTitle: {
    fontSize: 16,
    fontWeight: '600',
    marginBottom: 12,
  },
  fileItem: {
    flexDirection: 'row',
    alignItems: 'center',
    padding: 12,
    marginBottom: 8,
    backgroundColor: 'rgba(255,255,255,0.7)',
    borderRadius: 8,
    borderWidth: 1,
    borderColor: 'rgba(0,0,0,0.1)',
  },
  darkFileItem: {
    backgroundColor: 'rgba(255,255,255,0.1)',
    borderColor: 'rgba(255,255,255,0.2)',
  },
  fileInfo: {
    flex: 1,
    marginRight: 12,
  },
  fileName: {
    fontSize: 14,
    fontWeight: '600',
    marginBottom: 4,
  },
  fileDetails: {
    fontSize: 12,
    opacity: 0.7,
    marginBottom: 2,
  },
  filePath: {
    fontSize: 10,
    opacity: 0.5,
    fontFamily: 'monospace',
  },
  fileActions: {
    flexDirection: 'row',
    alignItems: 'center',
    flexWrap: 'wrap',
    gap: 8,
  },
  actionButton: {
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 6,
  },
  actionButtonText: {
    color: '#fff',
    fontSize: 12,
    fontWeight: '600',
  },
  emptyState: {
    padding: 20,
    alignItems: 'center',
  },
  emptyStateText: {
    fontSize: 14,
    opacity: 0.6,
    fontStyle: 'italic',
  },
});

