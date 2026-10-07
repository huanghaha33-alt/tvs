package com.example.data

import com.example.model.Channel

object DefaultChannels {

    // Reliable public live streams and IPTV MPEG-TS / HLS test streams
    private const val STREAM_HLS_1 = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
    private const val STREAM_HLS_2 = "https://cph-p2p-msl.akamaized.net/hls/live/2000341/test/master.m3u8"
    private const val STREAM_TS_1 = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
    private const val STREAM_TS_2 = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4"
    private const val STREAM_TS_3 = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4"

    fun getInitialChannels(): List<Channel> {
        val list = mutableListOf<Channel>()
        var no = 1

        fun add(name: String, group: String, tvgId: String, url: String, logo: String = "") {
            list.add(
                Channel(
                    id = "ch_$no",
                    number = no,
                    name = name,
                    url = url,
                    group = group,
                    logo = logo,
                    tvgId = tvgId,
                    isFavorite = (no in 1..4)
                )
            )
            no++
        }

        // 1. 卫视频道
        add("东方卫视", "卫视频道", "dongfang", STREAM_HLS_1)
        add("浙江卫视", "卫视频道", "zhejiang", STREAM_HLS_2)
        add("湖北卫视", "卫视频道", "hubei", STREAM_TS_1)
        add("北京卫视", "卫视频道", "beijing", STREAM_TS_2)
        add("天津卫视", "卫视频道", "tianjin", STREAM_TS_3)
        add("黑龙江卫视", "卫视频道", "heilongjiang", STREAM_HLS_1)
        add("河北卫视", "卫视频道", "hebei", STREAM_HLS_2)
        add("湖南卫视", "卫视频道", "hunan", STREAM_TS_1)
        add("江苏卫视", "卫视频道", "jiangsu", STREAM_TS_2)
        add("广东卫视", "卫视频道", "guangdong", STREAM_TS_3)
        add("深圳卫视", "卫视频道", "shenzhen", STREAM_HLS_1)
        add("山东卫视", "卫视频道", "shandong", STREAM_HLS_2)
        add("安徽卫视", "卫视频道", "anhui", STREAM_TS_1)

        // 2. 央视频道
        add("CCTV-1 综合", "央视频道", "cctv1", STREAM_HLS_1)
        add("CCTV-2 财经", "央视频道", "cctv2", STREAM_HLS_2)
        add("CCTV-3 综艺", "央视频道", "cctv3", STREAM_TS_1)
        add("CCTV-4 中文国际", "央视频道", "cctv4", STREAM_TS_2)
        add("CCTV-5 体育", "央视频道", "cctv5", STREAM_TS_3)
        add("CCTV-6 电影", "央视频道", "cctv6", STREAM_HLS_1)
        add("CCTV-7 国防军事", "央视频道", "cctv7", STREAM_HLS_2)
        add("CCTV-8 电视剧", "央视频道", "cctv8", STREAM_TS_1)
        add("CCTV-9 纪录", "央视频道", "cctv9", STREAM_TS_2)
        add("CCTV-10 科教", "央视频道", "cctv10", STREAM_TS_3)
        add("CCTV-13 新闻", "央视频道", "cctv13", STREAM_HLS_1)

        // 3. 地方频道
        add("上海都市频道", "地方频道", "sh_city", STREAM_HLS_1)
        add("上海新闻综合", "地方频道", "sh_news", STREAM_HLS_2)
        add("浙江教科影视频道", "地方频道", "zj_edu", STREAM_TS_1)
        add("广东珠江频道", "地方频道", "gd_zhujiang", STREAM_TS_2)
        add("北京文艺频道", "地方频道", "bj_art", STREAM_TS_3)

        // 4. 电影轮播
        add("动作大片 4K 轮播", "电影轮播", "movie_action", STREAM_TS_1)
        add("经典科幻 巨幕轮播", "电影轮播", "movie_scifi", STREAM_TS_2)
        add("华语经典 影院轮播", "电影轮播", "movie_chinese", STREAM_TS_3)

        // 5. 电视剧轮播
        add("热播古装连续剧", "电视剧轮播", "drama_period", STREAM_HLS_1)
        add("都市情感剧场", "电视剧轮播", "drama_city", STREAM_HLS_2)

        // 6. 其他频道
        add("探索与发现 4K", "其他频道", "other_discovery", STREAM_HLS_1)
        add("经典名曲鉴赏", "其他频道", "other_music", STREAM_HLS_2)
        add("少儿动画大本营", "其他频道", "other_kids", STREAM_TS_1)

        return list
    }
}
