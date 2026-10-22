package com.volumebooster.pro.model

data class AudioSettings(
    val boostLevel: Int = 50,
    val isBoostEnabled: Boolean = false,
    val isHeadsetOnly: Boolean = false
)
