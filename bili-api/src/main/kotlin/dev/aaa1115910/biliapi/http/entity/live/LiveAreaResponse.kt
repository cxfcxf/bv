package dev.aaa1115910.biliapi.http.entity.live

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 直播分区列表
 */
@Serializable
data class WebAreaListData(
    val data: List<WebAreaParent> = emptyList()
) {
    @Serializable
    data class WebAreaParent(
        val id: Int = 0,
        val name: String = "",
        val list: List<WebArea> = emptyList()
    ) {
        @Serializable
        data class WebArea(
            val id: Int = 0,
            val name: String = "",
            @SerialName("parent_id")
            val parentId: Int = 0
        )
    }
}

/**
 * 分区直播间列表 (app 端接口)
 */
@Serializable
data class AreaRoomListData(
    val count: Int = 0,
    val list: List<AreaRoom> = emptyList()
) {
    @Serializable
    data class AreaRoom(
        @SerialName("roomid")
        val roomId: Long = 0,
        val uid: Long = 0,
        val uname: String = "",
        val title: String = "",
        val face: String = "",
        val cover: String = "",
        @SerialName("user_cover")
        val userCover: String = "",
        @SerialName("system_cover")
        val systemCover: String = "",
        val online: Long = 0,
        @SerialName("area_v2_name")
        val areaV2Name: String = ""
    )
}
