package dev.aaa1115910.bv.ui.state

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.aaa1115910.biliapi.entity.danmaku.DanmakuMask
import dev.aaa1115910.biliapi.entity.video.Subtitle
import dev.aaa1115910.biliapi.entity.video.VideoShot
import dev.aaa1115910.bilisubtitle.entity.SubtitleItem
import dev.aaa1115910.bv.component.controllers.DanmakuType
import dev.aaa1115910.bv.entity.Audio
import dev.aaa1115910.bv.entity.VideoAspectRatio
import dev.aaa1115910.bv.entity.VideoCodec
import dev.aaa1115910.bv.entity.VideoListItem
import dev.aaa1115910.biliapi.entity.video.VideoPage
import dev.aaa1115910.bv.entity.carddata.VideoCardData
import dev.aaa1115910.bv.entity.proxy.ProxyArea

// 1. 核心 UI 状态 (低频更新)
data class PlayerUiState(
    // 视频信息
    val aid: Long = 0,
    val cid: Long = 0,
    val epid: Int? = null,
    val seasonId: Int = 0,
    val authorMid: Long = 0,
    val authorName: String = "",
    val title: String = "",
    val videoHeight: Int = 0,
    val videoWidth: Int = 0,
    val lastPlayed: Int = 0,
    val fromSeason: Boolean = false,
    val proxyArea: ProxyArea = ProxyArea.MainLand,
    val subType: Int = 0,

    // 播放状态
    val playerState: PlayerState = PlayerState.Ready,
    val isBuffering: Boolean = false, // 缓冲和暂停会同时出现，故单独列出
    // 进度条缩略图
    val videoShot: VideoShot? = null,
    // 播放器时钟
    val clock: Pair<Int, Int> = Pair(0, 0),

    // 显示tip
    val showSkipToNextEp: Boolean = false,
    val showBackToStart: Boolean = false,
    val showPreviewTip: Boolean = false,

    // 播放器配置与资源
    val availableQuality: Map<Int, String> = emptyMap(),
    val availableVideoCodec: List<VideoCodec> = emptyList(),
    val availableAudio: List<Audio> = emptyList(),
    val availableVideoList: List<VideoListItem> = emptyList(),

    // 相关视频
    val relatedVideos: List<VideoCardData> = emptyList(),

    // ==== 当前选中状态 ====

    // 媒体格式
    val mediaProfileState: MediaProfileState = MediaProfileState(),

    // 播放速度和宽高比
    val playSpeed: Float = 1f,
    val aspectRatio: VideoAspectRatio = VideoAspectRatio.Default,

    // 弹幕状态
    val danmakuState: DanmakuState = DanmakuState(),
    val danmakuMask: DanmakuMask? = null,

    // 字幕状态
    val subtitleState: SubtitleState = SubtitleState(),
    val subtitleId: Long = -1L,
    val subtitleData: List<SubtitleItem> = emptyList(),
    val subtitleList: List<Subtitle> = emptyList(),
)

// 2. 播放器进度条状态 (高频更新)
data class SeekerState(
    val totalDuration: Long = 0,
    val currentTime: Long = 0,
    val bufferedPercentage: Int = 0,
    val debugInfo: String = ""
)

data class DanmakuState(
    val scale: Float = 0f,
    val opacity: Float = 0f,
    val area: Float = 0f,
    val speedFactor: Float = 1f,
    val maskEnabled: Boolean = false,
    val enabledTypes: List<DanmakuType> = emptyList(),
)

data class SubtitleState(
    val fontSize: TextUnit = 0.sp,
    val opacity: Float = 0f,
    val bottomPadding: Dp = 0.dp
)

data class MediaProfileState(
    val qualityId: Int = 0,
    val videoCodec: VideoCodec = VideoCodec.AVC,
    val audio: Audio = Audio.A192K
)

sealed class PlayerState {
    data object Ready: PlayerState()
    data object Playing: PlayerState()
    data object Paused: PlayerState()
    data object Ended: PlayerState()
    data class Error(val message: String): PlayerState()
}

/** 相邻的一集：先在当前视频的分P内找，找不到再跳到列表里相邻的视频 */
sealed interface AdjacentEpisode {
    val title: String

    data class Page(val parent: VideoListItem, val page: VideoPage) : AdjacentEpisode {
        override val title: String = page.title
    }

    data class Video(val video: VideoListItem) : AdjacentEpisode {
        override val title: String = video.title
    }
}

/**
 * 上一集/下一集的目标。分P 和合集是嵌套的：先走完当前视频的分P，
 * 再跳到合集里相邻的视频。自动连播和控件上的按钮共用这套规则，
 * 免得两边判断不一致。
 *
 * @param forward true 取下一集，false 取上一集
 */
fun PlayerUiState.adjacentEpisode(forward: Boolean): AdjacentEpisode? {
    val step = if (forward) 1 else -1
    val index = availableVideoList.indexOfFirst { it.aid == aid }
    val currentItem = availableVideoList.getOrNull(index)

    val pages = currentItem?.ugcPages
    if (currentItem != null && !pages.isNullOrEmpty()) {
        val pageIndex = pages.indexOfFirst { it.cid == cid }
        val targetPage = if (pageIndex == -1) null else pages.getOrNull(pageIndex + step)
        if (targetPage != null) return AdjacentEpisode.Page(currentItem, targetPage)
    }

    if (index == -1) return null
    val neighbour = availableVideoList.getOrNull(index + step) ?: return null
    return AdjacentEpisode.Video(neighbour)
}
