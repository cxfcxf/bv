package dev.aaa1115910.biliapi.http.entity.live

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 关注的直播列表
 *
 * @param count 关注的主播总数
 * @param liveCount 正在直播的主播数
 * @param list 直播间列表，正在直播的排在前面
 */
@Serializable
data class FollowingLiveListData(
    val count: Int = 0,
    @SerialName("live_count")
    val liveCount: Int = 0,
    val pageSize: Int = 0,
    val totalPage: Int = 0,
    val list: List<FollowingLiveRoom> = emptyList()
) {
    @Serializable
    data class FollowingLiveRoom(
        @SerialName("roomid")
        val roomId: Long = 0,
        val uid: Long = 0,
        val uname: String = "",
        val title: String = "",
        val face: String = "",
        @SerialName("live_status")
        val liveStatus: Int = 0,
        @SerialName("record_live_time")
        val recordLiveTime: Long = 0,
        @SerialName("area_name_v2")
        val areaNameV2: String = "",
        @SerialName("room_news")
        val roomNews: String = "",
        @SerialName("room_cover")
        val roomCover: String = "",
        @SerialName("parent_area_name")
        val parentAreaName: String = "",
        @SerialName("area_name")
        val areaName: String = "",
        @SerialName("watched_show")
        val watchedShow: WatchedShow? = null
    ) {
        @Serializable
        data class WatchedShow(
            val num: Long = 0,
            @SerialName("text_small")
            val textSmall: String = "",
            @SerialName("text_large")
            val textLarge: String = ""
        )
    }
}
