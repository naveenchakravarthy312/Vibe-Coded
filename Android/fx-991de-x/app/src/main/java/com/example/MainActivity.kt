package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.display.CalculatorDisplay
import com.example.ui.keypad.ClassWizKeypad
import com.example.ui.theme.Fx991DeXTheme
import com.example.ui.theme.LocalCalculatorPalette
import com.example.viewmodel.ActiveOverlay
import com.example.viewmodel.CalcMode
import com.example.viewmodel.CalculatorViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent {
            Fx991DeXApp()
        }
    }
}

@Composable
fun Fx991DeXApp(vm: CalculatorViewModel = viewModel()) {
    val state by vm.uiState.collectAsStateWithLifecycle()

    // Back navigation closes overlays first, or returns to Mode 1 (Berechnungen)
    BackHandler(enabled = state.overlay !is ActiveOverlay.None || state.mode != CalcMode.CALCULATE) {
        if (state.overlay !is ActiveOverlay.None) {
            vm.dismissOverlay()
        } else {
            vm.selectMode(CalcMode.CALCULATE)
        }
    }

    Fx991DeXTheme(themeMode = state.themeMode) {
        val palette = LocalCalculatorPalette.current

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(palette.canvasBackground)
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentAlignment = Alignment.Center
        ) {
            // Adaptive container: full width on handhelds, clean ergonomic max-width on tablets/foldables
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
                    .padding(bottom = 4.dp)
            ) {
                // Top ~36%: Natural Textbook Display (Natürliche Darstellung)
                CalculatorDisplay(
                    state = state,
                    vm = vm,
                    modifier = Modifier.weight(0.36f)
                )

                // Bottom ~64%: Exact 50-key German fx-991DE X ClassWiz Keypad Grid
                ClassWizKeypad(
                    shiftActive = state.shiftActive,
                    alphaActive = state.alphaActive,
                    onKeyPress = vm::onKeyPress,
                    modifier = Modifier.weight(0.64f)
                )
            }
        }
    }
}
