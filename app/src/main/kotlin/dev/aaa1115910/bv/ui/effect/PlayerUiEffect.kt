package dev.aaa1115910.bv.ui.effect

sealed class PlayerUiEffect {
    data class SwitchingCdn(val isVideo: Boolean, val host: String) : PlayerUiEffect()
    data object PlayEnded : PlayerUiEffect()
    data object FinishActivity: PlayerUiEffect()
}