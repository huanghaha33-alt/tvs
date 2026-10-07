package com.example.ui

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.DefaultChannels
import com.example.data.M3uParser
import com.example.data.PreferenceManager
import com.example.data.XmlTvParser
import com.example.model.*
import com.example.player.VlcPlayerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

enum class TvFocusColumn {
    CATEGORY,
    CHANNEL,
    VIDEO
}

class IptvViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "IptvViewModel"
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 9; TV) AppleWebKit/537.36 (KHTML, like Gecko) VLC/3.6.3"
    }

    private val prefManager = PreferenceManager(application)
    val playerManager = VlcPlayerManager(application)

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    // Channels & Categories
    private val _allChannels = MutableStateFlow<List<Channel>>(emptyList())
    val allChannels: StateFlow<List<Channel>> = _allChannels.asStateFlow()

    private val _categories = MutableStateFlow<List<Category>>(emptyList())
    val categories: StateFlow<List<Category>> = _categories.asStateFlow()

    private val _selectedCategory = MutableStateFlow<String>("卫视频道")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _categoryChannels = MutableStateFlow<List<Channel>>(emptyList())
    val categoryChannels: StateFlow<List<Channel>> = _categoryChannels.asStateFlow()

    private val _highlightedChannel = MutableStateFlow<Channel?>(null)
    val highlightedChannel: StateFlow<Channel?> = _highlightedChannel.asStateFlow()

    private val _currentPlayingChannel = MutableStateFlow<Channel?>(null)
    val currentPlayingChannel: StateFlow<Channel?> = _currentPlayingChannel.asStateFlow()

    // Navigation and Focus
    private val _focusColumn = MutableStateFlow(TvFocusColumn.CHANNEL)
    val focusColumn: StateFlow<TvFocusColumn> = _focusColumn.asStateFlow()

    private val _categoryIndex = MutableStateFlow(0)
    val categoryIndex: StateFlow<Int> = _categoryIndex.asStateFlow()

    private val _channelIndex = MutableStateFlow(0)
    val channelIndex: StateFlow<Int> = _channelIndex.asStateFlow()

    // UI overlays
    private val _isFullscreen = MutableStateFlow(false)
    val isFullscreen: StateFlow<Boolean> = _isFullscreen.asStateFlow()

    private val _isChannelOverlayVisible = MutableStateFlow(true)
    val isChannelOverlayVisible: StateFlow<Boolean> = _isChannelOverlayVisible.asStateFlow()

    private val _isCategoryColumnVisible = MutableStateFlow(true)
    val isCategoryColumnVisible: StateFlow<Boolean> = _isCategoryColumnVisible.asStateFlow()

    private val _isSettingsOpen = MutableStateFlow(false)
    val isSettingsOpen: StateFlow<Boolean> = _isSettingsOpen.asStateFlow()

    private val _isTvFilePickerOpen = MutableStateFlow(false)
    val isTvFilePickerOpen: StateFlow<Boolean> = _isTvFilePickerOpen.asStateFlow()

    private val _isTestPlayerOpen = MutableStateFlow(false)
    val isTestPlayerOpen: StateFlow<Boolean> = _isTestPlayerOpen.asStateFlow()

    private val _isExitConfirmOpen = MutableStateFlow(false)
    val isExitConfirmOpen: StateFlow<Boolean> = _isExitConfirmOpen.asStateFlow()

    // Import status
    private val _isImportingM3u = MutableStateFlow(false)
    val isImportingM3u: StateFlow<Boolean> = _isImportingM3u.asStateFlow()

    // Number key OSD buffer (e.g. 154)
    private val _numberInput = MutableStateFlow("")
    val numberInput: StateFlow<String> = _numberInput.asStateFlow()
    private var numberInputJob: Job? = null

    // Channel EPG cache
    private val _epgMap = MutableStateFlow<Map<String, List<EpgProgram>>>(emptyMap())
    val epgMap: StateFlow<Map<String, List<EpgProgram>>> = _epgMap.asStateFlow()

    // Status toast or notification
    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    // Settings
    private val _playerSettings = MutableStateFlow(prefManager.loadSettings())
    val playerSettings: StateFlow<PlayerSettings> = _playerSettings.asStateFlow()

    init {
        loadChannelsAndSettings()
    }

    private fun loadChannelsAndSettings() {
        val settings = prefManager.loadSettings()
        _playerSettings.value = settings
        playerManager.setNetworkCaching(settings.cacheMode)
        playerManager.setHardwareAcceleration(settings.hardwareAcceleration)
        playerManager.setAspectRatio(settings.aspectRatio)

        val channels = if (prefManager.hasCustomM3u()) {
            val stream = prefManager.openCustomM3uInputStream()
            val parsed = stream?.let { M3uParser.parse(it) } ?: emptyList()
            if (parsed.isNotEmpty()) parsed else DefaultChannels.getInitialChannels()
        } else {
            DefaultChannels.getInitialChannels()
        }

        applyChannels(channels)

        // Restore last played channel or first channel
        val lastId = settings.lastChannelId
        val target = _allChannels.value.find { it.id == lastId }
            ?: _allChannels.value.find { it.name == "东方卫视" }
            ?: _allChannels.value.firstOrNull()

        target?.let { playChannel(it) }

        // Load custom M3U or EPG if configured
        if (settings.customM3uUrl.isNotBlank() && !prefManager.hasCustomM3u()) {
            importM3uFromUrl(settings.customM3uUrl, notify = false)
        }
        if (settings.customEpgUrl.isNotBlank()) {
            importEpgFromUrl(settings.customEpgUrl)
        }
    }

    private fun applyChannels(rawChannels: List<Channel>) {
        val favorites = prefManager.getFavorites()
        val updated = rawChannels.take(10000).map { ch ->
            ch.copy(isFavorite = favorites.contains(ch.id))
        }
        _allChannels.value = updated

        // Extract categories
        val catMap = linkedMapOf<String, Int>()
        val favCount = updated.count { it.isFavorite }
        if (favCount > 0) {
            catMap["我的收藏"] = favCount
        }

        updated.forEach { ch ->
            val grp = if (ch.group.isNotBlank()) ch.group else "其他频道"
            catMap[grp] = (catMap[grp] ?: 0) + 1
        }

        val catList = catMap.map { (name, count) ->
            Category(id = name, name = name, channelCount = count)
        }
        _categories.value = catList

        // Default to "卫视频道" if present, otherwise first
        val defaultCat = catList.find { it.name == "卫视频道" }?.name
            ?: catList.firstOrNull()?.name
            ?: "所有频道"
        selectCategory(defaultCat)
    }

    fun selectCategory(categoryName: String) {
        _selectedCategory.value = categoryName
        val catIndex = _categories.value.indexOfFirst { it.name == categoryName }.coerceAtLeast(0)
        _categoryIndex.value = catIndex

        val channels = if (categoryName == "我的收藏") {
            _allChannels.value.filter { it.isFavorite }
        } else {
            _allChannels.value.filter { it.group == categoryName }
        }
        _categoryChannels.value = channels

        if (channels.isNotEmpty()) {
            _channelIndex.value = 0
            _highlightedChannel.value = channels[0]
        } else {
            _channelIndex.value = 0
            _highlightedChannel.value = null
        }
    }

    fun playChannel(channel: Channel) {
        _currentPlayingChannel.value = channel
        _highlightedChannel.value = channel
        prefManager.saveLastChannelId(channel.id)

        // Sync category and channel indexes
        val cat = if (_selectedCategory.value == "我的收藏" && channel.isFavorite) {
            "我的收藏"
        } else {
            channel.group
        }
        if (_selectedCategory.value != cat) {
            selectCategory(cat)
        }
        val idx = _categoryChannels.value.indexOfFirst { it.id == channel.id }
        if (idx != -1) {
            _channelIndex.value = idx
        }

        playerManager.play(channel.url)
    }

    fun toggleFavorite(channelId: String) {
        val target = _allChannels.value.find { it.id == channelId } ?: return
        val newFav = !target.isFavorite
        prefManager.setFavorite(channelId, newFav)

        applyChannels(_allChannels.value)
        showToast(if (newFav) "已添加到收藏: ${target.name}" else "已取消收藏: ${target.name}")
    }

    fun getCurrentAndNextEpg(channel: Channel?): Pair<EpgProgram?, EpgProgram?> {
        if (channel == null) return Pair(null, null)
        val customPrograms = _epgMap.value[channel.tvgId] ?: _epgMap.value[channel.name]
        if (customPrograms.isNullOrEmpty()) {
            return Pair(null, null)
        }

        val now = System.currentTimeMillis()
        val current = customPrograms.find { it.isCurrent(now) }
        val next = if (current != null) {
            customPrograms.find { it.startTime >= current.endTime }
        } else {
            customPrograms.find { it.startTime > now }
        }
        return Pair(current, next)
    }

    // Remote D-Pad Navigation Handlers
    fun onDpadUp() {
        if (_focusColumn.value == TvFocusColumn.CATEGORY) {
            val list = _categories.value
            if (list.isNotEmpty()) {
                val newIdx = (_categoryIndex.value - 1 + list.size) % list.size
                _categoryIndex.value = newIdx
                selectCategory(list[newIdx].name)
            }
        } else if (_focusColumn.value == TvFocusColumn.CHANNEL) {
            val list = _categoryChannels.value
            if (list.isNotEmpty()) {
                val newIdx = (_channelIndex.value - 1 + list.size) % list.size
                _channelIndex.value = newIdx
                _highlightedChannel.value = list[newIdx]
            }
        } else if (_focusColumn.value == TvFocusColumn.VIDEO) {
            switchToPrevChannel()
        }
    }

    fun onDpadDown() {
        if (_focusColumn.value == TvFocusColumn.CATEGORY) {
            val list = _categories.value
            if (list.isNotEmpty()) {
                val newIdx = (_categoryIndex.value + 1) % list.size
                _categoryIndex.value = newIdx
                selectCategory(list[newIdx].name)
            }
        } else if (_focusColumn.value == TvFocusColumn.CHANNEL) {
            val list = _categoryChannels.value
            if (list.isNotEmpty()) {
                val newIdx = (_channelIndex.value + 1) % list.size
                _channelIndex.value = newIdx
                _highlightedChannel.value = list[newIdx]
            }
        } else if (_focusColumn.value == TvFocusColumn.VIDEO) {
            switchToNextChannel()
        }
    }

    fun switchToNextChannel() {
        val list = if (_categoryChannels.value.isNotEmpty()) _categoryChannels.value else _allChannels.value
        if (list.isEmpty()) return
        val current = _currentPlayingChannel.value
        val currentIndex = list.indexOfFirst { it.id == current?.id }
        val nextIndex = (currentIndex + 1) % list.size
        val next = list[nextIndex]
        showToast("切换至 [${next.number}] ${next.name}")
        playChannel(next)
    }

    fun switchToPrevChannel() {
        val list = if (_categoryChannels.value.isNotEmpty()) _categoryChannels.value else _allChannels.value
        if (list.isEmpty()) return
        val current = _currentPlayingChannel.value
        val currentIndex = list.indexOfFirst { it.id == current?.id }
        val prevIndex = (currentIndex - 1 + list.size) % list.size
        val prev = list[prevIndex]
        showToast("切换至 [${prev.number}] ${prev.name}")
        playChannel(prev)
    }

    fun hideCategoryColumn() {
        _isCategoryColumnVisible.value = false
        if (_focusColumn.value == TvFocusColumn.CATEGORY) {
            _focusColumn.value = TvFocusColumn.CHANNEL
        }
    }

    fun showCategoryColumn() {
        _isCategoryColumnVisible.value = true
    }

    fun toggleCategoryColumn() {
        _isCategoryColumnVisible.value = !_isCategoryColumnVisible.value
        if (!_isCategoryColumnVisible.value && _focusColumn.value == TvFocusColumn.CATEGORY) {
            _focusColumn.value = TvFocusColumn.CHANNEL
        }
    }

    fun onRightAreaClick() {
        if (_isCategoryColumnVisible.value) {
            hideCategoryColumn()
        } else if (_isChannelOverlayVisible.value) {
            hideOverlay()
        } else {
            toggleFullscreenOrOverlay()
        }
    }

    fun hideOverlay() {
        _isFullscreen.value = true
        _isChannelOverlayVisible.value = false
        _focusColumn.value = TvFocusColumn.VIDEO
    }

    fun showOverlay() {
        _isChannelOverlayVisible.value = true
        _focusColumn.value = TvFocusColumn.CHANNEL
    }

    fun onDpadLeft() {
        if (_focusColumn.value == TvFocusColumn.CHANNEL) {
            if (!_isCategoryColumnVisible.value) {
                _isCategoryColumnVisible.value = true
            }
            _focusColumn.value = TvFocusColumn.CATEGORY
        } else if (_focusColumn.value == TvFocusColumn.VIDEO) {
            _focusColumn.value = TvFocusColumn.CHANNEL
            _isChannelOverlayVisible.value = true
        }
    }

    fun onDpadRight() {
        if (_focusColumn.value == TvFocusColumn.CATEGORY) {
            _focusColumn.value = TvFocusColumn.CHANNEL
        } else if (_focusColumn.value == TvFocusColumn.CHANNEL) {
            hideOverlay()
        }
    }

    fun onDpadCenter() {
        when (_focusColumn.value) {
            TvFocusColumn.CATEGORY -> {
                _focusColumn.value = TvFocusColumn.CHANNEL
            }
            TvFocusColumn.CHANNEL -> {
                _highlightedChannel.value?.let { channel ->
                    playChannel(channel)
                }
            }
            TvFocusColumn.VIDEO -> {
                toggleFullscreenOrOverlay()
            }
        }
    }

    fun toggleFullscreenOrOverlay() {
        if (!_isFullscreen.value) {
            _isFullscreen.value = true
            _isChannelOverlayVisible.value = false
            _focusColumn.value = TvFocusColumn.VIDEO
        } else {
            val willShow = !_isChannelOverlayVisible.value
            _isChannelOverlayVisible.value = willShow
            if (willShow) {
                _focusColumn.value = TvFocusColumn.CHANNEL
            }
        }
    }

    fun onBackPress(): Boolean {
        if (_isTvFilePickerOpen.value) {
            _isTvFilePickerOpen.value = false
            return true
        }
        if (_isSettingsOpen.value) {
            _isSettingsOpen.value = false
            return true
        }
        if (_isTestPlayerOpen.value) {
            _isTestPlayerOpen.value = false
            return true
        }
        if (_isExitConfirmOpen.value) {
            _isExitConfirmOpen.value = false
            return true
        }

        // If category is visible and user presses back, hide category column
        if (_isCategoryColumnVisible.value && !_isFullscreen.value) {
            hideCategoryColumn()
            return true
        }

        if (_isFullscreen.value && _isChannelOverlayVisible.value) {
            _isChannelOverlayVisible.value = false
            _focusColumn.value = TvFocusColumn.VIDEO
            return true
        }

        if (_isFullscreen.value && !_isChannelOverlayVisible.value) {
            _isFullscreen.value = false
            _isChannelOverlayVisible.value = true
            _focusColumn.value = TvFocusColumn.CHANNEL
            return true
        }

        _isExitConfirmOpen.value = true
        return true
    }

    fun onNumberInput(digit: Int) {
        val current = _numberInput.value + digit
        _numberInput.value = current

        numberInputJob?.cancel()
        numberInputJob = viewModelScope.launch {
            delay(2000)
            commitNumberInput()
        }
    }

    fun commitNumberInput() {
        val input = _numberInput.value
        _numberInput.value = ""
        val targetNo = input.toIntOrNull() ?: return

        val found = _allChannels.value.find { it.number == targetNo }
        if (found != null) {
            showToast("正在切换至 [${found.number}] ${found.name}")
            playChannel(found)
        } else {
            showToast("未找到频道: $input")
        }
    }

    // Settings actions
    fun setCacheMode(mode: CacheMode) {
        prefManager.saveCacheMode(mode)
        _playerSettings.value = _playerSettings.value.copy(cacheMode = mode)
        playerManager.setNetworkCaching(mode)
        showToast("已设为: ${mode.displayName}")
    }

    fun setHardwareAcceleration(enabled: Boolean) {
        prefManager.saveHardwareAcceleration(enabled)
        _playerSettings.value = _playerSettings.value.copy(hardwareAcceleration = enabled)
        playerManager.setHardwareAcceleration(enabled)
        showToast(if (enabled) "已开启硬件加速解码" else "已切换为纯软件解码")
    }

    fun setAspectRatio(mode: AspectRatioMode) {
        prefManager.saveAspectRatio(mode)
        _playerSettings.value = _playerSettings.value.copy(aspectRatio = mode)
        playerManager.setAspectRatio(mode)
        showToast("画面比例: ${mode.displayName}")
    }

    fun openSettings() {
        _isSettingsOpen.value = true
    }

    fun closeSettings() {
        _isSettingsOpen.value = false
    }

    fun openTvFilePicker() {
        _isTvFilePickerOpen.value = true
    }

    fun closeTvFilePicker() {
        _isTvFilePickerOpen.value = false
    }

    fun openTestPlayer() {
        _isTestPlayerOpen.value = true
    }

    fun closeTestPlayer() {
        _isTestPlayerOpen.value = false
    }

    fun dismissExitDialog() {
        _isExitConfirmOpen.value = false
    }

    /**
     * Import directly from local File (e.g. from built-in TV file browser)
     */
    fun importM3uFromFile(file: File) {
        viewModelScope.launch {
            _isImportingM3u.value = true
            try {
                showToast("正在读取文件: ${file.name}")
                withContext(Dispatchers.IO) {
                    FileInputStream(file).use { fis ->
                        prefManager.saveCustomM3uStream(fis)
                    }
                }

                val channels = withContext(Dispatchers.IO) {
                    val stream = prefManager.openCustomM3uInputStream()
                    stream?.let { M3uParser.parse(it) } ?: emptyList()
                }

                if (channels.isNotEmpty()) {
                    applyChannels(channels)
                    channels.firstOrNull()?.let { playChannel(it) }
                    showToast("导入成功！共解析出 ${channels.size} 个频道")
                } else {
                    showToast("文件解析为空或没有找到有效直播源")
                }
            } catch (t: Throwable) {
                Log.e(TAG, "File import error", t)
                showToast("导入失败: ${t.localizedMessage ?: t.message}")
            } finally {
                _isImportingM3u.value = false
            }
        }
    }

    /**
     * Resilient M3U Import:
     * - Handles HTTP / HTTPS URLs
     * - Handles local absolute paths (/sdcard/..., /storage/...)
     * - Handles file:// URIs
     */
    fun importM3uFromUrl(rawInput: String, notify: Boolean = true) {
        val input = rawInput.trim()
        if (input.isBlank()) {
            if (notify) showToast("请输入有效的 M3U 链接或文件路径")
            return
        }

        viewModelScope.launch {
            _isImportingM3u.value = true
            try {
                if (notify) showToast("正在解析导入 M3U 播放列表...")

                // 1. Check if user provided a local file path
                if (input.startsWith("/") || input.startsWith("file://")) {
                    val filePath = if (input.startsWith("file://")) input.substring(7) else input
                    val localFile = File(filePath)
                    if (!localFile.exists() || !localFile.canRead()) {
                        if (notify) showToast("本地文件不存在或无读取权限: $filePath")
                        _isImportingM3u.value = false
                        return@launch
                    }

                    withContext(Dispatchers.IO) {
                        FileInputStream(localFile).use { fis ->
                            prefManager.saveCustomM3uStream(fis)
                        }
                    }
                } else {
                    // 2. Network URL: Normalize URL scheme
                    val targetUrl = when {
                        input.startsWith("http://", ignoreCase = true) -> input
                        input.startsWith("https://", ignoreCase = true) -> input
                        else -> "http://$input"
                    }

                    withContext(Dispatchers.IO) {
                        val request = Request.Builder()
                            .url(targetUrl)
                            .header("User-Agent", USER_AGENT)
                            .header("Accept", "*/*")
                            .build()

                        val response = okHttpClient.newCall(request).execute()
                        if (!response.isSuccessful) {
                            throw Exception("HTTP ${response.code} (${response.message})")
                        }

                        val body = response.body ?: throw Exception("响应体为空")
                        body.byteStream().use { stream ->
                            prefManager.saveCustomM3uStream(stream)
                        }
                    }
                    prefManager.saveCustomM3uUrl(targetUrl)
                }

                // 3. Parse from saved private file
                val channels = withContext(Dispatchers.IO) {
                    val stream = prefManager.openCustomM3uInputStream()
                    stream?.let { M3uParser.parse(it) } ?: emptyList()
                }

                if (channels.isNotEmpty()) {
                    applyChannels(channels)
                    channels.firstOrNull()?.let { playChannel(it) }
                    if (notify) showToast("M3U 导入成功！共解析出 ${channels.size} 个频道")
                } else {
                    if (notify) showToast("M3U 导入失败：未找到有效频道")
                }
            } catch (t: Throwable) {
                Log.e(TAG, "M3U import error", t)
                if (notify) showToast("导入失败: ${t.localizedMessage ?: t.message ?: "未知异常"}")
            } finally {
                _isImportingM3u.value = false
            }
        }
    }

    /**
     * Import directly from SAF Document Uri (e.g. from external file provider)
     */
    fun importM3uFromFileUri(uri: Uri) {
        viewModelScope.launch {
            _isImportingM3u.value = true
            try {
                showToast("正在读取所选文件...")
                val context = getApplication<Application>()
                val inputStream: InputStream? = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)
                }

                if (inputStream == null) {
                    showToast("无法打开所选文件")
                    _isImportingM3u.value = false
                    return@launch
                }

                withContext(Dispatchers.IO) {
                    inputStream.use { stream ->
                        prefManager.saveCustomM3uStream(stream)
                    }
                }

                val channels = withContext(Dispatchers.IO) {
                    val stream = prefManager.openCustomM3uInputStream()
                    stream?.let { M3uParser.parse(it) } ?: emptyList()
                }

                if (channels.isNotEmpty()) {
                    applyChannels(channels)
                    channels.firstOrNull()?.let { playChannel(it) }
                    showToast("本地 M3U 导入成功！共 ${channels.size} 个频道")
                } else {
                    showToast("本地文件解析为空或格式不正确")
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Local file import error", t)
                showToast("文件读取失败: ${t.localizedMessage ?: t.message}")
            } finally {
                _isImportingM3u.value = false
            }
        }
    }

    fun importM3uFromContent(content: String) {
        if (content.isBlank()) {
            showToast("内容为空")
            return
        }
        viewModelScope.launch {
            _isImportingM3u.value = true
            try {
                withContext(Dispatchers.IO) {
                    prefManager.saveCustomM3uContent(content)
                }

                val channels = withContext(Dispatchers.IO) {
                    val stream = prefManager.openCustomM3uInputStream()
                    stream?.let { M3uParser.parse(it) } ?: emptyList()
                }

                if (channels.isNotEmpty()) {
                    applyChannels(channels)
                    channels.firstOrNull()?.let { playChannel(it) }
                    showToast("文本解析成功！共导入 ${channels.size} 个频道")
                } else {
                    showToast("未解析到有效频道，请检查内容")
                }
            } catch (t: Throwable) {
                showToast("解析出错: ${t.localizedMessage ?: t.message}")
            } finally {
                _isImportingM3u.value = false
            }
        }
    }

    fun importEpgFromUrl(rawUrl: String) {
        val input = rawUrl.trim()
        if (input.isBlank()) return
        viewModelScope.launch {
            try {
                showToast("正在下载 XMLTV EPG...")
                val targetUrl = when {
                    input.startsWith("http://", ignoreCase = true) || input.startsWith("https://", ignoreCase = true) -> input
                    else -> "http://$input"
                }

                val content = withContext(Dispatchers.IO) {
                    val req = Request.Builder()
                        .url(targetUrl)
                        .header("User-Agent", USER_AGENT)
                        .build()
                    val resp = okHttpClient.newCall(req).execute()
                    if (resp.isSuccessful) {
                        resp.body?.string() ?: ""
                    } else {
                        throw Exception("HTTP ${resp.code}")
                    }
                }

                if (content.isNotBlank()) {
                    val parsed = XmlTvParser.parse(content)
                    _epgMap.value = parsed
                    prefManager.saveCustomEpgUrl(targetUrl)
                    showToast("EPG 节目单更新成功！")
                }
            } catch (t: Throwable) {
                showToast("EPG 下载失败: ${t.localizedMessage ?: t.message}")
            }
        }
    }

    fun resetToDefaultChannels() {
        prefManager.clearCustomM3u()
        prefManager.saveCustomM3uUrl("")
        _playerSettings.value = _playerSettings.value.copy(customM3uUrl = "")
        val initial = DefaultChannels.getInitialChannels()
        applyChannels(initial)
        initial.firstOrNull()?.let { playChannel(it) }
        showToast("已恢复默认内置频道")
    }

    fun showToast(message: String) {
        viewModelScope.launch {
            _toastMessage.value = message
            delay(2500)
            if (_toastMessage.value == message) {
                _toastMessage.value = null
            }
        }
    }
}
