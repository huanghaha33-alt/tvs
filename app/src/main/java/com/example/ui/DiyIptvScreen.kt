package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.PlaybackStatus
import com.example.ui.components.ChannelNumberOsd
import com.example.ui.components.SettingsDialog
import com.example.ui.components.TvFilePickerDialog
import com.example.ui.components.VlcTestPlayerView
import com.example.ui.components.VlcVideoSurface
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DiyIptvScreen(
    viewModel: IptvViewModel,
    modifier: Modifier = Modifier
) {
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val categoryChannels by viewModel.categoryChannels.collectAsStateWithLifecycle()
    val highlightedChannel by viewModel.highlightedChannel.collectAsStateWithLifecycle()
    val currentPlayingChannel by viewModel.currentPlayingChannel.collectAsStateWithLifecycle()
    val focusColumn by viewModel.focusColumn.collectAsStateWithLifecycle()

    val categoryIndex by viewModel.categoryIndex.collectAsStateWithLifecycle()
    val channelIndex by viewModel.channelIndex.collectAsStateWithLifecycle()

    val isFullscreen by viewModel.isFullscreen.collectAsStateWithLifecycle()
    val isChannelOverlayVisible by viewModel.isChannelOverlayVisible.collectAsStateWithLifecycle()
    val isCategoryColumnVisible by viewModel.isCategoryColumnVisible.collectAsStateWithLifecycle()
    val isSettingsOpen by viewModel.isSettingsOpen.collectAsStateWithLifecycle()
    val isTvFilePickerOpen by viewModel.isTvFilePickerOpen.collectAsStateWithLifecycle()
    val isTestPlayerOpen by viewModel.isTestPlayerOpen.collectAsStateWithLifecycle()
    val isExitConfirmOpen by viewModel.isExitConfirmOpen.collectAsStateWithLifecycle()

    val numberInput by viewModel.numberInput.collectAsStateWithLifecycle()
    val toastMessage by viewModel.toastMessage.collectAsStateWithLifecycle()
    val playbackStatus by viewModel.playerManager.playbackStatus.collectAsStateWithLifecycle()
    val streamMediaInfo by viewModel.playerManager.streamMediaInfo.collectAsStateWithLifecycle()

    // Handle Android system/TV back button
    BackHandler {
        viewModel.onBackPress()
    }

    // Realtime clock
    var currentTimeString by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        while (true) {
            currentTimeString = sdf.format(Date())
            delay(1000)
        }
    }

    // Auto-scroll list when selection changes
    val categoryListState = rememberLazyListState()
    val channelListState = rememberLazyListState()

    LaunchedEffect(categoryIndex) {
        if (categoryIndex in categories.indices) {
            categoryListState.animateScrollToItem((categoryIndex - 2).coerceAtLeast(0))
        }
    }

    LaunchedEffect(channelIndex) {
        if (channelIndex in categoryChannels.indices) {
            channelListState.animateScrollToItem((channelIndex - 3).coerceAtLeast(0))
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Base layer: Native libVLC video surface (strictly centered with aspect ratio preservation)
        VlcVideoSurface(
            playerManager = viewModel.playerManager,
            modifier = Modifier.fillMaxSize()
        )

        // Overlay & Menu Layers
        AnimatedVisibility(
            visible = !isFullscreen || isChannelOverlayVisible,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xF207090D),
                                Color(0xD80D1117),
                                Color(0x66000000),
                                Color.Transparent
                            ),
                            startX = 0f,
                            endX = 1400f
                        )
                    )
            ) {
                // Left Column (~18%): Category list - can be collapsed/hidden on click
                if (isCategoryColumnVisible) {
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(0.18f)
                            .background(Color(0xCC080B10))
                            .padding(vertical = 16.dp)
                    ) {
                        // Category Header & Hide button
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "DIYP 直播",
                                    color = Color(0xFF00E5FF),
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = "libVLC 内核",
                                    color = Color(0xFF6B7D96),
                                    fontSize = 10.sp
                                )
                            }

                            // Explicit hide button
                            Button(
                                onClick = { viewModel.hideCategoryColumn() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E2633)),
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "隐藏分类",
                                    tint = Color(0xFF00E5FF),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("隐藏", color = Color(0xFF00E5FF), fontSize = 11.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        LazyColumn(
                            state = categoryListState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            itemsIndexed(categories) { index, category ->
                                val isFocused = (focusColumn == TvFocusColumn.CATEGORY && categoryIndex == index)
                                val isSelected = (selectedCategory == category.name)

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            when {
                                                isFocused -> Color(0xFF007ACC).copy(alpha = 0.5f)
                                                isSelected -> Color(0xFF1E2530).copy(alpha = 0.6f)
                                                else -> Color.Transparent
                                            }
                                        )
                                        .border(
                                            width = if (isFocused) 1.5.dp else 0.dp,
                                            color = if (isFocused) Color(0xFF00E5FF) else Color.Transparent,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable {
                                            viewModel.selectCategory(category.name)
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (isSelected) {
                                                Text(
                                                    text = "<·· ",
                                                    color = Color(0xFF00E5FF),
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            Text(
                                                text = category.name,
                                                color = when {
                                                    isFocused -> Color.White
                                                    isSelected -> Color(0xFFE0E0E0)
                                                    else -> Color(0xFF8B9CB5)
                                                },
                                                fontSize = 15.sp,
                                                fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Normal
                                            )
                                        }
                                        Text(
                                            text = "${category.channelCount}",
                                            color = Color(0xFF4C5D73),
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }

                        // Settings button
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF141922))
                                .clickable { viewModel.openSettings() }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "设置",
                                tint = Color(0xFF8B9CB5),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("播放设置", color = Color(0xFF8B9CB5), fontSize = 12.sp)
                        }
                    }
                }

                // Middle Column: Channel list (adjusts weight when category is hidden)
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(if (isCategoryColumnVisible) 0.32f else 0.38f)
                        .background(Color(0xB30B0F15))
                        .padding(vertical = 16.dp, horizontal = 8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Expand category button if category column is currently hidden
                            if (!isCategoryColumnVisible) {
                                Button(
                                    onClick = { viewModel.showCategoryColumn() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E2633)),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ChevronRight,
                                        contentDescription = "展开分类",
                                        tint = Color(0xFF00E5FF),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text("分类", color = Color(0xFF00E5FF), fontSize = 11.sp)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                            }

                            Text(
                                text = selectedCategory,
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            text = "${categoryChannels.size} 个频道",
                            color = Color(0xFF8B9CB5),
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    LazyColumn(
                        state = channelListState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        itemsIndexed(categoryChannels) { index, channel ->
                            val isFocused = (focusColumn == TvFocusColumn.CHANNEL && channelIndex == index)
                            val isPlaying = (currentPlayingChannel?.id == channel.id)
                            val (currentProg, _) = viewModel.getCurrentAndNextEpg(channel)

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        when {
                                            isFocused -> Color(0xCC0D2845)
                                            isPlaying -> Color(0x661A365D)
                                            else -> Color.Transparent
                                        }
                                    )
                                    .border(
                                        width = if (isFocused) 2.dp else if (isPlaying) 1.dp else 0.dp,
                                        color = when {
                                            isFocused -> Color(0xFF00B0FF)
                                            isPlaying -> Color(0x880091EA)
                                            else -> Color.Transparent
                                        },
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable {
                                        viewModel.playChannel(channel)
                                    }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Play icon
                                    if (isFocused || isPlaying) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            tint = Color(0xFFE53935),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${channel.number}. ${channel.name}",
                                            color = if (isFocused || isPlaying) Color.White else Color(0xFFCBD5E1),
                                            fontSize = 16.sp,
                                            fontWeight = if (isFocused || isPlaying) FontWeight.Bold else FontWeight.Normal,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        // Only display real program title if XMLTV EPG is present (no mock data)
                                        if (currentProg != null) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = currentProg.title,
                                                color = if (isFocused) Color(0xFFB0BEC5) else Color(0xFF64748B),
                                                fontSize = 12.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    if (isFocused) {
                                        Text(
                                            text = "··>",
                                            color = Color(0xFF00E5FF),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    IconButton(
                                        onClick = { viewModel.toggleFavorite(channel.id) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (channel.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                            contentDescription = "收藏",
                                            tint = if (channel.isFavorite) Color(0xFFE53935) else Color(0xFF4A5568),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Right Area (~50%): Video preview info overlays
                // Clicking anywhere on this right area hides the left category or overlay!
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(if (isCategoryColumnVisible) 0.50f else 0.62f)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) {
                            viewModel.onRightAreaClick()
                        }
                        .padding(24.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Top header: Channel info & Clock
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        currentPlayingChannel?.let { ch ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFF00E5FF))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = "${ch.number}",
                                        color = Color.Black,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = ch.name,
                                    color = Color.White,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                // Real stream parameters badge (strictly based on actual video, NO hardcoded 1080P)
                                if (streamMediaInfo.height > 0) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(
                                                when {
                                                    streamMediaInfo.is4K -> Color(0xFFAB47BC).copy(alpha = 0.25f)
                                                    streamMediaInfo.is1080P -> Color(0xFF00E5FF).copy(alpha = 0.2f)
                                                    streamMediaInfo.is720P -> Color(0xFF29B6F6).copy(alpha = 0.2f)
                                                    else -> Color(0xFF78909C).copy(alpha = 0.2f)
                                                }
                                            )
                                            .border(
                                                1.dp,
                                                when {
                                                    streamMediaInfo.is4K -> Color(0xFFAB47BC)
                                                    streamMediaInfo.is1080P -> Color(0xFF00E5FF)
                                                    streamMediaInfo.is720P -> Color(0xFF29B6F6)
                                                    else -> Color(0xFF78909C)
                                                },
                                                RoundedCornerShape(4.dp)
                                            )
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = streamMediaInfo.resolutionLabel,
                                            color = when {
                                                streamMediaInfo.is4K -> Color(0xFFE1BEE7)
                                                streamMediaInfo.is1080P -> Color(0xFF00E5FF)
                                                streamMediaInfo.is720P -> Color(0xFF29B6F6)
                                                else -> Color(0xFFCFD8DC)
                                            },
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    // Real Codec & FPS if known
                                    if (streamMediaInfo.videoCodec.isNotBlank()) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .border(1.dp, Color(0xFF4CAF50), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 5.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "${streamMediaInfo.videoCodec.uppercase()}${if (streamMediaInfo.frameRate > 0) " ${streamMediaInfo.frameRate.toInt()}fps" else ""}",
                                                color = Color(0xFF4CAF50),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    // Real resolution digits e.g. 3840x2160
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "${streamMediaInfo.width}x${streamMediaInfo.height}",
                                        color = Color(0xFF8B9CB5),
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        } ?: Spacer(modifier = Modifier.width(1.dp))

                        // Clock
                        Text(
                            text = currentTimeString,
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Middle status if loading or error
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        when (val status = playbackStatus) {
                            is PlaybackStatus.Loading -> {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xCC000000)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            color = Color(0xFF00E5FF),
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(status.message, color = Color.White, fontSize = 13.sp)
                                    }
                                }
                            }
                            is PlaybackStatus.Buffering -> {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xCC000000)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "缓冲中: ${status.percent.toInt()}% (8000ms超强缓冲)",
                                        color = Color(0xFF4FC3F7),
                                        fontSize = 13.sp,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                    )
                                }
                            }
                            is PlaybackStatus.Error -> {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xEE4A1515)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(status.errorMessage, color = Color(0xFFFF8A80), fontSize = 13.sp)
                                        if (status.retryCountdown > 0) {
                                            Text("${status.retryCountdown} 秒后自动重新连接...", color = Color.White, fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                            else -> {}
                        }
                    }

                    // Bottom info: Program info (only if XMLTV is imported) + remote shortcuts hint
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xCC0B0E14))
                            .border(1.dp, Color(0xFF1E2530), RoundedCornerShape(12.dp))
                            .padding(16.dp)
                    ) {
                        val (currentProg, nextProg) = viewModel.getCurrentAndNextEpg(currentPlayingChannel)

                        if (currentProg != null) {
                            // Current program from real XMLTV EPG
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "正在播出: ${currentProg.title}",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "EPG 已同步",
                                    color = Color(0xFF00E5FF),
                                    fontSize = 12.sp
                                )
                            }

                            // Progress bar
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { currentProg.progress() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = Color(0xFF00E5FF),
                                trackColor = Color(0xFF232D3B)
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            // Next program
                            Text(
                                text = "稍后播出: ${nextProg?.title ?: "无后续节目单"}",
                                color = Color(0xFF8B9CB5),
                                fontSize = 12.sp
                            )
                        } else {
                            // Empty-shell app display when no EPG is imported
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "节目单: 暂无 EPG 节目信息 (空壳播放器)",
                                    color = Color(0xFF8B9CB5),
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = "可通过设置导入 XMLTV",
                                    color = Color(0xFF546E7A),
                                    fontSize = 11.sp
                                )
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = Color(0xFF1E2530),
                            thickness = 1.dp
                        )

                        // Remote key hints
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("[OK] 全屏/切换", color = Color(0xFF8B9CB5), fontSize = 11.sp)
                            Text("[点击右侧/返回] 隐藏菜单", color = Color(0xFF8B9CB5), fontSize = 11.sp)
                            Text("[菜单/长按OK] 设置", color = Color(0xFF8B9CB5), fontSize = 11.sp)
                            Text("[数字键] 换台", color = Color(0xFF8B9CB5), fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // Fullscreen touch/remote tap detector to bring back overlay
        if (isFullscreen && !isChannelOverlayVisible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { viewModel.toggleFullscreenOrOverlay() }
            )
        }

        // On-screen channel number input OSD (e.g. [ 1 5 4 ])
        ChannelNumberOsd(
            number = numberInput,
            modifier = Modifier.align(Alignment.TopEnd)
        )

        // Toast message overlay
        toastMessage?.let { msg ->
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xEE1E2530))
                    .border(1.dp, Color(0xFF00E5FF), RoundedCornerShape(8.dp))
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(text = msg, color = Color.White, fontSize = 14.sp)
            }
        }

        // Settings Dialog
        if (isSettingsOpen) {
            SettingsDialog(
                viewModel = viewModel,
                onDismiss = { viewModel.closeSettings() }
            )
        }

        // TV Local File Picker Dialog (Built-in, works on all TV boxes)
        if (isTvFilePickerOpen) {
            TvFilePickerDialog(
                onDismiss = { viewModel.closeTvFilePicker() },
                onFileSelected = { file ->
                    viewModel.closeTvFilePicker()
                    viewModel.importM3uFromFile(file)
                }
            )
        }

        // Dedicated VLC Test Player Screen (overlay on top of live video)
        if (isTestPlayerOpen) {
            VlcTestPlayerView(
                playerManager = viewModel.playerManager,
                onBack = { viewModel.closeTestPlayer() },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Exit confirmation dialog
        if (isExitConfirmOpen) {
            val activity = LocalContext.current as? android.app.Activity
            AlertDialog(
                onDismissRequest = { viewModel.dismissExitDialog() },
                title = { Text("退出提示", color = Color.White) },
                text = { Text("确定要退出 DIYP 电视直播 吗？", color = Color(0xFFCBD5E1)) },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.playerManager.release()
                            activity?.finishAffinity() ?: System.exit(0)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                    ) {
                        Text("退出应用", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissExitDialog() }) {
                        Text("继续观看", color = Color(0xFF00E5FF))
                    }
                },
                containerColor = Color(0xFF141922)
            )
        }
    }
}
