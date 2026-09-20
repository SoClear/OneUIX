package io.github.soclear.oneuix

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import android.widget.Toast
import io.github.soclear.oneuix.ui.SettingScreen
import io.github.soclear.oneuix.ui.SettingViewModel
import io.github.soclear.oneuix.ui.theme.OneUIXTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setSettingScreen()
        if (!XposedServiceManager.isModuleActive) {
            Toast.makeText(this, R.string.module_disabled_tip, Toast.LENGTH_LONG).show()
        }
    }

    private fun setSettingScreen() {
        val viewModel: SettingViewModel by viewModels {
            SettingViewModelFactory(this.application)
        }

        setContent {
            OneUIXTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    SettingScreen(viewModel = viewModel, modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

private class SettingViewModelFactory(private val application: Application) :
    ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST") return SettingViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
