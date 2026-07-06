package com.example.life_counter

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The phone lies on the table during a game — never let the screen sleep.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            LifeCounterTheme {
                LifeCounterApp()
            }
        }
    }
}

@Composable
private fun LifeCounterApp(viewModel: GameViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    LifeCounterScreen(
        state = state,
        onLifeChange = viewModel::adjustLife,
        onReset = viewModel::resetGame,
    )
}
