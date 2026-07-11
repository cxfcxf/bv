package dev.aaa1115910.bv.viewmodel.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import dev.aaa1115910.biliapi.entity.live.LiveArea
import dev.aaa1115910.biliapi.entity.live.LiveRoom
import dev.aaa1115910.biliapi.repositories.LiveRepository
import dev.aaa1115910.bv.BVApp
import dev.aaa1115910.bv.util.addAllDistinctWithMainContext
import dev.aaa1115910.bv.util.fInfo
import dev.aaa1115910.bv.util.fWarn
import dev.aaa1115910.bv.util.toast
import androidx.lifecycle.viewModelScope
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dev.aaa1115910.bv.repository.UserRepository as BvUserRepository
import org.koin.android.annotation.KoinViewModel

@KoinViewModel
class LiveViewModel(
    private val bvUserRepository: BvUserRepository,
    private val liveRepository: LiveRepository
) : ViewModel() {
    private val logger = KotlinLogging.logger {}

    /** 一级分区列表，作为顶部标签页 */
    val areas = mutableStateListOf<LiveArea>()

    val liveRoomList = mutableStateListOf<LiveRoom>()

    /** 当前标签页，null 表示「关注」 */
    var currentArea: LiveArea? by mutableStateOf(null)
        private set

    private var currentPage = 0
    private var totalPage = 1
    var liveCount by mutableStateOf(0)
        private set
    var loading by mutableStateOf(false)
        private set
    var hasMore by mutableStateOf(true)
        private set

    val isLogin get() = bvUserRepository.isLogin

    private var loadJob: Job? = null

    /** 每次清空列表时递增，用于丢弃迟到的旧请求结果 */
    private var generation = 0

    suspend fun loadAreas() {
        if (areas.isNotEmpty()) return
        runCatching {
            val areaList = liveRepository.getLiveAreas()
            withContext(Dispatchers.Main) {
                areas.clear()
                areas.addAll(areaList)
            }
            logger.fInfo { "Loaded live areas: ${areaList.map { it.name }}" }
        }.onFailure {
            logger.fWarn { "Load live areas failed: ${it.stackTraceToString()}" }
            withContext(Dispatchers.Main) {
                "加载直播分区失败: ${it.localizedMessage}".toast(BVApp.context)
            }
        }
    }

    fun switchArea(area: LiveArea?) {
        if (area?.id == currentArea?.id) return
        currentArea = area
        clearRooms()
    }

    fun loadMore() {
        if (loading) return
        loadJob = viewModelScope.launch(Dispatchers.IO) { loadData() }
    }

    private suspend fun loadData() {
        if (!hasMore) return
        if (loading) return
        val area = currentArea
        if (area == null && !isLogin) return

        loading = true
        val nextPage = currentPage + 1
        val requestGeneration = generation

        try {
            if (area == null) {
                logger.fInfo { "Load following live rooms page: $nextPage" }
                val data = liveRepository.getFollowingLiveRooms(page = nextPage, pageSize = 20)
                if (requestGeneration != generation) return
                currentPage = nextPage
                totalPage = data.totalPage
                liveCount = data.liveCount
                // 只展示正在直播的房间，列表后段均为未开播的主播
                val livingRooms = data.rooms.filter { it.living }
                liveRoomList.addAllDistinctWithMainContext(livingRooms) { it.roomId }
                hasMore = currentPage < totalPage &&
                        data.rooms.isNotEmpty() &&
                        data.rooms.all { it.living }
            } else {
                logger.fInfo { "Load live area ${area.name} page: $nextPage" }
                val rooms = liveRepository.getAreaLiveRooms(
                    parentAreaId = area.id,
                    page = nextPage,
                    pageSize = 20
                )
                if (requestGeneration != generation) return
                currentPage = nextPage
                liveRoomList.addAllDistinctWithMainContext(rooms) { it.roomId }
                hasMore = rooms.isNotEmpty()
            }
            logger.fInfo { "Loaded live page=$currentPage total=${liveRoomList.size}" }
        } catch (e: CancellationException) {
            // 快速切换标签页时取消上一个请求，避免旧分区数据写入新分区列表
            throw e
        } catch (e: Exception) {
            logger.fWarn { "Load live rooms failed: ${e.stackTraceToString()}" }
            if (requestGeneration == generation) {
                withContext(Dispatchers.Main) {
                    "加载直播列表失败: ${e.localizedMessage}".toast(BVApp.context)
                }
            }
        } finally {
            // 仅当仍是当前代的请求时才复位加载状态，避免旧请求干扰新标签页的加载
            if (requestGeneration == generation) loading = false
        }
    }

    fun clearRooms() {
        generation++
        loadJob?.cancel()
        liveRoomList.clear()
        currentPage = 0
        totalPage = 1
        liveCount = 0
        loading = false
        hasMore = true
    }
}
