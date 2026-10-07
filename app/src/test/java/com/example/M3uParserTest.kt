package com.example

import com.example.data.M3uParser
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class M3uParserTest {

    @Test
    fun testStandardM3uParsing() {
        val m3uContent = """
            #EXTM3U
            #EXTINF:-1 tvg-id="cctv1" tvg-name="CCTV-1" tvg-logo="http://example.com/cctv1.png" group-title="央视频道",CCTV-1 综合
            http://live.example.com/cctv1.m3u8
            #EXTINF:-1 tvg-id="cctv2" group-title="央视频道",CCTV-2 财经
            https://live.example.com/cctv2.ts
            #EXTINF:-1 group-title="卫视频道",东方卫视
            http://live.example.com/dongfang.m3u8
        """.trimIndent()

        val channels = M3uParser.parse(m3uContent)
        assertEquals(3, channels.size)

        assertEquals("CCTV-1 综合", channels[0].name)
        assertEquals("央视频道", channels[0].group)
        assertEquals("http://live.example.com/cctv1.m3u8", channels[0].url)
        assertEquals("cctv1", channels[0].tvgId)

        assertEquals("CCTV-2 财经", channels[1].name)
        assertEquals("https://live.example.com/cctv2.ts", channels[1].url)

        assertEquals("东方卫视", channels[2].name)
        assertEquals("卫视频道", channels[2].group)
    }

    @Test
    fun testDiypFormatParsing() {
        val diypContent = """
            央视频道,#genre#
            CCTV-1,http://live.example.com/cctv1.m3u8
            CCTV-2,http://live.example.com/cctv2.m3u8
            卫视频道,#genre#
            湖南卫视,http://live.example.com/hunan.m3u8#http://backup.example.com/hunan.m3u8
            浙江卫视,http://live.example.com/zhejiang.m3u8
        """.trimIndent()

        val channels = M3uParser.parse(diypContent)
        assertEquals(4, channels.size)

        assertEquals("CCTV-1", channels[0].name)
        assertEquals("央视频道", channels[0].group)

        assertEquals("湖南卫视", channels[2].name)
        assertEquals("卫视频道", channels[2].group)
        // Multi-url separated by '#' should pick primary URL
        assertEquals("http://live.example.com/hunan.m3u8", channels[2].url)
    }

    @Test
    fun testUtf8BomAndGbkHandling() {
        // UTF-8 with BOM (\uFEFF)
        val bomContent = "\uFEFF#EXTM3U\n#EXTINF:-1 group-title=\"测试\",测试频道\nhttp://live.example.com/test.m3u8"
        val channels = M3uParser.parse(bomContent)
        assertEquals(1, channels.size)
        assertEquals("测试频道", channels[0].name)
        assertEquals("测试", channels[0].group)

        // GBK byte stream parsing
        val gbkBytes = ("#EXTINF:-1 group-title=\"中文分类\",高清频道\nhttp://live.example.com/hd.m3u8\n")
            .toByteArray(charset("GBK"))
        val gbkChannels = M3uParser.parse(ByteArrayInputStream(gbkBytes))
        assertEquals(1, gbkChannels.size)
        assertEquals("高清频道", gbkChannels[0].name)
    }

    @Test
    fun testRealStreamMediaInfoResolutionLabels() {
        val info4K = com.example.model.StreamMediaInfo(width = 3840, height = 2160, videoCodec = "hevc", frameRate = 60f)
        assertTrue(info4K.is4K)
        assertEquals("4K 超高清", info4K.resolutionLabel)

        val info1080P = com.example.model.StreamMediaInfo(width = 1920, height = 1080, videoCodec = "h264", frameRate = 50f)
        assertTrue(info1080P.is1080P)
        assertEquals("1080P 全高清", info1080P.resolutionLabel)

        val info720P = com.example.model.StreamMediaInfo(width = 1280, height = 720, videoCodec = "h264")
        assertTrue(info720P.is720P)
        assertEquals("720P 高清", info720P.resolutionLabel)

        val infoSD = com.example.model.StreamMediaInfo(width = 720, height = 576)
        assertTrue(infoSD.isSD)
        assertEquals("576P 标清", infoSD.resolutionLabel)

        assertEquals(8000, com.example.model.CacheMode.ULTRA_4K.cachingMs)
    }
}
