package com.volumebooster.pro.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.volumebooster.pro.ui.theme.VolumeBoosterTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VolumeBoosterTheme {
                Surface(modifier = Modifier) {
                    // TODO: Add your screen here
                }
            }
        }
    }
}
