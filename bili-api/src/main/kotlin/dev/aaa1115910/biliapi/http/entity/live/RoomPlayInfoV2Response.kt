package dev.aaa1115910.biliapi.http.entity.live

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 直播间播放信息 v2，含实际拉流地址
 *
 * @param liveStatus 0: 未开播 1: 直播中 2: 轮播中
 */
@Serializable
data class RoomPlayInfoV2Data(
    @SerialName("room_id")
    val roomId: Long = 0,
    val uid: Long = 0,
    @SerialName("live_status")
    val liveStatus: Int = 0,
    @SerialName("live_time")
    val liveTime: Long = 0,
    @SerialName("playurl_info")
    val playurlInfo: PlayurlInfo? = null
) {
    @Serializable
    data class PlayurlInfo(
        val playurl: Playurl? = null
    ) {
        @Serializable
        data class Playurl(
            @SerialName("g_qn_desc")
            val qnDesc: List<QnDesc> = emptyList(),
            val stream: List<Stream> = emptyList()
        ) {
            @Serializable
            data class QnDesc(
                val qn: Int = 0,
                val desc: String = ""
            )

            @Serializable
            data class Stream(
                @SerialName("protocol_name")
                val protocolName: String = "",
                val format: List<Format> = emptyList()
            ) {
                @Serializable
                data class Format(
                    @SerialName("format_name")
                    val formatName: String = "",
                    val codec: List<Codec> = emptyList()
                ) {
                    @Serializable
                    data class Codec(
                        @SerialName("codec_name")
                        val codecName: String = "",
                        @SerialName("current_qn")
                        val currentQn: Int = 0,
                        @SerialName("accept_qn")
                        val acceptQn: List<Int> = emptyList(),
                        @SerialName("base_url")
                        val baseUrl: String = "",
                        @SerialName("url_info")
                        val urlInfo: List<UrlInfo> = emptyList()
                    ) {
                        @Serializable
                        data class UrlInfo(
                            val host: String = "",
                            val extra: String = "",
                            @SerialName("stream_ttl")
                            val streamTtl: Int = 0
                        )
                    }
                }
            }
        }
    }
}
