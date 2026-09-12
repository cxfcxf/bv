package dev.aaa1115910.bv.util

import dev.aaa1115910.bv.player.DashTrack

/**
 * DASH 分段播放所需信息。只有 Web 接口返回分段索引，拿得到时才走这条路，
 * 播放器会按 byte range 请求，避开 CDN 对长连接的限速。
 */
internal data class DashSources(
    /** 多条即开启自适应码率，单条则固定清晰度 */
    val videos: List<DashTrack>,
    val audio: DashTrack?,
    val durationMs: Long
) {
    /**
     * 剔除某个 CDN 后的副本。一个站点出问题时各档清晰度多半都受影响，
     * 所以全部一起剔除。没有任何变化时返回 null，表示无处可退。
     *
     * 某条流的地址全在该站点时保持原样 —— 宁可继续用，也不能留空。
     */
    fun withoutHost(host: String): DashSources? {
        fun DashTrack.pruned(): DashTrack {
            val remaining = urls.filterNot { it.cdnHost() == host }
            return if (remaining.isEmpty()) this else copy(urls = remaining)
        }

        val prunedVideos = videos.map { it.pruned() }
        val prunedAudio = audio?.pruned()
        val changed = prunedVideos != videos || prunedAudio != audio
        return if (changed) copy(videos = prunedVideos, audio = prunedAudio) else null
    }
}

/** 不依赖 android.net.Uri，方便单测 */
internal fun String.cdnHost(): String = substringAfter("://").substringBefore("/")

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
