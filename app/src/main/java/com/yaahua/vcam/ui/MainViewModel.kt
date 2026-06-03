package com.yaahua.vcam.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yaahua.vcam.ConfigManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MainUiState(
    val isModuleDisabled: Boolean = false,
    val playVideoSound: Boolean = false,
    val forcePrivateDir: Boolean = false,
    val disableToast: Boolean = false,
    val enableRandomPlay: Boolean = false,
    val enableMicHook: Boolean = false,
    val micHookMode: String = "mute",
    val enablePhotoFake: Boolean = false,
    val notificationControlEnabled: Boolean = false,
    val overlayControlEnabled: Boolean = false,
    val hasPermission: Boolean = false,
    val isXposedActive: Boolean = true,
    val mediaSourceType: String = ConfigManager.MEDIA_SOURCE_LOCAL,
    val streamUrl: String = "",
    val streamAutoReconnect: Boolean = true,
    val streamLocalFallback: Boolean = true,
    val streamTransportHint: String = "auto",
    val streamTimeoutMs: Long = 8000L
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val configManager = ConfigManager()
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        loadConfig()
    }

    fun loadConfig() {
        viewModelScope.launch(Dispatchers.IO) {
            configManager.reload()
            _uiState.update { currentState ->
                currentState.copy(
                    isModuleDisabled = configManager.getBoolean(ConfigManager.KEY_DISABLE_MODULE, false),
                    playVideoSound = configManager.getBoolean(ConfigManager.KEY_PLAY_VIDEO_SOUND, false),
                    forcePrivateDir = configManager.getBoolean(ConfigManager.KEY_FORCE_PRIVATE_DIR, false),
                    disableToast = configManager.getBoolean(ConfigManager.KEY_DISABLE_TOAST, false),
                    enableRandomPlay = configManager.getBoolean(ConfigManager.KEY_ENABLE_RANDOM_PLAY, false),
                    enableMicHook = configManager.getBoolean(ConfigManager.KEY_ENABLE_MIC_HOOK, false),
                    micHookMode = configManager.getString(ConfigManager.KEY_MIC_HOOK_MODE, ConfigManager.MIC_MODE_MUTE),
                    enablePhotoFake = configManager.getBoolean(ConfigManager.KEY_ENABLE_PHOTO_FAKE, false),
                    notificationControlEnabled = configManager.getBoolean(ConfigManager.KEY_NOTIFICATION_CONTROL_ENABLED, false),
                    overlayControlEnabled = configManager.getBoolean(ConfigManager.KEY_OVERLAY_CONTROL_ENABLED, false),
                    mediaSourceType = configManager.getString(ConfigManager.KEY_MEDIA_SOURCE_TYPE, ConfigManager.MEDIA_SOURCE_LOCAL),
                    streamUrl = configManager.getString(ConfigManager.KEY_STREAM_URL, ""),
                    streamAutoReconnect = configManager.getBoolean(ConfigManager.KEY_STREAM_AUTO_RECONNECT, true),
                    streamLocalFallback = configManager.getBoolean(ConfigManager.KEY_STREAM_LOCAL_FALLBACK, true),
                    streamTransportHint = configManager.getString(ConfigManager.KEY_STREAM_TRANSPORT_HINT, "auto"),
                    streamTimeoutMs = configManager.getLong(ConfigManager.KEY_STREAM_TIMEOUT_MS, 8000L)
                )
            }
        }
    }

    fun updatePermissionStatus(hasPermission: Boolean) {
        _uiState.update { it.copy(hasPermission = hasPermission) }
    }

    // ------ Setters ------

    fun setModuleDisabled(disabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            configManager.setBoolean(ConfigManager.KEY_DISABLE_MODULE, disabled)
            _uiState.update { it.copy(isModuleDisabled = disabled) }
        }
    }

    fun setPlayVideoSound(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            configManager.setBoolean(ConfigManager.KEY_PLAY_VIDEO_SOUND, enabled)
            _uiState.update { it.copy(playVideoSound = enabled) }
        }
    }

    fun setForcePrivateDir(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            configManager.setBoolean(ConfigManager.KEY_FORCE_PRIVATE_DIR, enabled)
            _uiState.update { it.copy(forcePrivateDir = enabled) }
        }
    }

    fun setDisableToast(disabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            configManager.setBoolean(ConfigManager.KEY_DISABLE_TOAST, disabled)
            _uiState.update { it.copy(disableToast = disabled) }
        }
    }

    fun setEnableRandomPlay(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            configManager.setBoolean(ConfigManager.KEY_ENABLE_RANDOM_PLAY, enabled)
            _uiState.update { it.copy(enableRandomPlay = enabled) }
        }
    }

    fun setEnableMicHook(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            configManager.setBoolean(ConfigManager.KEY_ENABLE_MIC_HOOK, enabled)
            _uiState.update { it.copy(enableMicHook = enabled) }
        }
    }

    fun setMicHookMode(mode: String) {
        viewModelScope.launch(Dispatchers.IO) {
            configManager.setString(ConfigManager.KEY_MIC_HOOK_MODE, mode)
            _uiState.update { it.copy(micHookMode = mode) }
        }
    }

    fun setEnablePhotoFake(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            configManager.setBoolean(ConfigManager.KEY_ENABLE_PHOTO_FAKE, enabled)
            _uiState.update { it.copy(enablePhotoFake = enabled) }
        }
    }

    fun setNotificationControlEnabled(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            configManager.setBoolean(ConfigManager.KEY_NOTIFICATION_CONTROL_ENABLED, enabled)
            _uiState.update { it.copy(notificationControlEnabled = enabled) }
        }
    }

    fun setOverlayControlEnabled(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            configManager.setBoolean(ConfigManager.KEY_OVERLAY_CONTROL_ENABLED, enabled)
            _uiState.update { it.copy(overlayControlEnabled = enabled) }
        }
    }

    fun setMediaSourceType(type: String) {
        viewModelScope.launch(Dispatchers.IO) {
            configManager.setString(ConfigManager.KEY_MEDIA_SOURCE_TYPE, type)
            _uiState.update { it.copy(mediaSourceType = type) }
        }
    }

    fun setStreamUrl(url: String) {
        viewModelScope.launch(Dispatchers.IO) {
            configManager.setString(ConfigManager.KEY_STREAM_URL, url)
            _uiState.update { it.copy(streamUrl = url) }
        }
    }

    fun setStreamAutoReconnect(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            configManager.setBoolean(ConfigManager.KEY_STREAM_AUTO_RECONNECT, enabled)
            _uiState.update { it.copy(streamAutoReconnect = enabled) }
        }
    }

    fun setStreamLocalFallback(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            configManager.setBoolean(ConfigManager.KEY_STREAM_LOCAL_FALLBACK, enabled)
            _uiState.update { it.copy(streamLocalFallback = enabled) }
        }
    }

    fun setStreamTransportHint(hint: String) {
        viewModelScope.launch(Dispatchers.IO) {
            configManager.setString(ConfigManager.KEY_STREAM_TRANSPORT_HINT, hint)
            _uiState.update { it.copy(streamTransportHint = hint) }
        }
    }

    fun setStreamTimeoutMs(timeout: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            configManager.setLong(ConfigManager.KEY_STREAM_TIMEOUT_MS, timeout)
            _uiState.update { it.copy(streamTimeoutMs = timeout) }
        }
    }
}