package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.example.ui.ScratchpadScreen
import com.example.ui.ScratchpadViewModel
import com.example.ui.theme.ScratchpadTheme

class MainActivity : ComponentActivity() {

    private val viewModel: ScratchpadViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Handle incoming shared text from other apps (ACTION_SEND)
        handleIncomingIntent(intent)

        setContent {
            ScratchpadTheme(darkTheme = true) {
                ScratchpadScreen(
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            val sharedSubject = intent.getStringExtra(Intent.EXTRA_SUBJECT)
            if (!sharedText.isNullOrBlank()) {
                val title = sharedSubject?.takeIf { it.isNotBlank() } ?: "Shared Note"
                viewModel.loadExternalFile(title, sharedText)
            }
        }
    }
}
