package com.yaahua.vcam.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yaahua.vcam.R
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageScreen(viewModel: MediaViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        stringResource(R.string.tab_video_manage),
        stringResource(R.string.tab_audio_manage)
    )

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title) }
                )
            }
        }

        when (selectedTab) {
            0 -> VideoManageTab(uiState.videos, viewModel)
            1 -> AudioManageTab(uiState.audios, viewModel)
        }
    }
}

@Composable
fun VideoManageTab(videos: List<VideoItem>, viewModel: MediaViewModel) {
    val context = LocalContext.current

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.importFiles(listOf(uri), isAudio = false)
            Toast.makeText(context, R.string.import_to_camera_dir, Toast.LENGTH_SHORT).show()
        }
    }

    val selectLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                // 从 Uri 转为文件路径后调用 selectVideoByAbsolutePath
                val path = uri.toString()
                viewModel.selectVideoByAbsolutePath(path)
                Toast.makeText(context, R.string.select_from_storage, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "添加失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (videos.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(R.string.status_no_video),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
                items(videos) { video ->
                    VideoRow(
                        video = video,
                        onDelete = { viewModel.deleteFile(video.file) }
                    )
                    HorizontalDivider()
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { importLauncher.launch("video/*") },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.import_to_camera_dir))
            }
            OutlinedButton(
                onClick = { selectLauncher.launch(arrayOf("video/*")) },
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.select_from_storage))
            }
        }
    }
}

@Composable
fun VideoRow(video: VideoItem, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onDelete() }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Videocam,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                video.name,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${formatFileSize(video.size)}  ${formatDuration(video.durationMs)}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "删除",
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
fun AudioManageTab(audios: List<AudioItem>, viewModel: MediaViewModel) {
    val audioImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.importFiles(listOf(uri), isAudio = true)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (audios.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(R.string.status_no_audio),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
                items(audios) { audio ->
                    AudioRow(
                        audio = audio,
                        onDelete = { viewModel.deleteFile(audio.file) }
                    )
                    HorizontalDivider()
                }
            }
        }
        Button(
            onClick = { audioImportLauncher.launch("audio/*") },
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.desc_add_media))
        }
    }
}

@Composable
fun AudioRow(audio: AudioItem, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onDelete() }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Audiotrack,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                audio.name,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${formatFileSize(audio.size)}  ${formatDuration(audio.durationMs)}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "删除",
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
        else -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
    }
}

private fun formatDuration(ms: Long): String {
    val sec = ms / 1000
    return String.format(Locale.US, "%d:%02d", sec / 60, sec % 60)
}