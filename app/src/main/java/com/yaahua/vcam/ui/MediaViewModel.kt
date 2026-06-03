package com.yaahua.vcam.ui

import android.app.Application
import android.media.MediaMetadataRetriever
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yaahua.vcam.ConfigManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

data class VideoItem(
    val name: String,
    val file: File,
    val size: Long,
    val durationMs: Long
)

data class AudioItem(
    val name: String,
    val file: File,
    val size: Long,
    val durationMs: Long
)

data class MediaUiState(
    val videos: List<VideoItem> = emptyList(),
    val audios: List<AudioItem> = emptyList(),
    val selectedVideoName: String? = null,
    val selectedAudioName: String? = null,
    val isLoading: Boolean = false,
    val totalVideoSizeMb: Double = 0.0,
    val totalVideoDurationSec: Long = 0
)

class MediaViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(MediaUiState())
    val uiState: StateFlow<MediaUiState> = _uiState.asStateFlow()

    private val videoDir = File("/storage/emulated/0/DCIM/Camera1/")
    private val configManager = ConfigManager().apply { setContext(application) }

    init {
        if (!videoDir.exists()) videoDir.mkdirs()
        loadMedia()
    }

    fun loadMedia() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true) }

            val videoExts = setOf("mp4", "mov", "avi", "mkv")
            val videoFiles = videoDir.listFiles { f ->
                videoExts.contains(f.extension.lowercase())
            }?.sorted() ?: emptyList()

            val videoItems = videoFiles.map { f ->
                var dur = 0L
                try {
                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(f.absolutePath)
                    dur = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                    retriever.release()
                } catch (_: Exception) {}
                VideoItem(f.name, f, f.length(), dur)
            }

            val audioExts = setOf("mp3", "wav", "aac", "m4a", "ogg", "flac")
            val audioFiles = videoDir.listFiles { f ->
                audioExts.contains(f.extension.lowercase())
            }?.sorted() ?: emptyList()

            val audioItems = audioFiles.map { f ->
                var dur = 0L
                try {
                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(f.absolutePath)
                    dur = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                    retriever.release()
                } catch (_: Exception) {}
                AudioItem(f.name, f, f.length(), dur)
            }

            configManager.reload()
            val selectedVideo = configManager.getString(ConfigManager.KEY_SELECTED_VIDEO, null)
            val selectedAudio = configManager.getString(ConfigManager.KEY_SELECTED_AUDIO, null)

            // 将选中的外部视频也加入列表（如果它是绝对路径且不在 Camera1 目录内）
            var extVideoItem: VideoItem? = null
            if (selectedVideo != null && selectedVideo.startsWith("/")) {
                val extFile = File(selectedVideo)
                if (extFile.exists() && !extFile.isDirectory()
                    && extFile.parent != videoDir.absolutePath) {
                    var dur = 0L
                    try {
                        val retriever = MediaMetadataRetriever()
                        retriever.setDataSource(extFile.absolutePath)
                        dur = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                        retriever.release()
                    } catch (_: Exception) {}
                    extVideoItem = VideoItem(extFile.name, extFile, extFile.length(), dur)
                }
            }

            val allVideos = if (extVideoItem != null) {
                // 外部视频放在列表最前面
                listOf(extVideoItem) + videoItems
            } else {
                videoItems
            }

            val totalSize = videoFiles.sumOf { it.length() } + (extVideoItem?.size ?: 0L)
            val totalDur = videoItems.sumOf { it.durationMs } + (extVideoItem?.durationMs ?: 0L)

            _uiState.update {
                it.copy(
                    videos = allVideos,
                    audios = audioItems,
                    selectedVideoName = selectedVideo,
                    selectedAudioName = selectedAudio,
                    isLoading = false,
                    totalVideoSizeMb = totalSize / 1048576.0,
                    totalVideoDurationSec = totalDur / 1000
                )
            }
        }
    }

    fun selectVideo(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            configManager.reload()
            val current = configManager.getString(ConfigManager.KEY_SELECTED_VIDEO, null)
            val targetPath = File(videoDir, name).absolutePath
            // 匹配逻辑：支持绝对路径与相对路径的相等比较
            val isSame = if (current != null) {
                if (current.startsWith("/")) {
                    current == targetPath || File(current).name == name
                } else {
                    current == name || current == targetPath
                }
            } else false

            if (isSame) {
                configManager.setString(ConfigManager.KEY_SELECTED_VIDEO, "")
                _uiState.update { it.copy(selectedVideoName = null) }
            } else {
                configManager.setString(ConfigManager.KEY_SELECTED_VIDEO, targetPath)
                _uiState.update { it.copy(selectedVideoName = targetPath) }
            }
        }
    }

    /** 从任意路径选择视频（不拷贝），存储绝对路径。 */
    fun selectVideoByAbsolutePath(absolutePath: String) {
        viewModelScope.launch(Dispatchers.IO) {
            configManager.reload()
            val current = configManager.getString(ConfigManager.KEY_SELECTED_VIDEO, null)
            // 匹配：当前若为绝对路径或相对文件名，都尝试匹配
            val isSame = if (current != null) {
                if (current.startsWith("/")) {
                    File(current).absolutePath == File(absolutePath).absolutePath
                } else {
                    File(absolutePath).name == current
                }
            } else false

            if (isSame) {
                configManager.setString(ConfigManager.KEY_SELECTED_VIDEO, "")
                _uiState.update { it.copy(selectedVideoName = null) }
            } else {
                configManager.setString(ConfigManager.KEY_SELECTED_VIDEO, absolutePath)
                _uiState.update { it.copy(selectedVideoName = absolutePath) }
                // 重新加载列表以显示这个外部视频
                loadMedia()
            }
        }
    }

    fun selectAudio(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            configManager.reload()
            val current = configManager.getString(ConfigManager.KEY_SELECTED_AUDIO, null)
            if (current == name) {
                configManager.setString(ConfigManager.KEY_SELECTED_AUDIO, "")
                _uiState.update { it.copy(selectedAudioName = null) }
            } else {
                configManager.setString(ConfigManager.KEY_SELECTED_AUDIO, name)
                _uiState.update { it.copy(selectedAudioName = name) }
            }
        }
    }

    fun deleteFile(file: File) {
        viewModelScope.launch(Dispatchers.IO) {
            file.delete()
            loadMedia()
        }
    }

    fun importFiles(uris: List<android.net.Uri>, isAudio: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            for (uri in uris) {
                try {
                    var name = "import_${System.currentTimeMillis()}"
                    // get display name
                    app.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            if (idx >= 0) name = cursor.getString(idx) ?: name
                        }
                    }
                    if (!name.contains(".")) name += if (isAudio) ".mp3" else ".mp4"
                    val dest = File(videoDir, name)
                    app.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(dest).use { output -> input.copyTo(output) }
                    }
                    dest.setReadable(true, false)
                } catch (_: Exception) {}
            }
            loadMedia()
        }
    }
}
