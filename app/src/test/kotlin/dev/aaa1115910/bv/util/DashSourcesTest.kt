package dev.aaa1115910.bv.util

import dev.aaa1115910.bv.player.DashTrack
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DashSourcesTest {
    private fun track(vararg urls: String, height: Int = 1080) = DashTrack(
        urls = urls.toList(),
        codecs = "avc1.640032",
        bandwidth = 1_000_000,
        initializationRange = "0-100",
        indexRange = "101-200",
        height = height
    )

    private val akam = "https://akam.example.net/v.m4s"
    private val cosov = "https://cosov.example.com/v.m4s"

    private val sources = DashSources(
        videos = listOf(
            track(akam, cosov, height = 1080),
            track(akam, cosov, height = 720)
        ),
        audio = track(akam, cosov),
        durationMs = 600_000
    )

    @Test
    fun `dropping a host prunes every rendition and the audio track`() {
        val next = sources.withoutHost("akam.example.net")!!
        assertEquals(listOf(listOf(cosov), listOf(cosov)), next.videos.map { it.urls })
        assertEquals(listOf(cosov), next.audio!!.urls)
        // 清晰度档数和时长不受影响
        assertEquals(2, next.videos.size)
        assertEquals(600_000, next.durationMs)
    }

    @Test
    fun `dropping an unknown host reports nowhere left to go`() {
        assertNull(sources.withoutHost("other.example.org"))
    }

    @Test
    fun `a track with no alternative keeps its only host rather than emptying`() {
        val single = DashSources(
            videos = listOf(track(akam)),
            audio = track(akam, cosov),
            durationMs = 1_000
        )
        val next = single.withoutHost("akam.example.net")!!
        // 视频只有这一个地址，保留；音频还有备用，剔除
        assertEquals(listOf(akam), next.videos.single().urls)
        assertEquals(listOf(cosov), next.audio!!.urls)
    }

    @Test
    fun `exhausting every host stops the retry loop`() {
        val onlyAkam = DashSources(
            videos = listOf(track(akam)),
            audio = null,
            durationMs = 1_000
        )
        assertNull(onlyAkam.withoutHost("akam.example.net"))
    }

    @Test
    fun `cdnHost extracts the authority`() {
        assertEquals("akam.example.net", "https://akam.example.net/a/b.m4s?x=1".cdnHost())
        assertEquals("h.example.com", "http://h.example.com/a".cdnHost())
    }
}
