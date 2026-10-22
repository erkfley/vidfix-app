package com.volumebooster.pro.di

import android.content.Context
import com.volumebooster.pro.repository.AudioRepository
import com.volumebooster.pro.ui.viewmodel.AudioViewModel
import com.volumebooster.pro.ui.viewmodel.EqualizerViewModel
import com.volumebooster.pro.ui.viewmodel.SettingsViewModel

class AppContainer(private val context: Context) {
    val audioRepository: AudioRepository by lazy { AudioRepository(context) }
    val audioViewModel: AudioViewModel by lazy { AudioViewModel() }
    val equalizerViewModel: EqualizerViewModel by lazy { EqualizerViewModel() }
    val settingsViewModel: SettingsViewModel by lazy { SettingsViewModel() }
}
