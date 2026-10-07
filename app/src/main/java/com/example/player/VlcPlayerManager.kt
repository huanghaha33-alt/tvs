package com.example.player

import android.content.Context
import android.net.Uri
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.example.model.AspectRatioMode
import com.example.model.CacheMode
import com.example.model.PlaybackStatus
import com.example.model.StreamMediaInfo
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IVLCVout

class VlcPlayerManager(private val context: Context) : IVLCVout.Callback {

    companion object {
        private const val TAG = "VlcPlayerManager"
        private const val MAX_RETRY_COUNT = 3
    }

    private var libVLC: LibVLC? = null
    private var mediaPlayer: MediaPlayer? = null
    private var currentSurfaceView: SurfaceView? = null
    private var isSurfaceAttached = false

    private val _playbackStatus = MutableStateFlow<PlaybackStatus>(PlaybackStatus.Idle)
    val playbackStatus: StateFlow<PlaybackStatus> = _playbackStatus.asStateFlow()

    private val _currentUrl = MutableStateFlow<String>("")
    val currentUrl: StateFlow<String> = _currentUrl.asStateFlow()

    private val _streamMediaInfo = MutableStateFlow(StreamMediaInfo())
    val streamMediaInfo: StateFlow<StreamMediaInfo> = _streamMediaInfo.asStateFlow()

    private val _logMessages = MutableStateFlow<List<String>>(emptyList())
    val logMessages: StateFlow<List<String>> = _logMessages.asStateFlow()

    var cacheMode: CacheMode = CacheMode.ULTRA_4K
        private set
    var hardwareAcceleration: Boolean = true
        private set
    var aspectRatioMode: AspectRatioMode = AspectRatioMode.FIT_SCREEN
        private set

    private var switchChannelJob: Job? = null
    private var retryJob: Job? = null
    private var videoWatchdogJob: Job? = null
    private var retryCount = 0
    private var voutReady = false

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    init {
        initializeVlc()
    }

    private fun initializeVlc() {
        try {
            val options = ArrayList<String>()
            val cachingMs = cacheMode.cachingMs

            // Buffer parameters tuned for high bitrate 4K & IPTV stability (8000ms default)
            options.add("--network-caching=$cachingMs")
            options.add("--live-caching=$cachingMs")
            options.add("--file-caching=$cachingMs")
            options.add("--sout-mux-caching=$cachingMs")
            options.add("--mms-timeout=$cachingMs")
            options.add("--rtsp-tcp")
            options.add("--http-reconnect")
            options.add("--http-continuous")
            options.add("--no-video-title-show")
            options.add("--no-sub-autodetect-file")
            options.add("--no-stats")

            // Universal hardware acceleration across all Android chips (Amlogic, Rockchip, Qualcomm, MTK)
            if (hardwareAcceleration) {
                options.add("--codec=mediacodec_jni,mediacodec_ndk,all")
                options.add("--avcodec-hw=any")
            } else {
                options.add("--codec=all")
            }

            // Multi-threaded decoding to smooth out 4K H.265 / HEVC decoding
            options.add("--avcodec-threads=0")

            releasePlayer()
            libVLC?.release()
            libVLC = LibVLC(context, options)
            setupMediaPlayer()
            log("LibVLC 初始化成功 (硬解=$hardwareAcceleration, 缓冲=${cacheMode.displayName})")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing LibVLC", e)
            log("LibVLC 初始化失败: ${e.message}")
        }
    }

    private fun setupMediaPlayer() {
        val vlc = libVLC ?: return
        try {
            mediaPlayer = MediaPlayer(vlc).apply {
                setEventListener { event ->
                    handleVlcEvent(event)
                }
            }
            isSurfaceAttached = false
            attachSurfaceIfPossible()
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up MediaPlayer", e)
        }
    }

    private fun handleVlcEvent(event: MediaPlayer.Event) {
        when (event.type) {
            MediaPlayer.Event.Buffering -> {
                val buff = event.buffering
                _playbackStatus.value = PlaybackStatus.Buffering(buff)
                if (buff < 100f && buff.toInt() % 25 == 0) {
                    log("4K流缓冲中: ${buff.toInt()}%")
                }
            }
            MediaPlayer.Event.Playing -> {
                retryCount = 0
                updateRealStreamMediaInfo()

                val info = _streamMediaInfo.value
                val w = if (info.width > 0) info.width else 1920
                val h = if (info.height > 0) info.height else 1080
                _playbackStatus.value = PlaybackStatus.Playing(w, h)
                applyAspectRatio(aspectRatioMode)
                log("播放开始: 实际分辨率 ${w}x${h} (${info.resolutionLabel})")

                startVideoWatchdog()
            }
            MediaPlayer.Event.Vout -> {
                val voutCount = event.voutCount
                if (voutCount > 0) {
                    voutReady = true
                    videoWatchdogJob?.cancel()
                    updateRealStreamMediaInfo()
                    applyAspectRatio(aspectRatioMode)
                    log("视频输出就绪: Vout $voutCount, 实际分辨率 ${_streamMediaInfo.value.width}x${_streamMediaInfo.value.height}")
                }
            }
            MediaPlayer.Event.Stopped -> {
                voutReady = false
                videoWatchdogJob?.cancel()
                _streamMediaInfo.value = StreamMediaInfo()
                log("播放停止")
            }
            MediaPlayer.Event.Paused -> {
                log("播放暂停")
            }
            MediaPlayer.Event.EncounteredError -> {
                voutReady = false
                videoWatchdogJob?.cancel()
                Log.w(TAG, "VLC encountered error on: ${_currentUrl.value}")
                log("直播流异常或中断，准备重新连接...")
                handlePlaybackError("流连接中断或解码异常")
            }
            MediaPlayer.Event.EndReached -> {
                voutReady = false
                videoWatchdogJob?.cancel()
                log("直播流结束，尝试重连...")
                handlePlaybackError("直播流已断开")
            }
        }
    }

    private fun updateRealStreamMediaInfo() {
        val player = mediaPlayer ?: return
        try {
            val vTrack = player.currentVideoTrack
            if (vTrack != null && vTrack.width > 0 && vTrack.height > 0) {
                val fps = if (vTrack.frameRateDen > 0) {
                    vTrack.frameRateNum.toFloat() / vTrack.frameRateDen.toFloat()
                } else 0f

                _streamMediaInfo.value = StreamMediaInfo(
                    width = vTrack.width,
                    height = vTrack.height,
                    videoCodec = vTrack.codec ?: "",
                    audioCodec = "",
                    frameRate = fps
                )
            }
        } catch (_: Exception) {}
    }

    private fun startVideoWatchdog() {
        if (!hardwareAcceleration) return
        videoWatchdogJob?.cancel()
        videoWatchdogJob = scope.launch {
            delay(3000)
            if (!voutReady && _playbackStatus.value is PlaybackStatus.Playing) {
                log("检测到音频正常但画面未渲染，自动切换纯软解恢复画面...")
                val url = _currentUrl.value
                if (url.isNotBlank()) {
                    playInternal(url, isRetry = true, forceSoftwareDecode = true)
                }
            }
        }
    }

    private fun handlePlaybackError(errorMsg: String) {
        if (_currentUrl.value.isBlank()) {
            _playbackStatus.value = PlaybackStatus.Idle
            return
        }

        retryCount++
        retryJob?.cancel()
        retryJob = scope.launch {
            // Fast 50ms sub-second reconnect to mitigate msd_lite drop_slow_clients disconnects
            _playbackStatus.value = PlaybackStatus.Error(
                errorMessage = "$errorMsg (正在自动快速重连... 第 $retryCount 次)",
                retryCountdown = 0
            )
            delay(50)
            log("正在快速重连直播流 (避免被 msd_lite 超时影响)...")
            playInternal(_currentUrl.value, isRetry = true, forceSoftwareDecode = false)
        }
    }

    fun attachSurface(surfaceView: SurfaceView) {
        if (currentSurfaceView == surfaceView && isSurfaceAttached) return

        currentSurfaceView = surfaceView

        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                log("Surface 创建完成")
                isSurfaceAttached = false
                attachSurfaceIfPossible()
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                try {
                    mediaPlayer?.vlcVout?.setWindowSize(width, height)
                    applyAspectRatio(aspectRatioMode)
                } catch (_: Exception) {}
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                log("Surface 已销毁")
                detachSurface()
            }
        })

        if (surfaceView.holder.surface?.isValid == true) {
            attachSurfaceIfPossible()
        }
    }

    fun attachSurfaceIfPossible() {
        val player = mediaPlayer ?: return
        val surfaceView = currentSurfaceView ?: return

        try {
            if (surfaceView.holder.surface?.isValid == true && !isSurfaceAttached) {
                val vout = player.vlcVout
                if (!vout.areViewsAttached()) {
                    vout.setVideoView(surfaceView)
                    vout.removeCallback(this)
                    vout.addCallback(this)
                    vout.attachViews()
                }
                isSurfaceAttached = true
                applyAspectRatio(aspectRatioMode)
                log("视频 Surface 已成功居中绑定到 VLC")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error attaching surface", e)
        }
    }

    fun detachSurface() {
        try {
            mediaPlayer?.let { player ->
                val vout = player.vlcVout
                if (vout.areViewsAttached()) {
                    vout.removeCallback(this)
                    vout.detachViews()
                }
            }
        } catch (_: Exception) {}
        isSurfaceAttached = false
    }

    fun play(url: String, immediate: Boolean = false) {
        switchChannelJob?.cancel()
        retryJob?.cancel()
        videoWatchdogJob?.cancel()
        voutReady = false
        retryCount = 0

        val trimmedUrl = url.trim()
        if (trimmedUrl.isBlank()) {
            stop()
            return
        }

        _currentUrl.value = trimmedUrl
        _streamMediaInfo.value = StreamMediaInfo()

        if (immediate) {
            switchChannelJob = scope.launch {
                playInternal(trimmedUrl, isRetry = false)
            }
        } else {
            switchChannelJob = scope.launch {
                delay(120)
                playInternal(trimmedUrl, isRetry = false)
            }
        }
    }

    private suspend fun playInternal(url: String, isRetry: Boolean, forceSoftwareDecode: Boolean = false) {
        val vlc = libVLC ?: return
        try {
            _playbackStatus.value = PlaybackStatus.Loading(if (isRetry) "正在重连频道..." else "正在加载频道...")
            log("加载频道: $url")

            val player = mediaPlayer ?: return

            try {
                if (player.isPlaying) {
                    player.stop()
                }
                player.media = null
            } catch (_: Exception) {}

            delay(80)

            val uri = if (url.startsWith("/")) Uri.parse("file://$url") else Uri.parse(url)
            val useHw = if (forceSoftwareDecode) false else hardwareAcceleration
            val cachingMs = cacheMode.cachingMs

            val media = Media(vlc, uri).apply {
                setHWDecoderEnabled(useHw, false)
                addOption(":network-caching=$cachingMs")
                addOption(":live-caching=$cachingMs")
                addOption(":file-caching=$cachingMs")
                addOption(":sout-mux-caching=$cachingMs")
                addOption(":tcp-caching=$cachingMs")
                addOption(":mms-timeout=$cachingMs")
                addOption(":http-reconnect=true")
                addOption(":http-continuous=true")
                addOption(":adaptive-logic=highest")
                addOption(":adaptive-maxbuffer=60")
                addOption(":codec=mediacodec_jni,mediacodec_ndk,all")
                addOption(":avcodec-hw=any")
                addOption(":avcodec-threads=0")
                addOption(":no-video-title-show")
            }

            player.media = media
            media.release()

            attachSurfaceIfPossible()
            player.play()
        } catch (e: Exception) {
            Log.e(TAG, "Play error", e)
            log("播放启动异常: ${e.message}")
            handlePlaybackError("播放启动异常: ${e.message}")
        }
    }

    fun pause() {
        try {
            mediaPlayer?.pause()
            log("暂停播放")
        } catch (_: Exception) {}
    }

    fun resume() {
        try {
            mediaPlayer?.play()
            log("继续播放")
        } catch (_: Exception) {}
    }

    fun stop() {
        switchChannelJob?.cancel()
        retryJob?.cancel()
        videoWatchdogJob?.cancel()
        voutReady = false
        try {
            mediaPlayer?.stop()
            mediaPlayer?.media = null
        } catch (_: Exception) {}
        _playbackStatus.value = PlaybackStatus.Idle
        _streamMediaInfo.value = StreamMediaInfo()
        log("停止播放")
    }

    fun setNetworkCaching(mode: CacheMode) {
        if (cacheMode == mode) return
        cacheMode = mode
        log("设置网络缓冲: ${mode.displayName}")
        val currentPlayUrl = _currentUrl.value
        initializeVlc()
        if (currentPlayUrl.isNotBlank()) {
            play(currentPlayUrl, immediate = true)
        }
    }

    fun setHardwareAcceleration(enabled: Boolean) {
        if (hardwareAcceleration == enabled) return
        hardwareAcceleration = enabled
        log("设置硬件解码加速: $enabled")
        val currentPlayUrl = _currentUrl.value
        initializeVlc()
        if (currentPlayUrl.isNotBlank()) {
            play(currentPlayUrl, immediate = true)
        }
    }

    fun setAspectRatio(mode: AspectRatioMode) {
        aspectRatioMode = mode
        applyAspectRatio(mode)
    }

    /**
     * Fixed-position centered scaling:
     * Guarantees that the video is centered on the display, preserving proper aspect ratio.
     */
    private fun applyAspectRatio(mode: AspectRatioMode) {
        val player = mediaPlayer ?: return
        try {
            when (mode) {
                AspectRatioMode.FIT_SCREEN -> {
                    // Default centered aspect ratio fit
                    player.aspectRatio = null
                    player.scale = 0f
                }
                AspectRatioMode.RATIO_16_9 -> {
                    player.aspectRatio = "16:9"
                    player.scale = 0f
                }
                AspectRatioMode.RATIO_4_3 -> {
                    player.aspectRatio = "4:3"
                    player.scale = 0f
                }
                AspectRatioMode.FILL -> {
                    player.aspectRatio = null
                    player.scale = 0f
                }
            }
            log("画面比例与居中已更新: ${mode.displayName}")
        } catch (_: Exception) {}
    }

    private fun log(message: String) {
        Log.d(TAG, message)
        val current = _logMessages.value.toMutableList()
        current.add("[${System.currentTimeMillis() % 100000}] $message")
        if (current.size > 50) current.removeAt(0)
        _logMessages.value = current
    }

    override fun onSurfacesCreated(vlcVout: IVLCVout?) {
        log("IVLCVout surfaces created")
        voutReady = true
        applyAspectRatio(aspectRatioMode)
    }

    override fun onSurfacesDestroyed(vlcVout: IVLCVout?) {
        log("IVLCVout surfaces destroyed")
        voutReady = false
    }

    private fun releasePlayer() {
        detachSurface()
        try {
            mediaPlayer?.let {
                it.stop()
                it.release()
            }
        } catch (_: Exception) {}
        mediaPlayer = null
    }

    fun release() {
        switchChannelJob?.cancel()
        retryJob?.cancel()
        videoWatchdogJob?.cancel()
        scope.cancel()
        releasePlayer()
        try {
            libVLC?.release()
        } catch (_: Exception) {}
        libVLC = null
        log("LibVLC 资源已完全释放")
    }
}
