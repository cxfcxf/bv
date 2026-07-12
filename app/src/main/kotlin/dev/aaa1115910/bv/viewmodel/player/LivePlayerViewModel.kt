package dev.aaa1115910.bv.viewmodel.player

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kuaishou.akdanmaku.DanmakuConfig
import com.kuaishou.akdanmaku.data.DanmakuItemData
import com.kuaishou.akdanmaku.render.SimpleRenderer
import com.kuaishou.akdanmaku.ui.DanmakuPlayer
import dev.aaa1115910.biliapi.entity.ApiType
import dev.aaa1115910.biliapi.http.entity.live.DanmakuEvent
import dev.aaa1115910.biliapi.repositories.AuthRepository
import dev.aaa1115910.biliapi.repositories.LiveRepository
import dev.aaa1115910.biliapi.websocket.LiveDataWebSocket
import dev.aaa1115910.bv.BVApp
import dev.aaa1115910.bv.R
import dev.aaa1115910.bv.entity.CdnType
import dev.aaa1115910.bv.player.AbstractVideoPlayer
import dev.aaa1115910.bv.player.VideoPlayerListener
import dev.aaa1115910.bv.player.VideoPlayerOptions
import dev.aaa1115910.bv.player.impl.exo.ExoPlayerFactory
import dev.aaa1115910.bv.util.Prefs
import dev.aaa1115910.bv.util.fInfo
import dev.aaa1115910.bv.util.fWarn
import dev.aaa1115910.bv.util.toast
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.annotation.KoinViewModel
import kotlin.random.Random

@KoinViewModel
class LivePlayerViewModel(
    private val liveRepository: LiveRepository,
    private val authRepository: AuthRepository
) : ViewModel() {
    private val logger = KotlinLogging.logger {}

    var videoPlayer: AbstractVideoPlayer? by mutableStateOf(null)
    var danmakuPlayer: DanmakuPlayer? by mutableStateOf(null)

    var roomId = 0L
    var title = ""
    var uname = ""

    var loading by mutableStateOf(true)
    var buffering by mutableStateOf(false)
    var reconnecting by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)

    // 视频实际尺寸，用于竖屏直播（手机开播）的画面比例适配
    var videoWidth by mutableStateOf(0)
        private set
    var videoHeight by mutableStateOf(0)
        private set

    // 清晰度选单数据
    var currentQuality by mutableStateOf(Prefs.liveQuality)
        private set
    var availableQualities by mutableStateOf(listOf<Int>())
        private set
    var qualityDescMap by mutableStateOf(mapOf<Int, String>())
        private set

    private var danmakuJob: Job? = null
    private var reconnectJob: Job? = null
    private var bufferingWatchdogJob: Job? = null
    private var danmakuId = 0L
    private var reconnectCount = 0
    private var released = false
    private var selectedApiType = ApiType.Web

    // 重连时轮换 CDN 节点（仅 CDN 设置为"自动"时启用），避免反复使用同一个较差的节点
    private var urlRotation = 0

    // 海外访问时 CDN 往往撑不住原画码率，短时间内频繁断流时自动降低清晰度
    private val recentFailures = ArrayDeque<Long>()

    private val playerListener = object : VideoPlayerListener {
        override fun onError(error: Exception) {
            logger.fWarn { "Live player error: ${error.stackTraceToString()}" }
            reconnect()
        }

        override fun onReady() {
            bufferingWatchdogJob?.cancel()
            buffering = false
            reconnecting = false
            videoWidth = videoPlayer?.videoWidth ?: 0
            videoHeight = videoPlayer?.videoHeight ?: 0
        }

        override fun onPlay() {
            bufferingWatchdogJob?.cancel()
            buffering = false
            reconnectCount = 0
        }

        override fun onPause() {}

        override fun onBuffering() {
            buffering = true
            startBufferingWatchdog()
        }

        override fun onEnd() {
            // 直播流被中断时 ExoPlayer 会进入 ENDED 状态，尝试重新拉流
            reconnect()
        }

        override fun onSeekBack(seekBackIncrementMs: Long) {}
        override fun onSeekForward(seekForwardIncrementMs: Long) {}
    }

    fun initPlayer(context: Context) {
        released = false
        selectedApiType = Prefs.apiType
        videoPlayer?.release()
        val options = VideoPlayerOptions(
            userAgent = when (selectedApiType) {
                ApiType.Web -> context.getString(R.string.video_player_user_agent_http)
                ApiType.App -> context.getString(R.string.video_player_user_agent_client)
            },
            referer = when (selectedApiType) {
                ApiType.Web -> "https://live.bilibili.com/"
                ApiType.App -> null
            },
            enableFfmpegAudioRenderer = Prefs.enableFfmpegAudioRenderer,
            enableSoftwareVideoDecoder = Prefs.enableSoftwareVideoDecoder
        )
        videoPlayer = ExoPlayerFactory().create(context.applicationContext, options).apply {
            setPlayerEventListener(playerListener)
        }

        danmakuPlayer?.release()
        danmakuPlayer = DanmakuPlayer(SimpleRenderer()).apply {
            val config = DanmakuConfig(
                density = 120,
                textSizeScale = Prefs.defaultDanmakuScale,
                screenPart = Prefs.defaultDanmakuArea,
                rollingSpeedFactor = Prefs.defaultDanmakuSpeedFactor
            )
            updateConfig(config)
            start(config)
        }
    }

    fun loadAndPlay() {
        viewModelScope.launch(Dispatchers.IO) { loadStream() }
        connectDanmaku()
    }

    private suspend fun loadStream() {
        runCatching {
            val streamInfo = liveRepository.getLiveStream(
                roomId = roomId,
                qn = currentQuality,
                preferApiType = selectedApiType
            )
            if (!streamInfo.living) {
                withContext(Dispatchers.Main) { errorMessage = "主播已下播" }
                return
            }
            withContext(Dispatchers.Main) {
                availableQualities = streamInfo.acceptQualities
                qualityDescMap = streamInfo.qualityDescMap
                // 服务端可能不支持请求的清晰度而返回其他值，以实际返回为准
                currentQuality = streamInfo.currentQuality
            }
            // 优先 fmp4 HLS，其次 ts HLS，最后 FLV；均优先 avc 编码以保证兼容性
            val candidates = streamInfo.urls.sortedWith(
                compareBy(
                    { it.codec != "avc" },
                    {
                        when (it.format) {
                            "fmp4" -> 0
                            "ts" -> 1
                            else -> 2
                        }
                    }
                )
            )
            if (candidates.isEmpty()) throw IllegalStateException("没有可用的直播流")
            val candidate = candidates[urlRotation % candidates.size]
            val url = candidate.url

            logger.fInfo {
                "Play live stream: room=$roomId api=$selectedApiType " +
                        "qn=${streamInfo.currentQuality} " +
                        "format=${candidate.format} rotation=$urlRotation/${candidates.size}"
            }
            logger.info { "Live stream url: $url" }
            withContext(Dispatchers.Main) {
                errorMessage = null
                videoPlayer?.playLiveUrl(url)
                videoPlayer?.prepare()
                videoPlayer?.start()
                loading = false
            }
        }.onFailure {
            logger.fWarn { "Load live stream failed: ${it.stackTraceToString()}" }
            withContext(Dispatchers.Main) {
                if (reconnecting) {
                    // Wait for the current retry coroutine to finish before scheduling the next
                    // attempt. Otherwise reconnect() would correctly treat it as a duplicate.
                    val failedReconnectJob = reconnectJob
                    viewModelScope.launch {
                        failedReconnectJob?.join()
                        reconnecting = false
                        reconnect()
                    }
                } else {
                    errorMessage = it.localizedMessage
                    "加载直播失败: ${it.localizedMessage}".toast(BVApp.context)
                }
            }
        }
    }

    /**
     * 直播流地址会过期，播放出错或流中断时重新拉流恢复播放
     */
    private fun reconnect() {
        if (released || reconnectJob?.isActive == true) return
        if (reconnectCount >= 5) {
            errorMessage = "直播连接已断开，请退出重试"
            reconnecting = false
            return
        }
        reconnecting = true
        buffering = false
        bufferingWatchdogJob?.cancel()
        reconnectCount++
        // CDN 设置为"自动"时换一个节点重试，指定了 CDN 则固定使用默认节点
        if (Prefs.preferredCdn == CdnType.Auto) urlRotation++
        maybeDowngradeQuality()
        logger.fInfo { "Reconnect live stream: room=$roomId attempt=$reconnectCount" }
        reconnectJob = viewModelScope.launch(Dispatchers.IO) {
            delay(1000L * reconnectCount)
            if (!released) loadStream()
        }
    }

    /**
     * 短暂播放成功会重置重连计数，因此单靠计数无法发现"播几秒就断"的循环。
     * 60 秒内断流 3 次即认为当前清晰度码率超出网络承载能力，自动降低清晰度。
     */
    private fun maybeDowngradeQuality() {
        val now = System.currentTimeMillis()
        recentFailures.addLast(now)
        while (recentFailures.isNotEmpty() && now - recentFailures.first() > 60_000) {
            recentFailures.removeFirst()
        }
        if (recentFailures.size < 3) return
        val lowerQn = availableQualities.filter { it < currentQuality }.maxOrNull() ?: return
        recentFailures.clear()
        val desc = qualityDescMap[lowerQn] ?: lowerQn.toString()
        logger.fInfo { "Downgrade live quality to $lowerQn ($desc)" }
        viewModelScope.launch(Dispatchers.Main) {
            // 自动降级不写入偏好设置，下次进入直播间仍使用手动选择的清晰度
            currentQuality = lowerQn
            "直播不稳定，已自动切换至$desc".toast(BVApp.context)
        }
    }

    /**
     * 手动切换清晰度，同时记住选择，下次进入直播间沿用
     */
    fun switchQuality(qn: Int) {
        if (qn == currentQuality) return
        currentQuality = qn
        Prefs.liveQuality = qn
        recentFailures.clear()
        reconnectCount = 0
        urlRotation = 0
        reconnectJob?.cancel()
        bufferingWatchdogJob?.cancel()
        reconnecting = false
        buffering = true
        logger.fInfo { "Switch live quality to $qn" }
        viewModelScope.launch(Dispatchers.IO) { loadStream() }
    }

    /**
     * ExoPlayer can remain in BUFFERING forever without producing an error. If no playable data
     * arrives within the timeout, obtain a fresh (signed) live URL and prepare the player again.
     */
    private fun startBufferingWatchdog() {
        bufferingWatchdogJob?.cancel()
        bufferingWatchdogJob = viewModelScope.launch {
            delay(BUFFERING_TIMEOUT_MS)
            if (buffering && !released) {
                logger.fWarn { "Live stream buffering timed out after ${BUFFERING_TIMEOUT_MS}ms" }
                reconnect()
            }
        }
    }

    private fun connectDanmaku() {
        danmakuJob?.cancel()
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                danmakuJob = LiveDataWebSocket.connectLiveEvent(
                    roomId = roomId.toInt(),
                    uid = authRepository.mid ?: 0,
                    buvid = authRepository.buvid3 ?: "",
                    sessData = authRepository.sessionData
                ) { event ->
                    when (event) {
                        is DanmakuEvent -> {
                            val player = danmakuPlayer ?: return@connectLiveEvent
                            if (danmakuId < 5) logger.fInfo { "Live danmaku: ${event.content}" }
                            player.send(
                                DanmakuItemData(
                                    danmakuId = danmakuId++,
                                    position = player.getCurrentTimeMs() + 100,
                                    content = event.content,
                                    mode = DanmakuItemData.DANMAKU_MODE_ROLLING,
                                    textSize = 25,
                                    textColor = android.graphics.Color.WHITE,
                                    score = Random.nextInt(0, 100),
                                    danmakuStyle = DanmakuItemData.DANMAKU_STYLE_NONE
                                )
                            )
                        }
                    }
                }
            }.onFailure {
                logger.fWarn { "Connect live danmaku failed: ${it.stackTraceToString()}" }
            }
        }
    }

    fun release() {
        released = true
        reconnectJob?.cancel()
        reconnectJob = null
        bufferingWatchdogJob?.cancel()
        bufferingWatchdogJob = null
        danmakuJob?.cancel()
        danmakuJob = null
        danmakuPlayer?.release()
        danmakuPlayer = null
        videoPlayer?.release()
        videoPlayer = null
    }

    private companion object {
        const val BUFFERING_TIMEOUT_MS = 15_000L
    }
}
