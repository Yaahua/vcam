package com.yaahua.vcam.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yaahua.vcam.R

@Composable
fun HomeScreen(
    mainViewModel: MainViewModel,
    onPermissionRequest: () -> Unit
) {
    val mainUiState by mainViewModel.uiState.collectAsState()
    val isWorking = mainUiState.hasPermission && !mainUiState.isModuleDisabled

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        StatusCard(
            isXposedActive = mainUiState.isXposedActive,
            hasPermission = mainUiState.hasPermission,
            isWorking = isWorking,
            isRandomPlay = mainUiState.enableRandomPlay,
            enableMicHook = mainUiState.enableMicHook,
            micHookMode = mainUiState.micHookMode,
            playVideoSound = mainUiState.playVideoSound,
            notificationControlEnabled = mainUiState.notificationControlEnabled,
            onPermissionRequest = onPermissionRequest
        )

        SupportCard()
    }
}

@Composable
fun StatusCard(
    isXposedActive: Boolean,
    hasPermission: Boolean,
    isWorking: Boolean,
    isRandomPlay: Boolean,
    enableMicHook: Boolean,
    micHookMode: String,
    playVideoSound: Boolean,
    notificationControlEnabled: Boolean,
    onPermissionRequest: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val (backgroundColor, textColor, statusText, statusIcon) = when {
        !isXposedActive -> Quadruple(
            colorScheme.errorContainer,
            colorScheme.onErrorContainer,
            stringResource(R.string.status_module_inactive),
            Icons.Default.Error
        )
        !hasPermission -> Quadruple(
            colorScheme.errorContainer,
            colorScheme.onErrorContainer,
            stringResource(R.string.status_no_permission),
            Icons.Default.Error
        )
        isWorking -> Quadruple(
            colorScheme.primaryContainer,
            colorScheme.onPrimaryContainer,
            stringResource(R.string.status_working),
            Icons.Default.CheckCircle
        )
        else -> Quadruple(
            colorScheme.surfaceVariant,
            colorScheme.onSurfaceVariant,
            stringResource(R.string.status_paused),
            Icons.Default.Pause
        )
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.elevation(defaultElevation = 4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(bottom = 16.dp),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = statusIcon,
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = textColor
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (!isXposedActive) {
                Text(
                    text = stringResource(R.string.status_activate_hint),
                    fontSize = 14.sp,
                    color = textColor.copy(alpha = 0.8f)
                )
            } else if (!hasPermission) {
                Button(
                    onClick = onPermissionRequest,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colorScheme.error,
                        contentColor = colorScheme.onError
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(stringResource(R.string.status_grant_permission), fontSize = 14.sp)
                }
            } else {
                Divider(
                    color = textColor.copy(alpha = 0.15f),
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                StatusRow(
                    icon = Icons.Default.Videocam,
                    label = stringResource(R.string.status_label_play_mode),
                    value = if (isRandomPlay) stringResource(R.string.status_value_random_play)
                            else stringResource(R.string.status_value_sequential_play),
                    valueIcon = if (isRandomPlay) Icons.Default.Shuffle else null,
                    tint = textColor
                )

                val micStatusValue = if (!enableMicHook) {
                    stringResource(R.string.status_value_off)
                } else {
                    when (micHookMode) {
                        "mute" -> stringResource(R.string.status_value_mic_mute)
                        "replace" -> stringResource(R.string.status_value_mic_replace)
                        "video_sync" -> stringResource(R.string.status_value_mic_sync)
                        else -> stringResource(R.string.status_value_off)
                    }
                }
                StatusRow(
                    icon = if (enableMicHook) Icons.Default.Mic else Icons.Default.MicOff,
                    label = stringResource(R.string.status_label_mic),
                    value = micStatusValue,
                    tint = textColor
                )

                StatusRow(
                    icon = if (playVideoSound) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                    label = stringResource(R.string.status_label_video_sound),
                    value = if (playVideoSound) stringResource(R.string.status_value_on)
                            else stringResource(R.string.status_value_off),
                    tint = textColor
                )

                StatusRow(
                    icon = if (notificationControlEnabled) Icons.Default.Notifications
                           else Icons.Default.NotificationsOff,
                    label = stringResource(R.string.status_label_notification),
                    value = if (notificationControlEnabled) stringResource(R.string.status_value_on)
                            else stringResource(R.string.status_value_off),
                    tint = textColor,
                    isLast = true
                )
            }
        }
    }
}

@Composable
private fun StatusRow(
    icon: ImageVector,
    label: String,
    value: String,
    valueIcon: ImageVector? = null,
    tint: Color,
    isLast: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint.copy(alpha = 0.6f),
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = label,
            fontSize = 13.sp,
            color = tint.copy(alpha = 0.6f),
            modifier = Modifier.widthIn(min = 60.dp, max = 90.dp)
        )
        Spacer(modifier = Modifier.weight(1f))
        if (valueIcon != null) {
            Icon(
                imageVector = valueIcon,
                contentDescription = null,
                tint = tint.copy(alpha = 0.85f),
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color =  tint.copy(alpha = 0.85f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 200.dp)
        )
    }
    if (!isLast) {
        Divider(color = tint.copy(alpha = 0.06f), modifier = Modifier.padding(start = 28.dp))
    }
}

@Composable
fun SupportCard() {
    val context = LocalContext.current

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.elevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.support_title),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Yaahua/vcam"))
                        )
                    }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Code,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.support_github),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)