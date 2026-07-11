package dev.aaa1115910.bv.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import dev.aaa1115910.bv.component.DanmakuPlayerCompose
import dev.aaa1115910.bv.component.LoadingTip
import dev.aaa1115910.bv.player.BvVideoPlayer
import dev.aaa1115910.bv.viewmodel.player.LivePlayerViewModel
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel

@Composable
fun LivePlayerScreen(
    modifier: Modifier = Modifier,
    playerViewModel: LivePlayerViewModel = koinViewModel()
) {
    var showInfo by remember { mutableStateOf(true) }

    // 进入后显示 5 秒房间信息，随后自动隐藏
    LaunchedEffect(playerViewModel.loading) {
        if (!playerViewModel.loading) {
            delay(5000)
            showInfo = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        playerViewModel.videoPlayer?.let { player ->
            BvVideoPlayer(
                modifier = Modifier.fillMaxSize(),
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
    }
}
