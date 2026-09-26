package io.github.soclear.oneuix.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import io.github.soclear.oneuix.ui.category.DetailPaneMessaging
import io.github.soclear.oneuix.ui.category.DetailPaneNotes
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun SettingScreen(viewModel: SettingViewModel, modifier: Modifier = Modifier) {
    val scaffoldNavigator = rememberListDetailPaneScaffoldNavigator<Category>()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val categoryAppInfoList by viewModel.categoryAppInfoList.collectAsStateWithLifecycle()
    val preferenceState by viewModel.preferenceState.collectAsStateWithLifecycle()
    val preference = preferenceState.preference

    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            scope.launch {
                try {
                    val saved = context.contentResolver.openOutputStream(it)?.use { stream ->
                        viewModel.backupTo(stream)
                    } ?: false
                    if (!saved) Toast.makeText(context, R.string.backup_failed, Toast.LENGTH_SHORT).show()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    Toast.makeText(context, R.string.backup_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            scope.launch {
                try {
                    val restored = context.contentResolver.openInputStream(it)?.use { stream ->
                        viewModel.restoreFrom(stream)
                    } ?: false
                    if (!restored) Toast.makeText(context, R.string.restore_failed, Toast.LENGTH_SHORT).show()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    Toast.makeText(context, R.string.restore_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    if (preferenceState.status != PreferenceStatus.Ready) {
        PreferenceConnectionStatus(
            status = preferenceState.status,
            onRetry = viewModel::retryConnection,
            onRestore = { restoreLauncher.launch(arrayOf("application/json")) },
            modifier = modifier,
        )
        return
    }

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
private fun PreferenceConnectionStatus(
    status: PreferenceStatus,
    onRetry: () -> Unit,
    onRestore: () -> Unit,
    modifier: Modifier,
) {
    val message = when (status) {
        PreferenceStatus.Connecting -> R.string.framework_connecting
        PreferenceStatus.Unavailable -> R.string.framework_unavailable
        PreferenceStatus.Loading -> R.string.preferences_loading
        PreferenceStatus.Error -> R.string.preferences_failed
        PreferenceStatus.Ready -> return
    }
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(message), textAlign = TextAlign.Center)
        if (status == PreferenceStatus.Connecting || status == PreferenceStatus.Loading) {
            CircularProgressIndicator()
        } else {
            Button(onClick = onRetry) { Text(stringResource(R.string.connection_retry)) }
        }
        if (status == PreferenceStatus.Error) {
            Button(onClick = onRestore) { Text(stringResource(R.string.restore_config)) }
        }
    }
}
