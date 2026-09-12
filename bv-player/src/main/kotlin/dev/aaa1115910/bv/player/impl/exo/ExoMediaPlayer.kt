package dev.aaa1115910.bv.player.impl.exo

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.dash.manifest.AdaptationSet
import androidx.media3.exoplayer.dash.manifest.BaseUrl
import androidx.media3.exoplayer.dash.manifest.DashManifest
import androidx.media3.exoplayer.dash.manifest.Period
import androidx.media3.exoplayer.dash.manifest.RangedUri
import androidx.media3.exoplayer.dash.manifest.Representation
import androidx.media3.exoplayer.dash.manifest.SegmentBase
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import dev.aaa1115910.bv.player.AbstractVideoPlayer
import dev.aaa1115910.bv.player.DashTrack
import dev.aaa1115910.bv.player.OkHttpUtil
import dev.aaa1115910.bv.player.VideoPlayerOptions
import dev.aaa1115910.bv.player.formatMinSec

@OptIn(UnstableApi::class)
class ExoMediaPlayer(
    private val context: Context,
    private val options: VideoPlayerOptions
) : AbstractVideoPlayer(), Player.Listener {
    var mPlayer: ExoPlayer? = null
    protected var mMediaSource: MediaSource? = null

    @OptIn(UnstableApi::class)
    private val dataSourceFactory =
        OkHttpDataSource.Factory(OkHttpUtil.generateCustomSslOkHttpClient(context)).apply {
            options.userAgent?.let { setUserAgent(it) }
            options.referer?.let { setDefaultRequestProperties(mapOf("referer" to it)) }
        }

    init {
        initPlayer()
    }

    @OptIn(UnstableApi::class)
    override fun initPlayer() {
        val renderersFactory = DefaultRenderersFactory(context).apply {
            setExtensionRendererMode(
                when (options.enableFfmpegAudioRenderer) {
                    true -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
                    false -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF
                }
            )
            if (options.enableSoftwareVideoDecoder) {
                // 强制软件解码
                setMediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
                    val allDecoders = MediaCodecUtil.getDecoderInfos(
                        mimeType,
                        requiresSecureDecoder,
                        requiresTunnelingDecoder
                    )
                    val softwareDecoders = allDecoders.filter {
                        it.name.startsWith("OMX.google.") || it.name.startsWith("c2.android.")
                    }
                    // 兜底回退
                    softwareDecoders.ifEmpty { allDecoders }
                }

            } else {
                // 默认硬件解码
                setMediaCodecSelector(MediaCodecSelector.DEFAULT)
            }
        }
        // 默认只缓冲 50s。B 站 CDN 对单条长连接限速，缓冲一满播放器就停止读取，
        // 连接随即被限到接近码率，之后缓冲始终贴着播放位置，稍有抖动就卡顿。
        // 放宽到 120s 让播放器持续下载，同时按堆上限限制缓冲字节数，
        // 避免高码率视频把堆撑爆（SHIELD 的 heapgrowthlimit 只有 192MB）。
        val targetBufferBytes = minOf(
            MAX_TARGET_BUFFER_BYTES,
            Runtime.getRuntime().maxMemory() / 4
        ).toInt()
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                BUFFER_MS,
                BUFFER_MS,
                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
                BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
            )
            .setTargetBufferBytes(targetBufferBytes)
            .build()

        mPlayer = ExoPlayer
            .Builder(context)
            .setRenderersFactory(renderersFactory)
            .setLoadControl(loadControl)
            .setSeekForwardIncrementMs(1000 * 10)
            .setSeekBackIncrementMs(1000 * 5)
            // 默认 500ms 在电视盒子上不够硬件解码器完成释放，超时会强杀播放线程，
            // 导致 DisplayListener/MediaCodec 泄漏，长时间使用后越来越卡
            .setReleaseTimeoutMs(5_000)
            .build()

        initListener()
    }

    private fun initListener() {
        mPlayer?.addListener(this)
    }

    @OptIn(UnstableApi::class)
    override fun setHeader(headers: Map<String, String>) {

    }

    @OptIn(UnstableApi::class)
    override fun playUrl(videoUrl: String?, audioUrl: String?) {
        val videoMediaSource = videoUrl?.let {
            ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(it))
        }
        val audioMediaSource = audioUrl?.let {
            ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(it))
        }

        val mediaSources = listOfNotNull(videoMediaSource, audioMediaSource)
        mMediaSource = MergingMediaSource(*mediaSources.toTypedArray())
    }

    @OptIn(UnstableApi::class)
    override fun playLiveUrl(url: String) {
        mMediaSource = if (url.contains(".m3u8")) {
            HlsMediaSource.Factory(dataSourceFactory)
                .setAllowChunklessPreparation(true)
                .createMediaSource(MediaItem.fromUri(url))
        } else {
            ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(url))
        }
    }

    @OptIn(UnstableApi::class)
    override fun playDash(videos: List<DashTrack>, audio: DashTrack?, durationMs: Long) {
        require(videos.isNotEmpty()) { "playDash requires at least one video track" }
        val adaptationSets = buildList {
            // 同一 AdaptationSet 内的多条 representation 才能被自适应切换
            add(videos.toAdaptationSet(id = 0, trackType = C.TRACK_TYPE_VIDEO))
            audio?.let { add(listOf(it).toAdaptationSet(id = 1, trackType = C.TRACK_TYPE_AUDIO)) }
        }
        val manifest = DashManifest(
            /* availabilityStartTimeMs = */ C.TIME_UNSET,
            /* durationMs = */ durationMs,
            /* minBufferTimeMs = */ C.TIME_UNSET,
            /* dynamic = */ false,
            /* minUpdatePeriodMs = */ C.TIME_UNSET,
            /* timeShiftBufferDepthMs = */ C.TIME_UNSET,
            /* suggestedPresentationDelayMs = */ C.TIME_UNSET,
            /* publishTimeMs = */ C.TIME_UNSET,
            /* programInformation = */ null,
            /* utcTiming = */ null,
            /* serviceDescription = */ null,
            /* location = */ null,
            /* periods = */ listOf(Period(null, 0, adaptationSets))
        )
        mMediaSource = DashMediaSource.Factory(dataSourceFactory).createMediaSource(manifest)
    }

    @OptIn(UnstableApi::class)
    private fun List<DashTrack>.toAdaptationSet(id: Int, trackType: Int): AdaptationSet {
        val representations = mapIndexed { index, track ->
            track.toRepresentation(id = "$id-$index", trackType = trackType)
        }
        return AdaptationSet(
            id.toLong(), trackType, representations,
            emptyList(), emptyList(), emptyList()
        )
    }

    @OptIn(UnstableApi::class)
    private fun DashTrack.toRepresentation(id: String, trackType: Int): Representation {
        val containerMimeType =
            if (trackType == C.TRACK_TYPE_VIDEO) MimeTypes.VIDEO_MP4 else MimeTypes.AUDIO_MP4
        val format = Format.Builder()
            .setId(id)
            .setContainerMimeType(containerMimeType)
            .setSampleMimeType(codecs?.let { MimeTypes.getMediaMimeType(it) })
            .setCodecs(codecs)
            .setAverageBitrate(bandwidth)
            .setPeakBitrate(bandwidth)
            .apply {
                if (trackType == C.TRACK_TYPE_VIDEO) {
                    setWidth(width)
                    setHeight(height)
                    if (frameRate > 0f) setFrameRate(frameRate)
                }
            }
            .build()
        val (initStart, initLength) = parseByteRange(initializationRange)
        val (indexStart, indexLength) = parseByteRange(indexRange)
        val segmentBase = SegmentBase.SingleSegmentBase(
            /* initialization = */ RangedUri(null, initStart, initLength),
            /* timescale = */ 1,
            /* presentationTimeOffset = */ 0,
            /* indexStart = */ indexStart,
            /* indexLength = */ indexLength
        )
        return Representation.newInstance(
            /* revisionId = */ Representation.REVISION_ID_DEFAULT,
            format,
            urls.map { BaseUrl(it) },
            segmentBase
        )
    }

    /** "1235-5678" -> start 1235, length 4444 */
    private fun parseByteRange(range: String): Pair<Long, Long> {
        val separator = range.indexOf('-')
        require(separator > 0) { "Malformed byte range: $range" }
        val start = range.substring(0, separator).toLong()
        val end = range.substring(separator + 1).toLong()
        return start to (end - start + 1)
    }

    @OptIn(UnstableApi::class)
    override fun prepare() {
        mPlayer?.setMediaSource(mMediaSource!!)
        mPlayer?.prepare()
    }

    override fun start() {
        mPlayer?.play()
    }

    override fun pause() {
        mPlayer?.pause()
    }

    override fun stop() {
        mPlayer?.stop()
    }

    override fun reset() {
        TODO("Not yet implemented")
    }

    override val isPlaying: Boolean
        get() = mPlayer?.isPlaying == true

    override fun seekTo(time: Long) {
        mPlayer?.seekTo(time)
    }

    override fun release() {
        mPlayer?.release()
    }

    override val currentPosition: Long
        get() = mPlayer?.currentPosition ?: 0
    override val duration: Long
        get() = mPlayer?.duration ?: 0
    override val bufferedPercentage: Int
        get() = mPlayer?.bufferedPercentage ?: 0

    override fun setOptions() {
        mPlayer?.playWhenReady = true
    }

    override var speed: Float
        get() = mPlayer?.playbackParameters?.speed ?: 1f
        set(value) {
            mPlayer?.setPlaybackSpeed(value)
        }
    override val tcpSpeed: Long
        get() = 0L

    override fun onPlaybackStateChanged(playbackState: Int) {
        when (playbackState) {
            Player.STATE_IDLE -> {}
            Player.STATE_BUFFERING -> mPlayerEventListener?.onBuffering()
            Player.STATE_READY -> mPlayerEventListener?.onReady()
            Player.STATE_ENDED -> mPlayerEventListener?.onEnd()
        }
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying) {
            mPlayerEventListener?.onPlay()
        } else {
            mPlayerEventListener?.onPause()
        }
    }

    override fun onSeekBackIncrementChanged(seekBackIncrementMs: Long) {
        mPlayerEventListener?.onSeekBack(seekBackIncrementMs)
    }

    override fun onSeekForwardIncrementChanged(seekForwardIncrementMs: Long) {
        mPlayerEventListener?.onSeekForward(seekForwardIncrementMs)
    }

    override val debugInfo: String
        get() {
            return """
                player: ${androidx.media3.common.MediaLibraryInfo.VERSION_SLASHY}
                time: ${currentPosition.formatMinSec()} / ${duration.formatMinSec()}
                buffered: $bufferedPercentage%
                resolution: ${mPlayer?.videoSize?.width} x ${mPlayer?.videoSize?.height}
                audio: ${mPlayer?.audioFormat?.bitrate ?: 0} kbps
                video codec: ${mPlayer?.videoFormat?.sampleMimeType ?: "null"}
                audio codec: ${mPlayer?.audioFormat?.sampleMimeType ?: "null"} (${getAudioRendererName()})
            """.trimIndent()
        }

    private fun getAudioRendererName(): String {
        val rendererCount = mPlayer?.rendererCount ?: return "UnknownRenderer"
        for (i in 0 until rendererCount) {
            val renderer = mPlayer!!.getRenderer(i)
            if (renderer.trackType == C.TRACK_TYPE_AUDIO && renderer.state == Renderer.STATE_STARTED) {
                return renderer.name
            }
        }
        return "UnknownRenderer"
    }

    override val videoWidth: Int
        get() = mPlayer?.videoSize?.width ?: 0
    override val videoHeight: Int
        get() = mPlayer?.videoSize?.height ?: 0

    override fun onPlayerError(error: PlaybackException) {
        mPlayerEventListener?.onError(error)
    }

    companion object {
        private const val BUFFER_MS = 120_000
        private const val BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 5_000
        private const val MAX_TARGET_BUFFER_BYTES = 64L * 1024 * 1024
    }
}
