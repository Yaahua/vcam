@file:OptIn(ExperimentalMaterial3Api::class)
package com.yaahua.vcam.ui

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yaahua.vcam.R
import java.io.File
import java.util.Locale

@Composable
fun ManageScreen(viewModel: MediaViewModel) {
    var selectedTab by remember { mutableStateOf(0) }
    var showFabMenu by remember { mutableStateOf(false) }
    val uiState by viewModel.uiState.collectAsState()

    val videoImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.importFiles(uris, false)
    }

    val videoRefLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            // 尝试从 content URI 解析文件路径
            val path = tryResolveFilePath(uri)
            if (path != null) {
                viewModel.selectVideoByAbsolutePath(path)
            }
        }
    }

    val audioLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.importFiles(uris, true)
    }

    Scaffold(
        floatingActionButton = {
            Box {
                DropdownMenu(
                    expanded = showFabMenu,
                    onDismissRequest = { showFabMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.import_to_camera_dir)) },
                        onClick = {
                            showFabMenu = false
                            videoImportLauncher.launch(arrayOf("video/*"))
                        },
                        leadingIcon = { Icon(Icons.Default.Add, null) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.select_from_storage)) },
                        onClick = {
                            showFabMenu = false
                            videoRefLauncher.launch(arrayOf("video/*"))
                        },
                        leadingIcon = { Icon(Icons.Default.VideoLibrary, null) }
                    )
                }
                FloatingActionButton(
                    onClick = { showFabMenu = true }
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.desc_add_media))
                }
            }
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
                val stats = when (selectedTab) {
                    0 -> String.format(
                        Locale.getDefault(),
                        "%d %s | %.1fMB",
                        uiState.videos.size,
                        stringResource(R.string.tab_video_manage),
                        uiState.totalVideoSizeMb
                    )
                    else -> String.format(
                        Locale.getDefault(),
                        "%d %s",
                        uiState.audios.size,
                        stringResource(R.string.tab_audio_manage)
                    )
                }
                Text(
                    text = stats,
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            TabRow(selectedTabIndex = selectedTab, modifier = Modifier.height(48.dp)) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
                        Icon(Icons.Default.VideoLibrary, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.tab_video_manage), fontSize = 14.sp)
                    }
                }
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
                        Icon(Icons.Default.MusicNote, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.tab_audio_manage), fontSize = 14.sp)
                    }
                }
            }

            if (uiState.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (selectedTab == 0) {
                        items(uiState.videos) { video ->
                            MediaCard(
                                name = video.name,
                                sizeMb = video.size / 1048576.0,
                                durationMs = video.durationMs,
                                isSelected = isVideoSelected(video, uiState.selectedVideoName),
                                isExternal = video.file.absolutePath.startsWith("/")
                                    && video.file.parent != "/storage/emulated/0/DCIM/Camera1",
                                isAudio = false,
                                onSelect = {
                                    // 外部视频用绝对路径，内部视频用文件名
                                    if (video.file.absolutePath.startsWith("/")
                                        && video.file.parent != "/storage/emulated/0/DCIM/Camera1") {
                                        viewModel.selectVideoByAbsolutePath(video.file.absolutePath)
                                    } else {
                                        viewModel.selectVideo(video.name)
                                    }
                                },
                                onDelete = {
                                    // 外部视频只能取消选择不能删除
                                    if (video.file.parent == "/storage/emulated/0/DCIM/Camera1") {
                                        viewModel.deleteFile(video.file)
                                    } else {
                                        viewModel.selectVideoByAbsolutePath(video.file.absolutePath)
                                    }
                                }
                            )
                        }
                        if (uiState.videos.isEmpty()) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                    Text(stringResource(R.string.status_no_video), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    } else {
                        items(uiState.audios) { audio ->
                            MediaCard(
                                name = audio.name,
                                sizeMb = audio.size / 1048576.0,
                                durationMs = audio.durationMs,
                                isSelected = audio.name == uiState.selectedAudioName,
                                isExternal = false,
                                isAudio = true,
                                onSelect = { viewModel.selectAudio(audio.name) },
                                onDelete = { viewModel.deleteFile(audio.file) }
                            )
                        }
                        if (uiState.audios.isEmpty()) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                    Text(stringResource(R.string.status_no_audio), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun isVideoSelected(video: VideoItem, selectedVideoName: String?): Boolean {
    if (selectedVideoName == null) return false
    // 支持绝对路径匹配
    if (selectedVideoName.startsWith("/")) {
        return video.file.absolutePath == selectedVideoName
    }
    return video.name == selectedVideoName
}

private fun tryResolveFilePath(uri: android.net.Uri): String? {
    // content URI 路径可能包含编码
    val rawPath = uri.path ?: return null
    // /document/primary:Download/myvideo.mp4
    if (rawPath.contains(":") && rawPath.contains("primary")) {
        val afterColon = rawPath.substringAfter(":")
        val decoded = java.net.URLDecoder.decode(afterColon, "UTF-8")
        return "/storage/emulated/0/$decoded"
    }
    // /external/video/media/123
    if (rawPath.startsWith("/external/")) return null // 无法简单解析
    // /storage/emulated/0/...
    if (rawPath.startsWith("/storage/")) return rawPath
    return null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MediaCard(
    name: String,
    sizeMb: Double,
    durationMs: Long,
    isSelected: Boolean,
    isExternal: Boolean,
    isAudio: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        elevation = CardDefaults.cardElevation(if (isSelected) 4.dp else 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                             else MaterialTheme.colorScheme.surface
        ),
        onClick = onSelect
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (isAudio) Icons.Default.MusicNote else Icons.Default.VideoLibrary,
                null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(40.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.weight(1f, fill = false))
                    if (isExternal) {
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            shape = MaterialTheme.shapes.extraSmall,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                        ) {
                            Text(
                                "外部",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row {
                    Text(String.format(Locale.getDefault(), "%.1f MB", sizeMb),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (durationMs > 0) {
                        val s = durationMs / 1000
                        val m = s / 60
                        Text(" • ${m}:${String.format("%02d", s % 60)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (isSelected) {
                Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
            }
            // 外部文件显示取消选择图标而非删除图标
            IconButton(onClick = onDelete) {
                Icon(
                    if (isExternal) Icons.Default.CheckCircle else Icons.Default.Delete,
                    null,
                    tint = if (isExternal) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
