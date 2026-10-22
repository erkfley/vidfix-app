package com.volumebooster.pro.repository

import android.content.Context
import android.media.AudioManager

class AudioRepository(private val context: Context) {
    private val audioManager: AudioManager by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    fun getCurrentVolume(): Int = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
    fun setVolume(level: Int) = audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, level, 0)
    fun getMaxVolume(): Int = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
}
