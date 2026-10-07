package com.example.ui.components

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.model.AspectRatioMode
import com.example.model.CacheMode
import com.example.ui.IptvViewModel

@Composable
fun SettingsDialog(
    viewModel: IptvViewModel,
    onDismiss: () -> Unit
) {
    val settings by viewModel.playerSettings.collectAsState()
    val isImportingM3u by viewModel.isImportingM3u.collectAsState()

    var m3uUrlInput by remember { mutableStateOf(settings.customM3uUrl) }
    var epgUrlInput by remember { mutableStateOf(settings.customEpgUrl) }
    var showPasteDialog by remember { mutableStateOf(false) }
    var pasteTextContent by remember { mutableStateOf("") }

    // SAF File Picker with safe fallback to TvFilePickerDialog on Android TV boxes
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            viewModel.importM3uFromFileUri(it)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .width(640.dp)
                .wrapContentHeight()
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, Color(0xFF2C384A), RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF141922))
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = Color(0xFF00E5FF)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "播放与系统设置",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "关闭", tint = Color.Gray)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 1. Network caching
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("VLC 网络缓冲 (4K超高码率请选 8000ms)：", color = Color(0xFF00E5FF), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("默认已开启 8000ms 4K抗卡顿缓冲", color = Color(0xFF81C784), fontSize = 11.sp)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CacheMode.values().forEach { mode ->
                        val selected = settings.cacheMode == mode
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) Color(0xFF00B0FF) else Color(0xFF1E2530))
                                .clickable { viewModel.setCacheMode(mode) }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = mode.displayName,
                                color = if (selected) Color.Black else Color.White,
                                fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 2. Hardware acceleration
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("通用安卓硬件加速解码 (MediaCodec 硬解优化)", color = Color.White, fontSize = 14.sp)
                        Text("全适配晶晨 Amlogic、瑞芯微、高通、联发科等全系芯片，4K H.265/HEVC 流畅硬解，异常自动回退", color = Color(0xFF8B9CB5), fontSize = 11.sp)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(
                        checked = settings.hardwareAcceleration,
                        onCheckedChange = { viewModel.setHardwareAcceleration(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF00E5FF),
                            checkedTrackColor = Color(0xFF00E5FF).copy(alpha = 0.5f)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Router msd_lite optimization tip card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1B2B)),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E3A5F))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("路由器 msd_lite 4K 直播防断流调优：", color = Color(0xFF00E5FF), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "若 37.7Mbps 4K 频道每几秒断开，系路由器端开启了 drop_slow_clients。本播放器已开启 8000ms 深度预读与 50ms 极速重连。建议在路由器执行：\n" +
                                    "uci set msd_lite.@instance[0].drop_slow_clients='0'\n" +
                                    "uci set msd_lite.@instance[0].ring_buffer_size='8192'\n" +
                                    "uci commit msd_lite && /etc/init.d/msd_lite restart",
                            color = Color(0xFFCBD5E1),
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 3. Aspect ratio
                Text("画面比例：", color = Color(0xFF8B9CB5), fontSize = 13.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AspectRatioMode.values().forEach { ratio ->
                        val selected = settings.aspectRatio == ratio
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) Color(0xFF00B0FF) else Color(0xFF1E2530))
                                .clickable { viewModel.setAspectRatio(ratio) }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = ratio.displayName,
                                color = if (selected) Color.Black else Color.White,
                                fontSize = 12.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // 4. M3U Import Section
                Text("M3U 直播源导入 (适配全版本安卓/电视盒子/U盘/本地文件)：", color = Color(0xFF00E5FF), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))

                // URL / Path Input
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = m3uUrlInput,
                        onValueChange = { m3uUrlInput = it },
                        placeholder = { Text("输入 http:// 网址 或 /sdcard/ 本地路径", color = Color.Gray, fontSize = 12.sp) },
                        singleLine = true,
                        enabled = !isImportingM3u,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF00E5FF),
                            unfocusedBorderColor = Color(0xFF2C384A),
                            focusedContainerColor = Color(0xFF1B222E),
                            unfocusedContainerColor = Color(0xFF1B222E)
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { viewModel.importM3uFromUrl(m3uUrlInput) },
                        enabled = !isImportingM3u,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0091EA)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (isImportingM3u) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Text("导入", color = Color.White)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Multi-way File Import Buttons (TV Local File, System Picker, Paste text)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Dedicated TV File Browser (No DocumentsUI dependency, 100% stable on Android TV boxes)
                    Button(
                        onClick = { viewModel.openTvFilePicker() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1.2f)
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("本地/U盘文件选择", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    // System File Picker with catch
                    OutlinedButton(
                        onClick = {
                            try {
                                filePickerLauncher.launch("*/*")
                            } catch (e: ActivityNotFoundException) {
                                viewModel.showToast("电视系统无内置文件选择器，已打开盒子本地浏览")
                                viewModel.openTvFilePicker()
                            } catch (e: Exception) {
                                viewModel.openTvFilePicker()
                            }
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF81C784)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("系统选择器", fontSize = 12.sp)
                    }

                    // Paste M3U text directly
                    OutlinedButton(
                        onClick = { showPasteDialog = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF64B5F6)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("粘贴文本", fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 5. XMLTV EPG URL
                Text("XMLTV 电子节目单 (EPG) URL：", color = Color(0xFF8B9CB5), fontSize = 13.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = epgUrlInput,
                        onValueChange = { epgUrlInput = it },
                        placeholder = { Text("输入 XMLTV EPG 网址 (http://...)", color = Color.Gray, fontSize = 12.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF00E5FF),
                            unfocusedBorderColor = Color(0xFF2C384A),
                            focusedContainerColor = Color(0xFF1B222E),
                            unfocusedContainerColor = Color(0xFF1B222E)
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { viewModel.importEpgFromUrl(epgUrlInput) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0091EA)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("更新EPG", color = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // 6. Action buttons: Reset default, Open VLC test lab
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            viewModel.resetToDefaultChannels()
                            m3uUrlInput = ""
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFFB74D)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("恢复默认内置频道", fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            onDismiss()
                            viewModel.openTestPlayer()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Tv, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("VLC 测试实验室", fontSize = 12.sp)
                    }
                }
            }
        }
    }

    // Direct paste M3U text dialog
    if (showPasteDialog) {
        AlertDialog(
            onDismissRequest = { showPasteDialog = false },
            title = { Text("粘贴 M3U 播放列表内容", color = Color.White) },
            text = {
                Column {
                    Text(
                        "支持 #EXTINF 格式或 DIYP (频道名,流地址) 格式：",
                        color = Color(0xFF8B9CB5),
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = pasteTextContent,
                        onValueChange = { pasteTextContent = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF00E5FF),
                            unfocusedBorderColor = Color(0xFF2C384A),
                            focusedContainerColor = Color(0xFF0B0E14),
                            unfocusedContainerColor = Color(0xFF0B0E14)
                        ),
                        placeholder = { Text("#EXTM3U\n#EXTINF:-1,CCTV-1\nhttp://...", color = Color.Gray) }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPasteDialog = false
                        viewModel.importM3uFromContent(pasteTextContent)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0091EA))
                ) {
                    Text("开始解析导入", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPasteDialog = false }) {
                    Text("取消", color = Color.Gray)
                }
            },
            containerColor = Color(0xFF141922)
        )
    }
}
