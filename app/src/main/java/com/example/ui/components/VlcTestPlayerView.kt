package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.CacheMode
import com.example.model.PlaybackStatus
import com.example.player.VlcPlayerManager

@Composable
fun VlcTestPlayerView(
    playerManager: VlcPlayerManager,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentUrl by playerManager.currentUrl.collectAsStateWithLifecycle()
    val playbackStatus by playerManager.playbackStatus.collectAsStateWithLifecycle()
    val logMessages by playerManager.logMessages.collectAsStateWithLifecycle()

    var inputUrl by remember {
        mutableStateOf(
            if (currentUrl.isNotBlank()) currentUrl
            else "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
        )
    }

    val presetStreams = remember {
        listOf(
            "Mux HLS 测试直播流" to "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
            "Akamai 4K/HLS 直播" to "https://cph-p2p-msl.akamaized.net/hls/live/2000341/test/master.m3u8",
            "BigBuck MPEG-TS/MP4" to "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
            "Tears of Steel TS" to "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4"
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xE60A0D14))
            .padding(20.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1E2530))
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = Color.White
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "libVLC 独立播放器测试实验室",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Amlogic S905L3A 硬件加速与 IPTV MPEG-TS/M3U8 直播测试 (底层直通渲染)",
                    color = Color(0xFF8B9CB5),
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // URL input and action controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputUrl,
                onValueChange = { inputUrl = it },
                label = { Text("输入测试流 URL (HTTP/HTTPS/M3U8/MPEG-TS/RTSP)") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF00E5FF),
                    unfocusedBorderColor = Color(0xFF2C384A),
                    focusedContainerColor = Color(0xFF131822),
                    unfocusedContainerColor = Color(0xFF131822)
                ),
                singleLine = true,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Button(
                onClick = {
                    if (inputUrl.isNotBlank()) {
                        playerManager.play(inputUrl, immediate = true)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0091EA)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(56.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("播放")
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = { playerManager.stop() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(56.dp)
            ) {
                Icon(Icons.Default.Stop, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("停止")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Preset streams chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("快速填入测试流：", color = Color(0xFF8B9CB5), fontSize = 12.sp)
            presetStreams.forEach { (name, url) ->
                AssistChip(
                    onClick = {
                        inputUrl = url
                        playerManager.play(url, immediate = true)
                    },
                    label = { Text(name, color = Color.White, fontSize = 11.sp) },
                    colors = AssistChipDefaults.assistChipColors(containerColor = Color(0xFF1E2530))
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Playback Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF131922)),
            shape = RoundedCornerShape(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("当前状态: ", color = Color(0xFF8B9CB5), fontSize = 13.sp)
                    when (val status = playbackStatus) {
                        is PlaybackStatus.Idle -> Text("空闲未播放", color = Color.Gray, fontSize = 13.sp)
                        is PlaybackStatus.Loading -> Text(status.message, color = Color(0xFFFFD54F), fontSize = 13.sp)
                        is PlaybackStatus.Buffering -> Text("缓冲中 ${status.percent.toInt()}%", color = Color(0xFF4FC3F7), fontSize = 13.sp)
                        is PlaybackStatus.Playing -> Text("正常播放中 (${status.videoWidth}x${status.videoHeight})", color = Color(0xFF81C784), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        is PlaybackStatus.Error -> Text("异常: ${status.errorMessage}", color = Color(0xFFFF5252), fontSize = 13.sp)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { playerManager.setHardwareAcceleration(!playerManager.hardwareAcceleration) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (playerManager.hardwareAcceleration) Color(0xFF00796B) else Color(0xFF455A64)
                        ),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(if (playerManager.hardwareAcceleration) "硬件解码: 开启" else "硬件解码: 关闭 (纯软解)", fontSize = 11.sp)
                    }

                    Button(
                        onClick = {
                            val nextMode = when (playerManager.cacheMode) {
                                CacheMode.LOW_LATENCY -> CacheMode.STANDARD
                                CacheMode.STANDARD -> CacheMode.STABLE
                                CacheMode.STABLE -> CacheMode.ULTRA_4K
                                CacheMode.ULTRA_4K -> CacheMode.LOW_LATENCY
                            }
                            playerManager.setNetworkCaching(nextMode)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0)),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("缓存: ${playerManager.cacheMode.displayName}", fontSize = 11.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Realtime VLC logs console
        Text("libVLC 运行实时日志 (用于诊断解码与连接问题)：", color = Color(0xFF8B9CB5), fontSize = 12.sp)
        Spacer(modifier = Modifier.height(6.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF07090D))
                .border(1.dp, Color(0xFF1E2530), RoundedCornerShape(8.dp))
                .padding(10.dp)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                reverseLayout = true
            ) {
                items(logMessages.reversed()) { msg ->
                    Text(
                        text = msg,
                        color = when {
                            msg.contains("失败") || msg.contains("异常") -> Color(0xFFFF5252)
                            msg.contains("正常") || msg.contains("成功") -> Color(0xFF81C784)
                            msg.contains("缓冲") -> Color(0xFF64B5F6)
                            else -> Color(0xFFCBD5E1)
                        },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        modifier = Modifier.padding(vertical = 1.dp)
                    )
                }
            }
        }
    }
}
