package com.volumebooster.pro.ui.screen

import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun SettingsScreen(
    viewModel: com.volumebooster.pro.ui.viewmodel.SettingsViewModel,
    modifier: Modifier = Modifier
) {
    Text("Settings")
    Switch(
        checked = viewModel.autoStart.value,
        onCheckedChange = { viewModel.toggleAutoStart() }
    )
    Switch(
        checked = viewModel.headsetOnly.value,
        onCheckedChange = { viewModel.toggleHeadsetOnly() }
    )
}
