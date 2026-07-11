package dev.aaa1115910.biliapi.http

import dev.aaa1115910.biliapi.http.entity.BiliResponse
import dev.aaa1115910.biliapi.http.entity.live.AreaRoomListData
import dev.aaa1115910.biliapi.http.entity.live.DanmuInfoData
import dev.aaa1115910.biliapi.http.entity.live.FollowingLiveListData
import dev.aaa1115910.biliapi.http.entity.live.WebAreaListData
import dev.aaa1115910.biliapi.http.entity.live.HistoryDanmaku
import dev.aaa1115910.biliapi.http.entity.live.RoomPlayInfoData
import dev.aaa1115910.biliapi.http.entity.live.RoomPlayInfoV2Data
import dev.aaa1115910.biliapi.http.plugins.BiliUserAgent
import dev.aaa1115910.biliapi.http.util.wbiSignedParams
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.URLProtocol
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

object BiliLiveHttpApi {
    private var endPoint: String = ""
    private lateinit var client: HttpClient
    private val logger = KotlinLogging.logger { }

    init {
        createClient()
    }

    private fun createClient() {
        client = HttpClient(OkHttp) {
            BiliUserAgent()
            install(ContentNegotiation) {
                json(Json {
                    coerceInputValues = true
                    ignoreUnknownKeys = true
                    prettyPrint = true
                })
            }
            install(ContentEncoding) {
                deflate(1.0F)
                gzip(0.9F)
            }
            defaultRequest {
                url {
                    host = "api.live.bilibili.com"
                    protocol = URLProtocol.HTTPS
                }
                header("Referer", "https://live.bilibili.com/")
            }
        }
    }

    /**
     * 拼接请求所需的 Cookie，直播接口受风控保护，需要携带已激活的 buvid3
     */
    private fun buildCookie(sessData: String? = null): String {
        var cookie = "buvid3=${BiliHttpApi.buvid3};"
        if (!sessData.isNullOrBlank()) cookie += " SESSDATA=$sessData;"
        return cookie
    }

    /**
     * 获取直播间[roomId]的弹幕连接地址等信息，例如 token
     *
     * 该接口受风控保护，需要 wbi 签名与 buvid3 cookie，否则返回 -352
     */
    suspend fun getLiveDanmuInfo(roomId: Int, sessData: String? = null): BiliResponse<DanmuInfoData> {
        val signedParams = wbiSignedParams(mapOf("id" to roomId.toString()))
        return client.get("/xlive/web-room/v1/index/getDanmuInfo") {
            signedParams.forEach { (key, value) -> parameter(key, value) }
            header("Cookie", buildCookie(sessData))
        }.body()
    }

    /**
     * 获取直播间[roomId]的信息
     */
    suspend fun getLiveRoomPlayInfo(roomId: Int): BiliResponse<RoomPlayInfoData> =
        client.get("/xlive/web-room/v1/index/getRoomPlayInfo") {
            parameter("room_id", roomId)
            header("Cookie", buildCookie())
        }.body()

    /**
     * 获取直播间[roomId]的历史弹幕
     */
    suspend fun getLiveDanmuHistory(roomId: Int): BiliResponse<HistoryDanmaku> =
        client.get("/xlive/web-room/v1/dM/gethistory") {
            parameter("roomid", roomId)
        }.body()

    /**
     * 获取关注的直播列表，正在直播的主播排在前面
     */
    suspend fun getFollowingLiveList(
        page: Int = 1,
        pageSize: Int = 10,
        sessData: String
    ): BiliResponse<FollowingLiveListData> =
        client.get("/xlive/web-ucenter/user/following") {
            parameter("page", page)
            parameter("page_size", pageSize)
            parameter("ignoreRecord", 1)
            parameter("hit_ab", true)
            header("Cookie", buildCookie(sessData))
        }.body()

    /**
     * 获取直播分区列表
     */
    suspend fun getWebAreaList(): BiliResponse<WebAreaListData> =
        client.get("/xlive/web-interface/v1/index/getWebAreaList") {
            parameter("source_id", 2)
            header("Cookie", buildCookie())
        }.body()

    /**
     * 获取分区下的直播间列表
     *
     * 使用 app 端接口，web 端接口 (xlive/web-interface/v1/second/getList) 风控严格
     */
    suspend fun getAreaRoomList(
        parentAreaId: Int,
        areaId: Int = 0,
        page: Int = 1,
        pageSize: Int = 20
    ): BiliResponse<AreaRoomListData> =
        client.get("/room/v3/area/getRoomList") {
            parameter("platform", "android")
            parameter("parent_area_id", parentAreaId)
            parameter("area_id", areaId)
            parameter("sort_type", "online")
            parameter("page", page)
            parameter("page_size", pageSize)
        }.body()

    /**
     * 获取直播间[roomId]的播放信息 v2，包含 FLV 与 HLS 拉流地址
     *
     * @param qn 清晰度，例如 10000 原画 400 蓝光 250 超清 150 高清，高清晰度需要登录
     */
    suspend fun getLiveRoomPlayInfoV2(
        roomId: Long,
        qn: Int = 10000,
        sessData: String? = null
    ): BiliResponse<RoomPlayInfoV2Data> =
        client.get("/xlive/web-room/v2/index/getRoomPlayInfo") {
            parameter("room_id", roomId)
            parameter("protocol", "0,1")
            parameter("format", "0,1,2")
            parameter("codec", "0,1")
            parameter("qn", qn)
            parameter("platform", "web")
            parameter("ptype", 8)
            header("Cookie", buildCookie(sessData))
        }.body()
}