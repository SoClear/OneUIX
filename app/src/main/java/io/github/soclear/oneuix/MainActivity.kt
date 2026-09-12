package io.github.soclear.oneuix

import android.annotation.SuppressLint
import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.ui.Modifier
import androidx.datastore.dataStoreFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.github.soclear.oneuix.common.Preference
import io.github.soclear.oneuix.ui.ModuleDisabledScreen
import io.github.soclear.oneuix.ui.SettingScreen
import io.github.soclear.oneuix.ui.SettingViewModel
import io.github.soclear.oneuix.ui.theme.OneUIXTheme
import kotlin.system.exitProcess


class MainActivity : ComponentActivity() {
    val preferenceFile by lazy { dataStoreFile("whatever") }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setScreen()
    }

    @SuppressLint("SetWorldReadable")
    override fun onPause() {
        super.onPause()
        setWorldReadable()
    }

    @OptIn(ExperimentalMaterial3AdaptiveApi::class)
    private fun setScreen() {
        if (preferenceFile.name == Preference.FILE_NAME) {
            setSettingScreen()
        } else {
            setModuleDisabledScreen()
        }
    }

    @SuppressLint("SetWorldReadable")
    private fun setWorldReadable(): Boolean = preferenceFile.setReadable(true, false)

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

    private fun setModuleDisabledScreen() {
        setContent {
            OneUIXTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ModuleDisabledScreen(
                        onClickClose = { exitProcess(0) },
                        modifier = Modifier.padding(innerPadding)
                    )
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
