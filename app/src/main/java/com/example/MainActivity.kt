package com.example

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.AuraDatabase
import com.example.data.AuraRepository
import com.example.ui.AuraAppScreen
import com.example.ui.AuraViewModel
import com.example.ui.AuraViewModelFactory
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        val context = LocalContext.current
        val database = remember { AuraDatabase.getDatabase(context) }
        val repository = remember { AuraRepository(database.auraDao()) }
        val application = context.applicationContext as Application
        
        val viewModel: AuraViewModel = viewModel(
          factory = AuraViewModelFactory(application, repository)
        )

        AuraAppScreen(viewModel = viewModel)
      }
    }
  }
}
