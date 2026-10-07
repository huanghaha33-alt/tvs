package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.DefaultChannels
import com.example.data.M3uParser
import com.example.data.PlaylistStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("DIYP 电视直播", appName)
    }

    @Test
    fun `default channels contain CCTV and Satellite channels`() {
        val channels = DefaultChannels.getInitialChannels()
        assertTrue(channels.isNotEmpty())
        assertTrue(channels.any { it.name.contains("东方卫视") })
        assertTrue(channels.any { it.name.contains("CCTV-1") })
    }

    @Test
    fun `m3u parser parses valid extinf content`() {
        val m3uContent = """
            #EXTM3U
            #EXTINF:-1 tvg-id="cctv1" group-title="央视频道",CCTV-1 综合
            http://example.com/cctv1.m3u8
            #EXTINF:-1 tvg-id="dongfang" group-title="卫视频道",东方卫视
            http://example.com/dongfang.ts
        """.trimIndent()

        val parsed = M3uParser.parse(m3uContent)
        assertEquals(2, parsed.size)
        assertEquals("CCTV-1 综合", parsed[0].name)
        assertEquals("央视频道", parsed[0].group)
        assertEquals("http://example.com/cctv1.m3u8", parsed[0].url)
        assertEquals("东方卫视", parsed[1].name)
        assertEquals("卫视频道", parsed[1].group)
    }

    @Test
    fun `m3u parser parses DIYP genre format`() {
        val diypContent = """
            央视频道,#genre#
            CCTV-1 综合,http://example.com/cctv1.m3u8
            CCTV-2 财经,http://example.com/cctv2.m3u8
            卫视频道,#genre#
            浙江卫视,http://example.com/zhejiang.m3u8
        """.trimIndent()

        val parsed = M3uParser.parse(diypContent)
        assertEquals(3, parsed.size)
        assertEquals("CCTV-1 综合", parsed[0].name)
        assertEquals("央视频道", parsed[0].group)
        assertEquals("浙江卫视", parsed[2].name)
        assertEquals("卫视频道", parsed[2].group)
    }

    @Test
    fun `playlist storage writes and reads without memory crash`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = PlaylistStorage(context)
        val content = "#EXTM3U\n#EXTINF:-1,测试频道\nhttp://example.com/live.m3u8\n"

        storage.savePlaylist(content)
        assertTrue(storage.hasCustomPlaylist())

        val reader = storage.openPlaylistReader()
        assertTrue(reader != null)
        val channels = reader?.let { M3uParser.parse(it) } ?: emptyList()
        assertEquals(1, channels.size)
        assertEquals("测试频道", channels[0].name)
    }
}
