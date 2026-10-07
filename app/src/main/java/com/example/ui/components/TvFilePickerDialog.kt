package com.example.ui.components

import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import java.io.File
import java.text.DecimalFormat

@Composable
fun TvFilePickerDialog(
    onDismiss: () -> Unit,
    onFileSelected: (File) -> Unit
) {
    val initialDir = remember {
        val download = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (download != null && download.exists()) {
            download
        } else {
            Environment.getExternalStorageDirectory() ?: File("/storage/emulated/0")
        }
    }

    var currentDir by remember { mutableStateOf(initialDir) }
    var fileList by remember { mutableStateOf<List<File>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun refreshFiles(dir: File) {
        try {
            if (!dir.exists() || !dir.canRead()) {
                errorMessage = "无法读取目录: ${dir.absolutePath}"
                fileList = emptyList()
                return
            }
            errorMessage = null
            val all = dir.listFiles()?.toList() ?: emptyList()
            // Folders first, then m3u/m3u8/txt files, then other files
            val sorted = all.filter { !it.name.startsWith(".") }
                .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            fileList = sorted
        } catch (e: Exception) {
            errorMessage = "读取目录异常: ${e.message}"
            fileList = emptyList()
        }
    }

    LaunchedEffect(currentDir) {
        refreshFiles(currentDir)
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .width(660.dp)
                .height(480.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, Color(0xFF00E5FF), RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF10151E))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Folder, contentDescription = null, tint = Color(0xFF00E5FF))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "选择本地 M3U / TXT 播放列表",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "关闭", tint = Color.Gray)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Quick directory shortcut chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    val sdcardDir = Environment.getExternalStorageDirectory()
                    val storageRoot = File("/storage")

                    AssistChip(
                        onClick = { currentDir = downloadDir ?: File("/sdcard/Download") },
                        label = { Text("Download 目录", color = Color.White, fontSize = 11.sp) },
                        colors = AssistChipDefaults.assistChipColors(containerColor = Color(0xFF1E2634))
                    )
                    AssistChip(
                        onClick = { currentDir = sdcardDir ?: File("/sdcard") },
                        label = { Text("内部存储 (/sdcard)", color = Color.White, fontSize = 11.sp) },
                        colors = AssistChipDefaults.assistChipColors(containerColor = Color(0xFF1E2634))
                    )
                    AssistChip(
                        onClick = { if (storageRoot.exists()) currentDir = storageRoot },
                        leadingIcon = { Icon(Icons.Default.Usb, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFFFFB74D)) },
                        label = { Text("U盘/外置设备", color = Color.White, fontSize = 11.sp) },
                        colors = AssistChipDefaults.assistChipColors(containerColor = Color(0xFF1E2634))
                    )
                }

                // Current path indicator
                Text(
                    text = "当前路径: ${currentDir.absolutePath}",
                    color = Color(0xFF8B9CB5),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                HorizontalDivider(color = Color(0xFF202A38), thickness = 1.dp)

                Spacer(modifier = Modifier.height(6.dp))

                // File list
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    // Go up one level option if parent exists
                    val parent = currentDir.parentFile
                    if (parent != null && parent.canRead()) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { currentDir = parent }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(".. 返回上级目录", color = Color(0xFF00E5FF), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    if (fileList.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = errorMessage ?: "此目录下没有文件",
                                    color = Color.Gray,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }

                    items(fileList) { file ->
                        val isDir = file.isDirectory
                        val isM3u = file.name.endsWith(".m3u", ignoreCase = true) ||
                                file.name.endsWith(".m3u8", ignoreCase = true) ||
                                file.name.endsWith(".txt", ignoreCase = true)

                        val highlightColor = if (isM3u) Color(0xFF00E5FF) else if (isDir) Color(0xFFFFB74D) else Color.White

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isM3u) Color(0x2200E5FF) else Color.Transparent)
                                .clickable {
                                    if (isDir) {
                                        currentDir = file
                                    } else {
                                        onFileSelected(file)
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isDir) Icons.Default.Folder else Icons.Default.Description,
                                    contentDescription = null,
                                    tint = highlightColor,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = file.name,
                                    color = if (isM3u) Color(0xFF00E5FF) else Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = if (isM3u) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            if (!isDir) {
                                Text(
                                    text = formatFileSize(file.length()),
                                    color = Color(0xFF8B9CB5),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatFileSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, 3)
    return DecimalFormat("#,##0.#").format(size / Math.pow(1024.0, digitGroups.toDouble())) + " " + units[digitGroups]
}
