package dev.aaa1115910.bv.entity.carddata

data class VideoCardData(
    val avid: Long,
    val cid: Long? = null,
    val epId: Int? = null,
    val title: String,
    val cover: String,
    val upName: String,
    val upMid: Long? = null,
    val playString: String =  "",
    val danmakuString: String =  "",
    val timeString: String =  "",
    val jumpToSeason: Boolean = false,
    val pubTime: String? = null,
    /** 非空表示直播间记录，点击应跳转直播播放页 */
    val liveRoomId: Long? = null,
    /** 直播间是否正在直播 */
    val living: Boolean = false
)
