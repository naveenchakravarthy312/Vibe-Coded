package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.WrcApp
import com.example.ui.theme.VibeTheme
import com.example.ui.viewmodel.WrcViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: WrcViewModel = viewModel()
            val themeConfig by viewModel.themeConfig.collectAsStateWithLifecycle()

            VibeTheme(
                themeMode = themeConfig.themeMode,
                dynamicColor = themeConfig.dynamicColor
            ) {
                WrcApp(viewModel = viewModel)
            }
        }
    }
}
