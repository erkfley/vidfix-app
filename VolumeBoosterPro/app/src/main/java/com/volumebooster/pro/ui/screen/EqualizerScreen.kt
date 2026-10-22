package com.volumebooster.pro.ui.screen

import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun EqualizerScreen(
    viewModel: com.volumebooster.pro.ui.viewmodel.EqualizerViewModel,
    modifier: Modifier = Modifier
) {
    Text("Equalizer")
    Slider(
        value = viewModel.bassBoost.value.toFloat(),
        onValueChange = { viewModel.setBassBoost(it.toInt()) },
        valueRange = 0f..100f
    )
    Slider(
        value = viewModel.trebleBoost.value.toFloat(),
        onValueChange = { viewModel.setTrebleBoost(it.toInt()) },
        valueRange = 0f..100f
    )
}
