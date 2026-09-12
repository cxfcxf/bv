package dev.aaa1115910.bv.util

/** Remaining mirrors for the selected quality/codec, preferred URL first. */
internal data class PlaybackSources(
    val videoUrls: List<String>,
    val audioUrls: List<String> = emptyList()
) {
    val videoUrl: String get() = videoUrls.first()
    val audioUrl: String? get() = audioUrls.firstOrNull()

    fun afterFailure(url: String): PlaybackSources? = when {
        url == videoUrl && videoUrls.size > 1 -> copy(videoUrls = videoUrls.drop(1))
        url == audioUrl && audioUrls.size > 1 -> copy(audioUrls = audioUrls.drop(1))
        else -> null
    }
}
