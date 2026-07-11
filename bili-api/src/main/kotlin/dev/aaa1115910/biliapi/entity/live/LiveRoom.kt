package dev.aaa1115910.biliapi.entity.live

import dev.aaa1115910.biliapi.http.entity.live.AreaRoomListData
import dev.aaa1115910.biliapi.http.entity.live.FollowingLiveListData

/**
 * 直播间卡片信息
 */
data class LiveRoom(
    val roomId: Long,
    val uid: Long,
    val uname: String,
    val title: String,
    val face: String,
    val cover: String,
    val living: Boolean,
    val areaName: String,
    val watchedText: String,
    val online: Long = 0
) {
    companion object {
        fun fromFollowingLiveRoom(room: FollowingLiveListData.FollowingLiveRoom) = LiveRoom(
            roomId = room.roomId,
            uid = room.uid,
            uname = room.uname,
            title = room.title,
            face = room.face,
            cover = room.roomCover.ifBlank { room.face },
            living = room.liveStatus == 1,
            areaName = room.areaNameV2.ifBlank { room.areaName },
            watchedText = room.watchedShow?.textSmall ?: ""
        )

        fun fromAreaRoom(room: AreaRoomListData.AreaRoom) = LiveRoom(
            roomId = room.roomId,
            uid = room.uid,
            uname = room.uname,
            title = room.title,
            face = room.face,
            cover = room.cover.ifBlank { room.userCover.ifBlank { room.systemCover } },
            living = true,
            areaName = room.areaV2Name,
            watchedText = "",
            online = room.online
        )
    }
}

/**
 * 直播分区
 */
data class LiveArea(
    val id: Int,
    val name: String
)

/**
 * 关注的直播列表分页数据
 */
data class FollowingLiveList(
    val liveCount: Int,
    val totalPage: Int,
    val rooms: List<LiveRoom>
)

/**
 * 直播拉流信息
 *
 * @param qualityDescMap 清晰度代码与名称的映射
 */
data class LiveStreamInfo(
    val living: Boolean,
    val urls: List<LiveStreamUrl>,
    val acceptQualities: List<Int>,
    val currentQuality: Int,
    val qualityDescMap: Map<Int, String>
) {
    /**
     * @param protocol http_stream (FLV) 或 http_hls
     * @param format flv / ts / fmp4
     * @param codec avc / hevc
     */
    data class LiveStreamUrl(
        val url: String,
        val protocol: String,
        val format: String,
        val codec: String
    )
}
