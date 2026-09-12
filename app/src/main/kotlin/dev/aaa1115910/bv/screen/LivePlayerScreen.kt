package dev.aaa1115910.bv.screen

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.aaa1115910.bv.component.DanmakuPlayerCompose
import dev.aaa1115910.bv.component.LoadingTip
import dev.aaa1115910.bv.component.settings.SettingsMenuSelectItem
import dev.aaa1115910.bv.player.BvVideoPlayer
import dev.aaa1115910.bv.viewmodel.player.LivePlayerViewModel
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel
import dev.aaa1115910.bv.ui.theme.LocalFocusColor

@Composable
fun LivePlayerScreen(
    modifier: Modifier = Modifier,
    playerViewModel: LivePlayerViewModel = koinViewModel()
) {
    var showInfo by remember { mutableStateOf(true) }
    var showQualityMenu by remember { mutableStateOf(false) }
    val rootFocusRequester = remember { FocusRequester() }
    val menuFocusRequester = remember { FocusRequester() }

    // 进入后显示 5 秒房间信息，随后自动隐藏
    LaunchedEffect(playerViewModel.loading) {
        if (!playerViewModel.loading) {
            delay(5000)
            showInfo = false
        }
    }

    LaunchedEffect(Unit) {
        rootFocusRequester.requestFocus()
    }

    // 菜单打开时聚焦当前清晰度项，关闭后交还按键焦点
    LaunchedEffect(showQualityMenu) {
        if (showQualityMenu) {
            runCatching { menuFocusRequester.requestFocus() }
        } else {
            runCatching { rootFocusRequester.requestFocus() }
        }
    }

    BackHandler(showQualityMenu) {
        showQualityMenu = false
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (showQualityMenu) {
                    // 拦截返回键直接关闭选单，避免第一次按键只是让菜单项失焦
                    if (event.key == Key.Back) {
                        if (event.type == KeyEventType.KeyUp) showQualityMenu = false
                        return@onPreviewKeyEvent true
                    }
                    return@onPreviewKeyEvent false
                }
                if (event.type == KeyEventType.KeyUp &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter ||
                            event.key == Key.Menu)
                ) {
                    if (playerViewModel.availableQualities.isNotEmpty()) {
                        showQualityMenu = true
                    }
                    return@onPreviewKeyEvent true
                }
                false
            }
    ) {
        playerViewModel.videoPlayer?.let { player ->
            // 竖屏直播（手机开播）按实际画面比例居中显示，避免被拉伸铺满
            val aspectRatio =
                if (playerViewModel.videoWidth > 0 && playerViewModel.videoHeight > 0) {
                    playerViewModel.videoWidth / playerViewModel.videoHeight.toFloat()
                } else 16 / 9f
            BvVideoPlayer(
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(aspectRatio)
                    .align(Alignment.Center),
                videoPlayer = player
            )
        }

        DanmakuPlayerCompose(
            modifier = Modifier.fillMaxSize(),
            danmakuPlayer = playerViewModel.danmakuPlayer
        )

        if (playerViewModel.loading && playerViewModel.errorMessage == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                LoadingTip()
            }
        }

        // 缓冲/重连状态提示
        if (!playerViewModel.loading &&
            (playerViewModel.buffering || playerViewModel.reconnecting) &&
            playerViewModel.errorMessage == null
        ) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .background(
                        Color.Black.copy(alpha = 0.6f),
                        MaterialTheme.shapes.medium
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = LocalFocusColor.current,
                    strokeWidth = 2.dp
                )
                Text(
                    text = if (playerViewModel.reconnecting) "直播中断，重新连接中..." else "缓冲中...",
                    fontSize = 14.sp,
                    color = Color.White
                )
            }
        }

        playerViewModel.errorMessage?.let { message ->
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = message,
                    fontSize = 20.sp,
                    color = Color.White
                )
            }
        }

        AnimatedVisibility(
            modifier = Modifier.align(Alignment.BottomStart),
            visible = showInfo,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                        )
                    )
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Text(
                    text = playerViewModel.title,
                    fontSize = 20.sp,
                    color = Color.White
                )
                Text(
                    text = playerViewModel.uname,
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
        }

        // 清晰度选单，按 OK/菜单键打开
        AnimatedVisibility(
            modifier = Modifier.align(Alignment.CenterEnd),
            visible = showQualityMenu,
            enter = slideInHorizontally { it },
            exit = slideOutHorizontally { it }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(240.dp)
                    .background(Color.Black.copy(alpha = 0.85f))
                    .padding(horizontal = 12.dp, vertical = 24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    modifier = Modifier.padding(start = 12.dp, bottom = 12.dp),
                    text = "清晰度",
                    fontSize = 18.sp,
                    color = Color.White
                )
                // 初始焦点放在当前清晰度上，当前值不在列表中时回退到第一项
                val focusQn = playerViewModel.currentQuality
                    .takeIf { playerViewModel.availableQualities.contains(it) }
                    ?: playerViewModel.availableQualities.firstOrNull()
                playerViewModel.availableQualities.forEach { qn ->
                    val selected = qn == playerViewModel.currentQuality
                    SettingsMenuSelectItem(
                        modifier = if (qn == focusQn) {
                            Modifier.focusRequester(menuFocusRequester)
                        } else Modifier,
                        text = playerViewModel.qualityDescMap[qn] ?: qn.toString(),
                        selected = selected,
                        onClick = {
                            showQualityMenu = false
                            playerViewModel.switchQuality(qn)
                        }
                    )
                }
            }
        }
    }
}
