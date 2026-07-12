package dev.aaa1115910.biliapi.repositories

import dev.aaa1115910.biliapi.entity.ApiType
import dev.aaa1115910.biliapi.entity.live.FollowingLiveList
import dev.aaa1115910.biliapi.entity.live.LiveArea
import dev.aaa1115910.biliapi.entity.live.LiveRoom
import dev.aaa1115910.biliapi.entity.live.LiveStreamInfo
import dev.aaa1115910.biliapi.http.BiliLiveHttpApi
import org.koin.core.annotation.Single

@Single
class LiveRepository(
    private val authRepository: AuthRepository
) {
    /**
     * 获取关注的直播列表，正在直播的主播排在前面
     */
    suspend fun getFollowingLiveRooms(
        page: Int = 1,
        pageSize: Int = 20
    ): FollowingLiveList {
        val data = BiliLiveHttpApi.getFollowingLiveList(
            page = page,
            pageSize = pageSize,
            sessData = authRepository.sessionData
                ?: throw IllegalStateException("未登录，无法获取关注的直播")
        ).getResponseData()
        return FollowingLiveList(
            liveCount = data.liveCount,
            totalPage = data.totalPage,
            rooms = data.list.map { LiveRoom.fromFollowingLiveRoom(it) }
        )
    }

    /**
     * 获取直播一级分区列表
     */
    suspend fun getLiveAreas(): List<LiveArea> =
        BiliLiveHttpApi.getWebAreaList().getResponseData().data
            .map { LiveArea(id = it.id, name = it.name) }

    /**
     * 获取分区下的直播间列表，按人气排序
     */
    suspend fun getAreaLiveRooms(
        parentAreaId: Int,
        page: Int = 1,
        pageSize: Int = 20
    ): List<LiveRoom> =
        BiliLiveHttpApi.getAreaRoomList(
            parentAreaId = parentAreaId,
            page = page,
            pageSize = pageSize
        ).getResponseData().list.map { LiveRoom.fromAreaRoom(it) }

    /**
     * 获取直播间拉流地址
     */
    suspend fun getLiveStream(
        roomId: Long,
        qn: Int = 10000,
        preferApiType: ApiType = ApiType.Web
    ): LiveStreamInfo {
        val data = when (preferApiType) {
            ApiType.Web -> BiliLiveHttpApi.getLiveRoomPlayInfoV2(
                roomId = roomId,
                qn = qn,
                sessData = authRepository.sessionData
            )

            ApiType.App -> BiliLiveHttpApi.getAppLiveRoomPlayInfoV2(
                roomId = roomId,
                qn = qn,
                accessKey = authRepository.accessToken
            )
        }.getResponseData()

        val playurl = data.playurlInfo?.playurl
        val urls = mutableListOf<LiveStreamInfo.LiveStreamUrl>()
        var currentQn = qn
        val acceptQn = mutableSetOf<Int>()
        playurl?.stream?.forEach { stream ->
            stream.format.forEach { format ->
                format.codec.forEach { codec ->
                    currentQn = codec.currentQn
                    acceptQn.addAll(codec.acceptQn)
                    // 每个 codec 通常有多个 CDN 节点，全部保留供播放端在重连时轮换
                    codec.urlInfo.forEach { urlInfo ->
                        urls.add(
                            LiveStreamInfo.LiveStreamUrl(
                                url = urlInfo.host + codec.baseUrl + urlInfo.extra,
                                protocol = stream.protocolName,
                                format = format.formatName,
                                codec = codec.codecName
                            )
                        )
                    }
                }
            }
        }
        return LiveStreamInfo(
            living = data.liveStatus == 1,
            urls = urls,
            acceptQualities = acceptQn.sortedDescending(),
            currentQuality = currentQn,
            qualityDescMap = playurl?.qnDesc?.associate { it.qn to it.desc } ?: emptyMap()
        )
    }
}
