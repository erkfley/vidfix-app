package com.volumebooster.pro.ui.screen

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun MainScreen(
    viewModel: com.volumebooster.pro.ui.viewmodel.AudioViewModel,
    modifier: Modifier = Modifier
) {
    Text("Volume Booster Pro")
    Button(onClick = { viewModel.toggleBoost() }) {
        Text(if (viewModel.isBoosting.value) "Stop Boost" else "Start Boost")
    }
}
