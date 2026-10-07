package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.model.AspectRatioMode
import com.example.model.CacheMode
import com.example.model.PlayerSettings
import java.io.InputStream
import java.io.Reader

class PreferenceManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("diyp_iptv_prefs", Context.MODE_PRIVATE)
    private val playlistStorage = PlaylistStorage(context)

    companion object {
        private const val KEY_CACHE_MODE = "cache_mode"
        private const val KEY_HW_DECODE = "hw_decode"
        private const val KEY_ASPECT_RATIO = "aspect_ratio"
        private const val KEY_CUSTOM_M3U_URL = "custom_m3u_url"
        private const val KEY_CUSTOM_EPG_URL = "custom_epg_url"
        private const val KEY_LAST_CHANNEL_ID = "last_channel_id"
        private const val KEY_FAVORITES = "favorite_channel_ids"
    }

    fun loadSettings(): PlayerSettings {
        val cacheModeName = prefs.getString(KEY_CACHE_MODE, CacheMode.ULTRA_4K.name) ?: CacheMode.ULTRA_4K.name
        val cacheMode = try {
            CacheMode.valueOf(cacheModeName)
        } catch (e: Exception) {
            CacheMode.ULTRA_4K
        }

        val hw = prefs.getBoolean(KEY_HW_DECODE, true)
        val aspectName = prefs.getString(KEY_ASPECT_RATIO, AspectRatioMode.FIT_SCREEN.name) ?: AspectRatioMode.FIT_SCREEN.name
        val aspectRatio = try {
            AspectRatioMode.valueOf(aspectName)
        } catch (e: Exception) {
            AspectRatioMode.FIT_SCREEN
        }

        val customM3uUrl = prefs.getString(KEY_CUSTOM_M3U_URL, "") ?: ""
        val customEpgUrl = prefs.getString(KEY_CUSTOM_EPG_URL, "") ?: ""
        val lastChannelId = prefs.getString(KEY_LAST_CHANNEL_ID, "") ?: ""

        return PlayerSettings(
            cacheMode = cacheMode,
            hardwareAcceleration = hw,
            aspectRatio = aspectRatio,
            customM3uUrl = customM3uUrl,
            customEpgUrl = customEpgUrl,
            lastChannelId = lastChannelId
        )
    }

    fun saveCacheMode(mode: CacheMode) {
        prefs.edit().putString(KEY_CACHE_MODE, mode.name).apply()
    }

    fun saveHardwareAcceleration(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HW_DECODE, enabled).apply()
    }

    fun saveAspectRatio(mode: AspectRatioMode) {
        prefs.edit().putString(KEY_ASPECT_RATIO, mode.name).apply()
    }

    fun saveCustomM3uUrl(url: String) {
        prefs.edit().putString(KEY_CUSTOM_M3U_URL, url).apply()
    }

    fun saveCustomM3uContent(content: String) {
        playlistStorage.savePlaylist(content)
    }

    fun saveCustomM3uStream(inputStream: InputStream) {
        playlistStorage.savePlaylist(inputStream)
    }

    fun openCustomM3uInputStream(): InputStream? {
        return playlistStorage.openPlaylistInputStream()
    }

    fun hasCustomM3u(): Boolean {
        return playlistStorage.hasCustomPlaylist()
    }

    fun clearCustomM3u() {
        playlistStorage.clearPlaylist()
    }

    fun saveCustomEpgUrl(url: String) {
        prefs.edit().putString(KEY_CUSTOM_EPG_URL, url).apply()
    }

    fun saveLastChannelId(channelId: String) {
        prefs.edit().putString(KEY_LAST_CHANNEL_ID, channelId).apply()
    }

    fun getFavorites(): Set<String> {
        return prefs.getStringSet(KEY_FAVORITES, emptySet()) ?: emptySet()
    }

    fun setFavorite(channelId: String, isFavorite: Boolean) {
        val set = getFavorites().toMutableSet()
        if (isFavorite) {
            set.add(channelId)
        } else {
            set.remove(channelId)
        }
        prefs.edit().putStringSet(KEY_FAVORITES, set).apply()
    }
}
