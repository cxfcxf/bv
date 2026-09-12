package dev.aaa1115910.bv.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlaybackSourcesTest {
    private val sources = PlaybackSources(listOf("video-a", "video-b"), listOf("audio-a", "audio-b"))

    @Test
    fun `video failure keeps working audio and exhausts video mirrors`() {
        val next = sources.afterFailure("video-a")!!
        assertEquals("video-b", next.videoUrl)
        assertEquals("audio-a", next.audioUrl)
        assertNull(next.afterFailure("video-b"))
        assertNull(next.afterFailure("video-a"))
    }

    @Test
    fun `audio failure keeps video and can recover after video failover`() {
        val next = sources.afterFailure("video-a")!!.afterFailure("audio-a")!!
        assertEquals("video-b", next.videoUrl)
        assertEquals("audio-b", next.audioUrl)
        assertNull(next.afterFailure("audio-b"))
    }

    @Test
    fun `unrelated failures and sources without backups do not retry`() {
        assertNull(sources.afterFailure("unrelated"))
        assertNull(PlaybackSources(listOf("video")).afterFailure("video"))
    }
}
