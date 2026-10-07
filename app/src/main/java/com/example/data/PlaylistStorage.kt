package com.example.data

import android.content.Context
import java.io.*

class PlaylistStorage(private val context: Context) {

    private val playlistFile: File
        get() = File(context.filesDir, "custom_playlist.m3u")

    fun hasCustomPlaylist(): Boolean {
        return playlistFile.exists() && playlistFile.length() > 0
    }

    fun savePlaylist(content: String) {
        try {
            FileOutputStream(playlistFile).use { fos ->
                OutputStreamWriter(fos, Charsets.UTF_8).use { writer ->
                    writer.write(content)
                    writer.flush()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun savePlaylist(inputStream: InputStream) {
        try {
            FileOutputStream(playlistFile).use { fos ->
                val buffer = ByteArray(16384)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    fos.write(buffer, 0, bytesRead)
                }
                fos.flush()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun openPlaylistInputStream(): InputStream? {
        return if (hasCustomPlaylist()) {
            try {
                FileInputStream(playlistFile)
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }

    fun openPlaylistReader(): Reader? {
        val stream = openPlaylistInputStream() ?: return null
        return InputStreamReader(stream, Charsets.UTF_8)
    }

    fun clearPlaylist() {
        try {
            if (playlistFile.exists()) {
                playlistFile.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
