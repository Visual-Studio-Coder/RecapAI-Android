package com.click.recapai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.click.recapai.ui.theme.RecapAITheme

class MainActivity : ComponentActivity() {
    private val viewModel: GeminiAPIViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.loadSettings() // Load settings when the app starts

        setContent {
            RecapAITheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    RecapScreen(viewModel) // Pass the viewModel to the RecapScreen
                }
            }
        }
    }
}