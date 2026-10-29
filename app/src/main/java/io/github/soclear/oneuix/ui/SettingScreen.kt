package io.github.soclear.oneuix.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.soclear.oneuix.R
import io.github.soclear.oneuix.ui.category.Category
import io.github.soclear.oneuix.ui.category.DetailPaneAndroid
import io.github.soclear.oneuix.ui.category.DetailPaneBixby
import io.github.soclear.oneuix.ui.category.DetailPaneBrowser
import io.github.soclear.oneuix.ui.category.DetailPaneCalendar
import io.github.soclear.oneuix.ui.category.DetailPaneCall
import io.github.soclear.oneuix.ui.category.DetailPaneCamera
import io.github.soclear.oneuix.ui.category.DetailPaneDualApp
import io.github.soclear.oneuix.ui.category.DetailPaneGalaxyStore
import io.github.soclear.oneuix.ui.category.DetailPaneGallery
import io.github.soclear.oneuix.ui.category.DetailPaneSamsungHealth
import io.github.soclear.oneuix.ui.category.DetailPaneHealthMonitor
import io.github.soclear.oneuix.ui.category.DetailPaneLauncher
import io.github.soclear.oneuix.ui.category.DetailPaneMessaging
import io.github.soclear.oneuix.ui.category.DetailPaneNFC
import io.github.soclear.oneuix.ui.category.ExtraPaneNetworkSpeed
import io.github.soclear.oneuix.ui.category.ExtraPaneNfcCards
import io.github.soclear.oneuix.ui.category.DetailPaneNotes
import io.github.soclear.oneuix.ui.category.DetailPaneOther
import io.github.soclear.oneuix.ui.category.DetailPanePhotoRetouching
import io.github.soclear.oneuix.ui.category.DetailPaneSPen
import io.github.soclear.oneuix.ui.category.DetailPaneSettings
import io.github.soclear.oneuix.ui.category.DetailPaneSketchBook
import io.github.soclear.oneuix.ui.category.DetailPaneSystemUI
import io.github.soclear.oneuix.ui.category.DetailPaneThemeCenter
import io.github.soclear.oneuix.ui.category.DetailPaneVideo
import io.github.soclear.oneuix.ui.category.DetailPaneWatchManager
import io.github.soclear.oneuix.ui.category.DetailPaneWeather
import io.github.soclear.oneuix.ui.category.ListPaneCategory
import io.github.soclear.oneuix.ui.category.OtherEvent
import io.github.soclear.oneuix.ui.category.SystemUIEvent
import io.github.soclear.oneuix.ui.category.onAndroidEvent
import io.github.soclear.oneuix.ui.category.onBixbyEvent
import io.github.soclear.oneuix.ui.category.onBrowserEvent
import io.github.soclear.oneuix.ui.category.onCalendarEvent
import io.github.soclear.oneuix.ui.category.onCallEvent
import io.github.soclear.oneuix.ui.category.onCameraEvent
import io.github.soclear.oneuix.ui.category.onDualAppEvent
import io.github.soclear.oneuix.ui.category.onGalaxyStoreEvent
import io.github.soclear.oneuix.ui.category.onGalleryEvent
import io.github.soclear.oneuix.ui.category.onSamsungHealthEvent
import io.github.soclear.oneuix.ui.category.onHealthMonitorEvent
import io.github.soclear.oneuix.ui.category.onLauncherEvent
import io.github.soclear.oneuix.ui.category.onMessagingEvent
import io.github.soclear.oneuix.ui.category.onNFCEvent
import io.github.soclear.oneuix.ui.category.onNotesEvent
import io.github.soclear.oneuix.ui.category.onOtherEvent
import io.github.soclear.oneuix.ui.category.onPhotoRetouchingEvent
import io.github.soclear.oneuix.ui.category.onSPenEvent
import io.github.soclear.oneuix.ui.category.onSettingsEvent
import io.github.soclear.oneuix.ui.category.onSketchBookEvent
import io.github.soclear.oneuix.ui.category.onSystemUIEvent
import io.github.soclear.oneuix.ui.category.onThemeCenterEvent
import io.github.soclear.oneuix.ui.category.onVideoEvent
import io.github.soclear.oneuix.ui.category.onWatchManagerEvent
import io.github.soclear.oneuix.ui.category.onWeatherEvent
import io.github.soclear.oneuix.ui.component.AppPicker
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun SettingScreen(viewModel: SettingViewModel, modifier: Modifier = Modifier) {
    val scaffoldNavigator = rememberListDetailPaneScaffoldNavigator<SettingDestination>()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val categoryAppInfoList by viewModel.categoryAppInfoList.collectAsStateWithLifecycle()
    val preference by viewModel.preference.collectAsStateWithLifecycle()
    var selectedCategory by rememberSaveable { mutableStateOf<Category?>(null) }

    fun navigateToExtra(destination: SettingDestination.Extra) {
        scope.launch {
            scaffoldNavigator.navigateTo(ListDetailPaneScaffoldRole.Extra, destination)
        }
    }

    val onBack: () -> Unit = { scope.launch { scaffoldNavigator.navigateBack() } }

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
                    onItemClick = { category ->
                        selectedCategory = category
                        scope.launch {
                            scaffoldNavigator.navigateTo(
                                ListDetailPaneScaffoldRole.Detail,
                                SettingDestination.Detail(category)
                            )
                        }
                    },
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
                selectedCategory?.let {
                    when (it) {
                        Category.Android -> DetailPaneAndroid(
                            uiState = preference.android,
                            onEvent = viewModel::onAndroidEvent
                        )

                        Category.SystemUI -> DetailPaneSystemUI(
                            uiState = preference.systemUI,
                            onEvent = viewModel::onSystemUIEvent,
                            onNavigateToAppPicker = {
                                navigateToExtra(SettingDestination.Extra.HideOngoingActivityMediaApps)
                            },
                            onNavigateToNetworkSpeed = {
                                navigateToExtra(SettingDestination.Extra.NetworkSpeed)
                            }
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

                        Category.Bixby -> DetailPaneBixby(
                            uiState = preference.other,
                            onEvent = viewModel::onBixbyEvent,
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

                        Category.Health -> DetailPaneSamsungHealth(
                            uiState = preference.other,
                            onEvent = viewModel::onSamsungHealthEvent
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

                        Category.NFC -> DetailPaneNFC(
                            onNavigateToNfcCards = {
                                navigateToExtra(SettingDestination.Extra.NfcCards)
                            }
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

                        Category.Other -> DetailPaneOther(
                            uiState = preference.other,
                            onEvent = viewModel::onOtherEvent,
                            onNavigateToWritingToolkitApps = {
                                navigateToExtra(SettingDestination.Extra.DisableWritingToolkitApps)
                            },
                            onNavigateToScreenshotDetectionApps = {
                                navigateToExtra(SettingDestination.Extra.HideScreenshotDetectionApps)
                            }
                        )
                    }
                }
            }
        },
        extraPane = {
            AnimatedPane {
                when (scaffoldNavigator.currentDestination?.contentKey) {
                    SettingDestination.Extra.NfcCards -> ExtraPaneNfcCards(
                        uiState = preference.other,
                        onEvent = viewModel::onNFCEvent,
                        onBack = onBack
                    )

                    SettingDestination.Extra.NetworkSpeed -> ExtraPaneNetworkSpeed(
                        uiState = preference.systemUI,
                        onEvent = viewModel::onSystemUIEvent,
                        onBack = onBack
                    )

                    SettingDestination.Extra.HideOngoingActivityMediaApps -> AppPicker(
                        selectedPackages = preference.systemUI.notification.hideOngoingActivityMediaPackages
                            .split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet(),
                        onSelectionChange = {
                            viewModel.onSystemUIEvent(
                                SystemUIEvent.Notification.HideOngoingActivityMediaPackages(it.sorted().joinToString(","))
                            )
                        },
                        onBack = onBack
                    )

                    SettingDestination.Extra.DisableWritingToolkitApps -> AppPicker(
                        selectedPackages = preference.other.disableWritingToolkitPackages,
                        onSelectionChange = { viewModel.onOtherEvent(OtherEvent.DisableWritingToolkitPackages(it)) },
                        onBack = onBack
                    )

                    SettingDestination.Extra.HideScreenshotDetectionApps -> AppPicker(
                        selectedPackages = preference.other.hideScreenshotDetectionPackages,
                        onSelectionChange = { viewModel.onOtherEvent(OtherEvent.HideScreenshotDetectionPackages(it)) },
                        onBack = onBack
                    )

                    else -> Unit
                }
            }
        },
        modifier = modifier.fillMaxSize(),
    )
}
