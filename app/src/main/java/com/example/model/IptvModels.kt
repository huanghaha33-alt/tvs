package com.example.model

enum class CacheMode(val displayName: String, val cachingMs: Int) {
    LOW_LATENCY("低延迟 (500ms)", 500),
    STANDARD("标准直播 (1500ms)", 1500),
    STABLE("稳定抗卡顿 (3000ms)", 3000),
    ULTRA_4K("4K超强缓冲 (8000ms)", 8000)
}

enum class AspectRatioMode(val displayName: String, val vlcRatio: String?) {
    FIT_SCREEN("自适应居中 (推荐)", null),
    RATIO_16_9("16 : 9", "16:9"),
    RATIO_4_3("4 : 3", "4:3"),
    FILL("全屏拉伸", "fill")
}

data class StreamMediaInfo(
    val width: Int = 0,
    val height: Int = 0,
    val videoCodec: String = "",
    val audioCodec: String = "",
    val frameRate: Float = 0f
) {
    val is4K: Boolean get() = width >= 3840 || height >= 2160
    val is1080P: Boolean get() = (height in 1080..2159) || (width in 1920..3839)
    val is720P: Boolean get() = (height in 720..1079) || (width in 1280..1919)
    val isSD: Boolean get() = height in 1..719

    val resolutionLabel: String
        get() = when {
            is4K -> "4K 超高清"
            is1080P -> "1080P 全高清"
            is720P -> "720P 高清"
            isSD -> "${height}P 标清"
            else -> "正在解析"
        }
}

data class PlayerSettings(
    val cacheMode: CacheMode = CacheMode.ULTRA_4K,
    val hardwareAcceleration: Boolean = true,
    val aspectRatio: AspectRatioMode = AspectRatioMode.FIT_SCREEN,
    val customM3uUrl: String = "",
    val customEpgUrl: String = "",
    val lastChannelId: String = "",
    val customBufferMs: Int = 8000
)

data class EpgProgram(
    val channelTvgId: String,
    val title: String,
    val startTime: Long, // timestamp in ms
    val endTime: Long,   // timestamp in ms
    val description: String = ""
) {
    fun isCurrent(now: Long = System.currentTimeMillis()): Boolean {
        return now in startTime..endTime
    }

    fun progress(now: Long = System.currentTimeMillis()): Float {
        if (endTime <= startTime) return 0f
        val p = (now - startTime).toFloat() / (endTime - startTime).toFloat()
        return p.coerceIn(0f, 1f)
    }
}

data class Channel(
    val id: String,
    val number: Int,
    val name: String,
    val url: String,
    val group: String,
    val logo: String = "",
    val tvgId: String = "",
    val isFavorite: Boolean = false
)

data class Category(
    val id: String,
    val name: String,
    val channelCount: Int = 0
)

sealed class PlaybackStatus {
    object Idle : PlaybackStatus()
    data class Loading(val message: String = "正在加载直播流...") : PlaybackStatus()
    data class Buffering(val percent: Float) : PlaybackStatus()
    data class Playing(val videoWidth: Int = 0, val videoHeight: Int = 0) : PlaybackStatus()
    data class Error(val errorMessage: String, val retryCountdown: Int = 0) : PlaybackStatus()
}
