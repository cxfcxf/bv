package dev.aaa1115910.bv.player

/**
 * 一条 DASH representation。
 *
 * B 站的 m4s 本身就是 DASH 分段文件，带 sidx 索引。用 [initializationRange] 和
 * [indexRange] 构建 manifest 后，播放器会按 byte range 分段请求，而不是开一条
 * 长连接把整个文件拉下来 —— CDN 对后者会限速。
 *
 * @param urls 主线流 + 备用流，内容相同。会全部作为 BaseURL 写入 manifest，
 *             播放器可在其间自行故障转移
 * @param initializationRange 初始化段范围，形如 "0-1234"
 * @param indexRange sidx 索引范围，形如 "1235-5678"
 */
data class DashTrack(
    val urls: List<String>,
    val codecs: String?,
    val bandwidth: Int,
    val initializationRange: String,
    val indexRange: String,
    val width: Int = 0,
    val height: Int = 0,
    val frameRate: Float = 0f
)
