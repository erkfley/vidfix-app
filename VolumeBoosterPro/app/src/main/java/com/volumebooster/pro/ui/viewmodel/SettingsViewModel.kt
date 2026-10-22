package com.volumebooster.pro.ui.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsViewModel : ViewModel() {
    private val _autoStart = MutableStateFlow(false)
    val autoStart: StateFlow<Boolean> = _autoStart.asStateFlow()

    private val _headsetOnly = MutableStateFlow(false)
    val headsetOnly: StateFlow<Boolean> = _headsetOnly.asStateFlow()

    fun toggleAutoStart() {
        _autoStart.value = !_autoStart.value
    }

    fun toggleHeadsetOnly() {
        _headsetOnly.value = !_headsetOnly.value
    }
}
