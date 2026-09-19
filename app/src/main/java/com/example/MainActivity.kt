package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.example.trawa.core.di.TrawaContainer
import com.example.trawa.ui.navigation.TrawaNavHost
import com.example.ui.theme.TrawaTheme

class MainActivity : ComponentActivity() {

  private lateinit var container: TrawaContainer

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    container = TrawaContainer(this)

    setContent {
      val isDarkTheme by container.profileRepository.isDarkTheme.collectAsState()

      TrawaTheme(darkTheme = isDarkTheme) {
        TrawaNavHost(
          container = container,
          modifier = Modifier.fillMaxSize()
        )
      }
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    if (::container.isInitialized) {
      container.voiceService.destroy()
    }
  }
}
