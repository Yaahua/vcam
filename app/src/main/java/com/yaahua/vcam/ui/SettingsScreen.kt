package com.yaahua.vcam.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.outlined.FolderSpecial
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yaahua.vcam.ConfigManager
import com.yaahua.vcam.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // General Settings
        SettingsSection(title = stringResource(R.string.settings_category_general)) {
            SettingsSwitchRow(
                icon = Icons.Default.NotificationsActive,
                title = stringResource(R.string.settings_notification_control),
                subtitle = stringResource(R.string.settings_notification_control_desc),
                checked = uiState.notificationControlEnabled,
                onCheckedChange = {
                    viewModel.setNotificationControlEnabled(it)
                    if (it) context.startService(Intent(context, com.yaahua.vcam.NotificationService::class.java).apply { action = "FOREGROUND" })
                    else context.stopService(Intent(context, com.yaahua.vcam.NotificationService::class.java))
                }
            )
            SettingsDivider()

            SettingsSwitchRow(
                icon = Icons.Default.VolumeUp,
                title = stringResource(R.string.settings_play_sound),
                subtitle = stringResource(R.string.settings_play_sound_desc),
                checked = uiState.playVideoSound,
                onCheckedChange = { viewModel.setPlayVideoSound(it) }
            )
            SettingsDivider()

            SettingsSwitchRow(
                icon = Icons.Default.Mic,
                title = stringResource(R.string.settings_mic_hook),
                subtitle = stringResource(R.string.settings_mic_hook_desc),
                checked = uiState.enableMicHook,
                onCheckedChange = { viewModel.setEnableMicHook(it) }
            )

            AnimatedVisibility(
                visible = uiState.enableMicHook,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.padding(start = 40.dp, top = 4.dp, bottom = 4.dp)) {
                    MicModeOption(
                        title = stringResource(R.string.mic_mode_mute),
                        description = stringResource(R.string.mic_mode_mute_desc),
                        selected = uiState.micHookMode == "mute",
                        onClick = { viewModel.setMicHookMode("mute") }
                    )
                    MicModeOption(
                        title = stringResource(R.string.mic_mode_replace),
                        description = stringResource(R.string.mic_mode_replace_desc),
                        selected = uiState.micHookMode == "replace",
                        onClick = { viewModel.setMicHookMode("replace") }
                    )
                    MicModeOption(
                        title = stringResource(R.string.mic_mode_video_sync),
                        description = stringResource(R.string.mic_mode_video_sync_desc),
                        selected = uiState.micHookMode == "video_sync",
                        onClick = { viewModel.setMicHookMode("video_sync") }
                    )
                }
            }
            SettingsDivider()

            SettingsSwitchRow(
                icon = Icons.Default.Shuffle,
                title = stringResource(R.string.settings_random_play),
                subtitle = stringResource(R.string.settings_random_play_desc),
                checked = uiState.enableRandomPlay,
                onCheckedChange = { viewModel.setEnableRandomPlay(it) }
            )
        }

        // Stream Settings
        SettingsSection(title = stringResource(R.string.settings_category_stream)) {
            val isStreamMode = uiState.mediaSourceType == ConfigManager.MEDIA_SOURCE_STREAM

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.SettingsInputAntenna, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(14.dp))
                Text(stringResource(R.string.settings_media_source_type), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 36.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(selected = !isStreamMode, onClick = { viewModel.setMediaSourceType(ConfigManager.MEDIA_SOURCE_LOCAL) }, label = { Text(stringResource(R.string.settings_media_source_local)) },
                    leadingIcon = if (!isStreamMode) { { Icon(Icons.Outlined.Videocam, null, Modifier.size(18.dp)) } } else null)
                FilterChip(selected = isStreamMode, onClick = { viewModel.setMediaSourceType(ConfigManager.MEDIA_SOURCE_STREAM) }, label = { Text(stringResource(R.string.settings_media_source_stream)) },
                    leadingIcon = if (isStreamMode) { { Icon(Icons.Default.Link, null, Modifier.size(18.dp)) } } else null)
            }

            AnimatedVisibility(visible = isStreamMode, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column {
                    Spacer(modifier = Modifier.height(8.dp))
                    var urlText by remember(uiState.streamUrl) { mutableStateOf(uiState.streamUrl) }
                    OutlinedTextField(
                        value = urlText, onValueChange = { urlText = it },
                        label = { Text(stringResource(R.string.settings_stream_url)) },
                        placeholder = { Text(stringResource(R.string.settings_stream_url_hint)) },
                        singleLine = true, modifier = Modifier.fillMaxWidth().padding(start = 36.dp, end = 4.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                    )
                    if (urlText != uiState.streamUrl) {
                        TextButton(onClick = { viewModel.setStreamUrl(urlText) }, modifier = Modifier.padding(end = 4.dp)) {
                            Text(stringResource(R.string.positive))
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    SettingsSwitchRow(icon = Icons.Default.Refresh, title = stringResource(R.string.settings_stream_auto_reconnect), subtitle = stringResource(R.string.settings_stream_auto_reconnect_desc), checked = uiState.streamAutoReconnect, onCheckedChange = { viewModel.setStreamAutoReconnect(it) })
                    SettingsDivider()
                    SettingsSwitchRow(icon = Icons.Outlined.Videocam, title = stringResource(R.string.settings_stream_local_fallback), subtitle = stringResource(R.string.settings_stream_local_fallback_desc), checked = uiState.streamLocalFallback, onCheckedChange = { viewModel.setStreamLocalFallback(it) })
                    SettingsDivider()
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.SettingsInputAntenna, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(14.dp))
                        Text(stringResource(R.string.settings_stream_transport_hint), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    }
                    Column(modifier = Modifier.padding(start = 40.dp)) {
                        TransportOption(title = stringResource(R.string.settings_stream_transport_auto), selected = uiState.streamTransportHint == "auto", onClick = { viewModel.setStreamTransportHint("auto") })
                        TransportOption(title = stringResource(R.string.settings_stream_transport_tcp), selected = uiState.streamTransportHint == "tcp", onClick = { viewModel.setStreamTransportHint("tcp") })
                        TransportOption(title = stringResource(R.string.settings_stream_transport_udp), selected = uiState.streamTransportHint == "udp", onClick = { viewModel.setStreamTransportHint("udp") })
                    }
                    SettingsDivider()
                    var timeoutText by remember(uiState.streamTimeoutMs) { mutableStateOf(uiState.streamTimeoutMs.toString()) }
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Timer, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(14.dp))
                        OutlinedTextField(
                            value = timeoutText, onValueChange = { newVal -> timeoutText = newVal; newVal.toLongOrNull()?.let { viewModel.setStreamTimeoutMs(it) } },
                            label = { Text(stringResource(R.string.settings_stream_timeout)) }, singleLine = true, modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), suffix = { Text("ms") }
                        )
                    }
                }
            }
        }

        // Advanced Settings
        SettingsSection(title = stringResource(R.string.settings_category_advanced)) {
            SettingsSwitchRow(
                icon = Icons.Outlined.FolderSpecial,
                title = stringResource(R.string.settings_force_private_dir),
                subtitle = stringResource(R.string.settings_force_private_dir_desc),
                checked = uiState.forcePrivateDir,
                onCheckedChange = { viewModel.setForcePrivateDir(it) }
            )
            SettingsDivider()

            SettingsSwitchRow(
                icon = Icons.Outlined.NotificationsOff,
                title = stringResource(R.string.settings_disable_toast),
                subtitle = stringResource(R.string.settings_disable_toast_desc),
                checked = uiState.disableToast,
                onCheckedChange = { viewModel.setDisableToast(it) }
            )
            SettingsDivider()

            SettingsSwitchRow(
                icon = Icons.Default.Image,
                title = stringResource(R.string.settings_enable_photo_fake),
                subtitle = stringResource(R.string.settings_enable_photo_fake_desc),
                checked = uiState.enablePhotoFake,
                onCheckedChange = { viewModel.setEnablePhotoFake(it) }
            )
            SettingsDivider()

            SettingsSwitchRow(
                icon = Icons.Default.Videocam,
                title = stringResource(R.string.settings_disable_module),
                subtitle = stringResource(R.string.settings_disable_module_desc),
                checked = uiState.isModuleDisabled,
                onCheckedChange = { viewModel.setModuleDisabled(it) }
            )
            SettingsDivider()

            SettingsClickRow(
                icon = Icons.Default.Security,
                title = stringResource(R.string.settings_system_permission),
                subtitle = stringResource(R.string.settings_system_permission_desc),
                onClick = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                }
            )
        }

        // About
        SettingsSection(title = stringResource(R.string.about_title)) {
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.about_app_name), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(stringResource(R.string.about_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            SettingsDivider()
            SettingsClickRow(
                icon = Icons.Default.Code, title = "GitHub", subtitle = stringResource(R.string.support_github),
                onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Yaahua/vcam"))) }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp), shape = MaterialTheme.shapes.large) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp), color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun SettingsSwitchRow(icon: ImageVector, title: String, subtitle: String? = null, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable { onCheckedChange(!checked) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 16.sp)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsClickRow(icon: ImageVector, title: String, subtitle: String? = null, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 16.sp)
        }
        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun MicModeOption(title: String, description: String, selected: Boolean, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable(onClick = onClick).padding(vertical = 6.dp)) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(modifier = Modifier.width(6.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 15.sp)
        }
    }
}

@Composable
private fun TransportOption(title: String, selected: Boolean, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable(onClick = onClick).padding(vertical = 4.dp)) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(modifier = Modifier.width(6.dp))
        Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal)
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f), modifier = Modifier.padding(start = 36.dp))
}
