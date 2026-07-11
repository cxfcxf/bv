package dev.aaa1115910.bv.activities.video

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.aaa1115910.bv.screen.LivePlayerScreen
import dev.aaa1115910.bv.ui.theme.BVTheme
import dev.aaa1115910.bv.viewmodel.player.LivePlayerViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel

class LivePlayerActivity : ComponentActivity() {
    private val playerViewModel: LivePlayerViewModel by viewModel()

    companion object {
        fun actionStart(
            context: Context,
            roomId: Long,
            title: String = "",
            uname: String = ""
        ) {
            context.startActivity(
                Intent(context, LivePlayerActivity::class.java).apply {
                    putExtra("roomId", roomId)
                    putExtra("title", title)
                    putExtra("uname", uname)
                }
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        playerViewModel.roomId = intent.getLongExtra("roomId", 0)
        playerViewModel.title = intent.getStringExtra("title") ?: ""
        playerViewModel.uname = intent.getStringExtra("uname") ?: ""

        playerViewModel.initPlayer(applicationContext)
        playerViewModel.loadAndPlay()

        setContent {
            BVTheme {
                LivePlayerScreen()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onPause() {
        super.onPause()
        WindowInsetsControllerCompat(window, window.decorView)
            .show(WindowInsetsCompat.Type.systemBars())
        // 直播无法暂停恢复到原进度，退到后台直接停止播放
        playerViewModel.videoPlayer?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        playerViewModel.release()
    }
}
