package dev.aaa1115910.bv.entity

import android.content.Context
import androidx.compose.ui.graphics.Color
import dev.aaa1115910.bv.R

/**
 * 全局焦点色。会同时作为 border 和 inverseSurface 写入配色，
 * 因此每个选项都必须配一个自己的前景色 —— 焦点态的图标和文字用它。
 * 候选色都取亮色，保证配黑色前景时对比足够。
 */
enum class FocusColor(
    val code: Int,
    val color: Color,
    val onColor: Color,
    private val strRes: Int
) {
    Pink(0, Color(0xFFFF69B4), Color.Black, R.string.focus_color_pink),
    White(1, Color(0xFFFFFFFF), Color.Black, R.string.focus_color_white),
    Amber(2, Color(0xFFFFC107), Color.Black, R.string.focus_color_amber),
    Cyan(3, Color(0xFF4DD0E1), Color.Black, R.string.focus_color_cyan);

    fun getDisplayName(context: Context): String = context.getString(strRes)

    companion object {
        fun fromCode(code: Int): FocusColor = entries.find { it.code == code } ?: Pink
    }
}
