package dev.aaa1115910.bv.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 全局焦点色，由设置里的"焦点颜色"决定。
 *
 * 用 CompositionLocal 而不是直接读 MaterialTheme.colorScheme：项目里
 * tv 和普通两套 MaterialTheme 是嵌套的，只有 tv 那套写入了焦点色，
 * 取错一套会静默拿到默认配色。
 */
val LocalFocusColor = staticCompositionLocalOf { Color(0xFFFF69B4) }
