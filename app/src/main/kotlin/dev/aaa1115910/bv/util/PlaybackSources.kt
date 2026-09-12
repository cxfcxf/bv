package dev.aaa1115910.bv.util

import dev.aaa1115910.bv.player.DashTrack

/**
 * DASH 分段播放所需信息。只有 Web 接口返回分段索引，拿得到时才走这条路，
 * 播放器会按 byte range 请求，避开 CDN 对长连接的限速。
 */
internal data class DashSources(
    val video: DashTrack,
    val audio: DashTrack?,
    val durationMs: Long
)

/** Remaining mirrors for the selected quality/codec, preferred URL first. */
internal data class PlaybackSources(
    val videoUrls: List<String>,
    val audioUrls: List<String> = emptyList(),
    val dash: DashSources? = null
) {
    val videoUrl: String get() = videoUrls.first()
    val audioUrl: String? get() = audioUrls.firstOrNull()

    fun afterFailure(url: String): PlaybackSources? = when {
        url == videoUrl && videoUrls.size > 1 -> copy(videoUrls = videoUrls.drop(1))
        url == audioUrl && audioUrls.size > 1 -> copy(audioUrls = audioUrls.drop(1))
        else -> null
    }
}
