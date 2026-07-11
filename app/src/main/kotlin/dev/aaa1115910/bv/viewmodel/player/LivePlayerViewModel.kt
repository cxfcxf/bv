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
import dev.aaa1115910.biliapi.http.entity.live.DanmakuEvent
import dev.aaa1115910.biliapi.repositories.AuthRepository
import dev.aaa1115910.biliapi.repositories.LiveRepository
import dev.aaa1115910.biliapi.websocket.LiveDataWebSocket
import dev.aaa1115910.bv.BVApp
import dev.aaa1115910.bv.R
import dev.aaa1115910.bv.player.AbstractVideoPlayer
import dev.aaa1115910.bv.player.VideoPlayerOptions
import dev.aaa1115910.bv.player.impl.exo.ExoPlayerFactory
import dev.aaa1115910.bv.util.Prefs
import dev.aaa1115910.bv.util.fInfo
import dev.aaa1115910.bv.util.fWarn
import dev.aaa1115910.bv.util.toast
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    var errorMessage by mutableStateOf<String?>(null)

    private var danmakuJob: Job? = null
    private var danmakuId = 0L

    fun initPlayer(context: Context) {
        videoPlayer?.release()
        val options = VideoPlayerOptions(
            userAgent = context.getString(R.string.video_player_user_agent_http),
            referer = "https://live.bilibili.com/",
            enableFfmpegAudioRenderer = Prefs.enableFfmpegAudioRenderer,
            enableSoftwareVideoDecoder = Prefs.enableSoftwareVideoDecoder
        )
        videoPlayer = ExoPlayerFactory().create(context.applicationContext, options)

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
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val streamInfo = liveRepository.getLiveStream(roomId)
                if (!streamInfo.living) {
                    withContext(Dispatchers.Main) { errorMessage = "主播已下播" }
                    return@launch
                }
                // 优先 fmp4 HLS，其次 ts HLS，最后 FLV；均优先 avc 编码以保证兼容性
                val url = streamInfo.urls.sortedWith(
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
                ).firstOrNull()?.url ?: throw IllegalStateException("没有可用的直播流")

                logger.fInfo { "Play live stream: room=$roomId qn=${streamInfo.currentQuality}" }
                logger.info { "Live stream url: $url" }
                withContext(Dispatchers.Main) {
                    videoPlayer?.playLiveUrl(url)
                    videoPlayer?.prepare()
                    videoPlayer?.start()
                    loading = false
                }
            }.onFailure {
                logger.fWarn { "Load live stream failed: ${it.stackTraceToString()}" }
                withContext(Dispatchers.Main) {
                    errorMessage = it.localizedMessage
                    "加载直播失败: ${it.localizedMessage}".toast(BVApp.context)
                }
            }
        }
        connectDanmaku()
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
        danmakuJob?.cancel()
        danmakuJob = null
        danmakuPlayer?.release()
        danmakuPlayer = null
        videoPlayer?.release()
        videoPlayer = null
    }
}
