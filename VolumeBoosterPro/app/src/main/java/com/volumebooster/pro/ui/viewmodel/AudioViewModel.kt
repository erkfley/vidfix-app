package com.volumebooster.pro.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AudioViewModel : ViewModel() {
    private val _boostLevel = MutableStateFlow(0)
    val boostLevel: StateFlow<Int> = _boostLevel.asStateFlow()

    private val _isBoosting = MutableStateFlow(false)
    val isBoosting: StateFlow<Boolean> = _isBoosting.asStateFlow()

    fun toggleBoost() {
        _isBoosting.value = !_isBoosting.value
    }

    fun setBoostLevel(level: Int) {
        _boostLevel.value = level.coerceIn(0, 100)
    }
}
