package dev.aaa1115910.bv.screen.main

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import dev.aaa1115910.biliapi.entity.live.LiveArea
import dev.aaa1115910.bv.activities.video.LivePlayerActivity
import dev.aaa1115910.bv.component.LoadingTip
import dev.aaa1115910.bv.component.TopNav
import dev.aaa1115910.bv.component.TopNavItem
import dev.aaa1115910.bv.component.TvLazyVerticalGrid
import dev.aaa1115910.bv.component.videocard.LiveRoomCard
import dev.aaa1115910.bv.viewmodel.home.LiveViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

private data class LiveNavItem(
    val area: LiveArea?
) : TopNavItem {
    override fun getDisplayName(context: Context): String = area?.name ?: "关注"
}

@Composable
fun LiveContent(
    modifier: Modifier = Modifier,
    navFocusRequester: FocusRequester,
    liveViewModel: LiveViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()
    var focusOnContent by remember { mutableStateOf(false) }

    val navItems = remember(liveViewModel.areas.size) {
        listOf(LiveNavItem(null)) + liveViewModel.areas.map { LiveNavItem(it) }
    }

    LaunchedEffect(Unit) {
        scope.launch(Dispatchers.IO) {
            liveViewModel.loadAreas()
            if (liveViewModel.liveRoomList.isEmpty()) liveViewModel.loadMore()
        }
    }

    LaunchedEffect(gridState, liveViewModel.currentArea) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .distinctUntilChanged()
            .filter { index ->
                index != null && index >= liveViewModel.liveRoomList.size - 8
            }
            .collect {
                scope.launch(Dispatchers.IO) { liveViewModel.loadMore() }
            }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopNav(
                modifier = Modifier.focusRequester(navFocusRequester),
                items = navItems,
                isLargePadding = !focusOnContent,
                onSelectedChanged = { nav ->
                    val item = nav as LiveNavItem
                    liveViewModel.switchArea(item.area)
                    scope.launch(Dispatchers.IO) { liveViewModel.loadMore() }
                },
                onClick = {
                    liveViewModel.clearRooms()
                    scope.launch(Dispatchers.IO) { liveViewModel.loadMore() }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .onFocusChanged { focusOnContent = it.hasFocus }
                .onPreviewKeyEvent {
                    if (it.key == Key.Menu) {
                        if (it.type == KeyEventType.KeyDown) return@onPreviewKeyEvent true
                        liveViewModel.clearRooms()
                        scope.launch(Dispatchers.IO) { liveViewModel.loadMore() }
                        navFocusRequester.requestFocus()
                        return@onPreviewKeyEvent true
                    }
                    false
                }
        ) {
            if (liveViewModel.currentArea != null || liveViewModel.isLogin) {
                TvLazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(4),
                    contentPadding = PaddingValues(24.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(
                        items = liveViewModel.liveRoomList,
                        key = { _, room -> room.roomId }
                    ) { _, room ->
                        LiveRoomCard(
                            room = room,
                            onClick = {
                                LivePlayerActivity.actionStart(
                                    context = context,
                                    roomId = room.roomId,
                                    title = room.title,
                                    uname = room.uname
                                )
                            }
                        )
                    }

                    if (liveViewModel.loading)
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                LoadingTip()
                            }
                        }

                    if (!liveViewModel.loading && liveViewModel.liveRoomList.isEmpty())
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 120.dp),
                                textAlign = TextAlign.Center,
                                text = if (liveViewModel.currentArea == null) {
                                    "关注的主播都不在直播捏"
                                } else {
                                    "这个分区没有直播捏"
                                },
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        }
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "请先登录")
                }
            }
        }
    }
}
