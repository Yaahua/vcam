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

            val totalSize = videoFiles.sumOf { it.length() }
            val totalDur = videoItems.sumOf { it.durationMs }

            _uiState.update {
                it.copy(
                    videos = videoItems,
                    audios = audioItems,
                    selectedVideoName = selectedVideo?.let { n ->
                        if (n.startsWith("/")) File(n).name else n
                    },
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
            val currentName = if (current != null && current.startsWith("/")) File(current).name else current

            if (currentName == name) {
                configManager.setString(ConfigManager.KEY_SELECTED_VIDEO, "")
                _uiState.update { it.copy(selectedVideoName = null) }
            } else {
                val targetPath = File(videoDir, name).absolutePath
                configManager.setString(ConfigManager.KEY_SELECTED_VIDEO, targetPath)
                _uiState.update { it.copy(selectedVideoName = name) }
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