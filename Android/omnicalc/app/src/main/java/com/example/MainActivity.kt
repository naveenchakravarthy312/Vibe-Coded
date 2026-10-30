package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.*
import com.example.ui.theme.OmniCalcTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val viewModel: CalculatorViewModel = viewModel()
            val themeConfig by viewModel.themeConfig.collectAsState()
            val activeTab by viewModel.activeTab.collectAsState()

            var showInfoDialog by remember { mutableStateOf(false) }

            // Handle back gesture when on secondary tabs
            BackHandler(enabled = activeTab != ActiveTab.CALCULATOR) {
                viewModel.setActiveTab(ActiveTab.CALCULATOR)
            }

            OmniCalcTheme(
                themeMode = themeConfig.themeMode,
                dynamicColor = themeConfig.dynamicColor
            ) {
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .imePadding()
                        .testTag("omnicalc_scaffold"),
                    topBar = {
                        OmniCalcTopBar(
                            activeTab = activeTab,
                            onTabSelected = { viewModel.setActiveTab(it) },
                            themeMode = themeConfig.themeMode,
                            onThemeModeSelected = { viewModel.setThemeMode(it) },
                            dynamicColor = themeConfig.dynamicColor,
                            onDynamicColorChanged = { viewModel.setDynamicColor(it) },
                            onOpenInfoDialog = { showInfoDialog = true }
                        )
                    },
                    contentWindowInsets = WindowInsets(0, 0, 0, 0)
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        AnimatedContent(
                            targetState = activeTab,
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                            label = "TabContentTransition"
                        ) { targetTab ->
                            when (targetTab) {
                                ActiveTab.CALCULATOR -> CalculatorScreen(viewModel = viewModel)
                                ActiveTab.CONVERTER -> ConverterScreen(viewModel = viewModel)
                                ActiveTab.HISTORY -> HistoryScreen(viewModel = viewModel)
                            }
                        }
                    }

                    if (showInfoDialog) {
                        AppInfoDialog(onDismiss = { showInfoDialog = false })
                    }
                }
            }
        }
    }
}

@Composable
fun AppInfoDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Calculate,
                contentDescription = "OmniCalc",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
        },
        title = {
            Text(
                text = "OmniCalc",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "A versatile calculator engineered for speed and precision.",
                    style = MaterialTheme.typography.bodyMedium
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                InfoFeatureRow("Private & Secure", "All calculations and unit conversions stay locally on your device.")
                InfoFeatureRow("Measurement Translators", "11 comprehensive categories with live multi-unit comparison.")
                InfoFeatureRow("Persistent History", "Local SQLite database with tagging, notes, and search.")
                InfoFeatureRow("Scientific Engine", "Trigonometry (DEG/RAD), powers, roots, log, ln, factorials.")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Got it")
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp)
    )
}

@Composable
private fun InfoFeatureRow(title: String, description: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp).padding(top = 2.dp)
        )
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
