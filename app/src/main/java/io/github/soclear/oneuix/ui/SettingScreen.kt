package io.github.soclear.oneuix.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import io.github.soclear.oneuix.R
import io.github.soclear.oneuix.ui.category.Category
import io.github.soclear.oneuix.ui.category.DetailPaneAndroid
import io.github.soclear.oneuix.ui.category.DetailPaneBixby
import io.github.soclear.oneuix.ui.category.DetailPaneCall
import io.github.soclear.oneuix.ui.category.DetailPaneCamera
import io.github.soclear.oneuix.ui.category.DetailPaneOther
import io.github.soclear.oneuix.ui.category.DetailPaneSettings
import io.github.soclear.oneuix.ui.category.DetailPaneSystemUI
import io.github.soclear.oneuix.ui.category.ListPaneCategory
import io.github.soclear.oneuix.ui.category.onAndroidEvent
import io.github.soclear.oneuix.ui.category.onBixbyEvent
import io.github.soclear.oneuix.ui.category.onCallEvent
import io.github.soclear.oneuix.ui.category.onCameraEvent
import io.github.soclear.oneuix.ui.category.onOtherEvent
import io.github.soclear.oneuix.ui.category.onSettingsEvent
import io.github.soclear.oneuix.ui.category.onSystemUIEvent

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun SettingScreen(viewModel: SettingViewModel, modifier: Modifier = Modifier) {
    val scaffoldNavigator = rememberListDetailPaneScaffoldNavigator<Category>()
    val scope = rememberCoroutineScope()
    val categoryAppInfoList by viewModel.categoryAppInfoList.collectAsStateWithLifecycle()
    val preference by viewModel.preference.collectAsStateWithLifecycle()
    NavigableListDetailPaneScaffold(
        navigator = scaffoldNavigator,
        listPane = {
            AnimatedPane {
                ListPaneCategory(
                    categoryAppInfoList = categoryAppInfoList,
                    onItemClick = { category ->
                        scope.launch {
                            scaffoldNavigator.navigateTo(
                                ListDetailPaneScaffoldRole.Detail,
                                category
                            )
                        }
                    }
                )
            }
        },
        detailPane = {
            AnimatedPane {
                scaffoldNavigator.currentDestination?.contentKey?.let {
                    when (it) {
                        Category.Android -> DetailPaneAndroid(
                            uiState = preference.android,
                            onEvent = viewModel::onAndroidEvent
                        )

                        Category.SystemUI -> DetailPaneSystemUI(
                            uiState = preference.systemUI,
                            onEvent = viewModel::onSystemUIEvent
                        )

                        Category.Settings -> DetailPaneSettings(
                            uiState = preference.settings,
                            onEvent = viewModel::onSettingsEvent
                        )

                        Category.Call -> DetailPaneCall(
                            uiState = preference.call,
                            onEvent = viewModel::onCallEvent
                        )

                        Category.Camera -> DetailPaneCamera(
                            uiState = preference.camera,
                            onEvent = viewModel::onCameraEvent
                        )

                        Category.Bixby -> DetailPaneBixby(
                            uiState = preference.bixby,
                            onEvent = viewModel::onBixbyEvent
                        )

                        Category.Other -> DetailPaneOther(
                            uiState = preference.other,
                            onEvent = viewModel::onOtherEvent
                        )
                    }
                }
            }
        },
        modifier = modifier.fillMaxSize(),
    )
}

@Composable
fun ModuleDisabledScreen(
    onClickClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = stringResource(R.string.module_disabled_tip))
        Button(
            onClick = onClickClose,
            modifier = Modifier.padding(top = 10.dp)
        ) {
            Text(text = stringResource(R.string.close))
        }
    }
}
