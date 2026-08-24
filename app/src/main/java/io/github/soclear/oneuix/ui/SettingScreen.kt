package io.github.soclear.oneuix.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import io.github.soclear.oneuix.R
import io.github.soclear.oneuix.ui.category.Category
import io.github.soclear.oneuix.ui.category.DetailPaneAndroid
import io.github.soclear.oneuix.ui.category.DetailPaneBrowser
import io.github.soclear.oneuix.ui.category.DetailPaneCalendar
import io.github.soclear.oneuix.ui.category.DetailPaneCall
import io.github.soclear.oneuix.ui.category.DetailPaneCamera
import io.github.soclear.oneuix.ui.category.DetailPaneDualApp
import io.github.soclear.oneuix.ui.category.DetailPaneGalaxyStore
import io.github.soclear.oneuix.ui.category.DetailPaneGallery
import io.github.soclear.oneuix.ui.category.DetailPaneHealthMonitor
import io.github.soclear.oneuix.ui.category.DetailPaneLauncher
import io.github.soclear.oneuix.ui.category.DetailPaneInteraction
import io.github.soclear.oneuix.ui.category.DetailPaneMessaging
import io.github.soclear.oneuix.ui.category.DetailPaneNotes
import io.github.soclear.oneuix.ui.category.DetailPanePhotoRetouching
import io.github.soclear.oneuix.ui.category.DetailPaneSPen
import io.github.soclear.oneuix.ui.category.DetailPaneSketchBook
import io.github.soclear.oneuix.ui.category.DetailPaneSettings
import io.github.soclear.oneuix.ui.category.DetailPaneSystemUI
import io.github.soclear.oneuix.ui.category.DetailPaneThemeCenter
import io.github.soclear.oneuix.ui.category.DetailPaneVideo
import io.github.soclear.oneuix.ui.category.DetailPaneWatchManager
import io.github.soclear.oneuix.ui.category.DetailPaneWeather
import io.github.soclear.oneuix.ui.category.ListPaneCategory
import io.github.soclear.oneuix.ui.category.onAndroidEvent
import io.github.soclear.oneuix.ui.category.onBrowserEvent
import io.github.soclear.oneuix.ui.category.onCalendarEvent
import io.github.soclear.oneuix.ui.category.onCallEvent
import io.github.soclear.oneuix.ui.category.onCameraEvent
import io.github.soclear.oneuix.ui.category.onDualAppEvent
import io.github.soclear.oneuix.ui.category.onGalaxyStoreEvent
import io.github.soclear.oneuix.ui.category.onGalleryEvent
import io.github.soclear.oneuix.ui.category.onHealthMonitorEvent
import io.github.soclear.oneuix.ui.category.onLauncherEvent
import io.github.soclear.oneuix.ui.category.onInteractionEvent
import io.github.soclear.oneuix.ui.category.onMessagingEvent
import io.github.soclear.oneuix.ui.category.onNotesEvent
import io.github.soclear.oneuix.ui.category.onPhotoRetouchingEvent
import io.github.soclear.oneuix.ui.category.onSPenEvent
import io.github.soclear.oneuix.ui.category.onSettingsEvent
import io.github.soclear.oneuix.ui.category.onSketchBookEvent
import io.github.soclear.oneuix.ui.category.onSystemUIEvent
import io.github.soclear.oneuix.ui.category.onThemeCenterEvent
import io.github.soclear.oneuix.ui.category.onVideoEvent
import io.github.soclear.oneuix.ui.category.onWatchManagerEvent
import io.github.soclear.oneuix.ui.category.onWeatherEvent
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun SettingScreen(viewModel: SettingViewModel, modifier: Modifier = Modifier) {
    val scaffoldNavigator = rememberListDetailPaneScaffoldNavigator<Category>()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val categoryAppInfoList by viewModel.categoryAppInfoList.collectAsStateWithLifecycle()
    val userLaunchableApps by viewModel.userLaunchableApps.collectAsStateWithLifecycle()
    val preference by viewModel.preference.collectAsStateWithLifecycle()

    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            scope.launch {
                context.contentResolver.openOutputStream(it)?.use { stream ->
                    viewModel.backupTo(stream)
                }
            }
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            scope.launch {
                context.contentResolver.openInputStream(it)?.use { stream ->
                    try {
                        viewModel.restoreFrom(stream)
                    } catch (_: Throwable) {
                        Toast.makeText(context, R.string.restore_failed, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    NavigableListDetailPaneScaffold(
        navigator = scaffoldNavigator,
        listPane = {
            AnimatedPane {
                ListPaneCategory(
                    categoryAppInfoList = categoryAppInfoList,
                    hiddenCategoryNames = preference.hiddenMainMenuCategories,
                    onItemClick = { category ->
                        scope.launch {
                            scaffoldNavigator.navigateTo(
                                ListDetailPaneScaffoldRole.Detail,
                                category
                            )
                        }
                    },
                    onCategoryVisibilityChange = viewModel::setCategoryVisible,
                    onShowAllCategories = viewModel::showAllCategories,
                    onBackup = {
                        val name = "OneUIX_backup_${
                            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
                        }.json"
                        backupLauncher.launch(name)
                    },
                    onRestore = { restoreLauncher.launch(arrayOf("application/json")) }
                )
            }
        },
        detailPane = {
            AnimatedPane {
                scaffoldNavigator.currentDestination?.contentKey?.let {
                    when (it) {
                        Category.Interaction -> DetailPaneInteraction(
                            uiState = preference.interaction,
                            userLaunchableApps = userLaunchableApps,
                            onEvent = viewModel::onInteractionEvent
                        )

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

                        Category.Browser -> DetailPaneBrowser(
                            uiState = preference.other,
                            onEvent = viewModel::onBrowserEvent
                        )

                        Category.Calendar -> DetailPaneCalendar(
                            uiState = preference.other,
                            onEvent = viewModel::onCalendarEvent
                        )

                        Category.DualApp -> DetailPaneDualApp(
                            uiState = preference.other,
                            onEvent = viewModel::onDualAppEvent
                        )

                        Category.Gallery -> DetailPaneGallery(
                            uiState = preference.other,
                            onEvent = viewModel::onGalleryEvent
                        )

                        Category.GalaxyStore -> DetailPaneGalaxyStore(
                            uiState = preference.other,
                            onEvent = viewModel::onGalaxyStoreEvent
                        )

                        Category.HealthMonitor -> DetailPaneHealthMonitor(
                            uiState = preference.other,
                            onEvent = viewModel::onHealthMonitorEvent
                        )

                        Category.Launcher -> DetailPaneLauncher(
                            uiState = preference.other,
                            onEvent = viewModel::onLauncherEvent
                        )

                        Category.Messaging -> DetailPaneMessaging(
                            uiState = preference.other,
                            onEvent = viewModel::onMessagingEvent
                        )

                        Category.Notes -> DetailPaneNotes(
                            uiState = preference.other,
                            onEvent = viewModel::onNotesEvent
                        )

                        Category.PhotoRetouching -> DetailPanePhotoRetouching(
                            uiState = preference.other,
                            onEvent = viewModel::onPhotoRetouchingEvent
                        )

                        Category.SketchBook -> DetailPaneSketchBook(
                            uiState = preference.other,
                            onEvent = viewModel::onSketchBookEvent
                        )

                        Category.SPen -> DetailPaneSPen(
                            uiState = preference.other,
                            onEvent = viewModel::onSPenEvent
                        )

                        Category.ThemeCenter -> DetailPaneThemeCenter(
                            uiState = preference.other,
                            onEvent = viewModel::onThemeCenterEvent
                        )

                        Category.Video -> DetailPaneVideo(
                            uiState = preference.other,
                            onEvent = viewModel::onVideoEvent
                        )

                        Category.WatchManager -> DetailPaneWatchManager(
                            uiState = preference.other,
                            onEvent = viewModel::onWatchManagerEvent
                        )

                        Category.Weather -> DetailPaneWeather(
                            uiState = preference.other,
                            onEvent = viewModel::onWeatherEvent
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
