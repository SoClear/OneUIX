package io.github.soclear.oneuix.ui.category

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.soclear.oneuix.R
import io.github.soclear.oneuix.data.ONE_UI_VERSION
import io.github.soclear.oneuix.data.Preference
import io.github.soclear.oneuix.service.InteractionDiagnostics
import io.github.soclear.oneuix.service.NotificationWakeService
import io.github.soclear.oneuix.service.SoftRestartController
import io.github.soclear.oneuix.ui.SettingViewModel
import io.github.soclear.oneuix.ui.component.SwitchItem
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun DetailPaneInteraction(
    uiState: Preference.Interaction,
    userLaunchableApps: List<UserLaunchableApp>,
    onEvent: (InteractionEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showSoftRestartConfirmation by remember { mutableStateOf(false) }
    var showAutoStartAppPicker by remember { mutableStateOf(false) }
    var notificationAccessGranted by remember {
        mutableStateOf(isNotificationAccessGranted(context))
    }
    val notificationSettings = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        notificationAccessGranted = isNotificationAccessGranted(context)
        InteractionDiagnostics.recordPermissionStatus(context, notificationAccessGranted)
    }

    PackagePane(modifier) {
        SectionTitle(stringResource(R.string.interaction_apply_section))
        ListItem(
            headlineContent = { Text(stringResource(R.string.soft_restart_title)) },
            supportingContent = { Text(stringResource(R.string.soft_restart_summary)) },
            trailingContent = {
                Button(onClick = { showSoftRestartConfirmation = true }) {
                    Text(stringResource(R.string.soft_restart_button))
                }
            },
        )

        SectionTitle(stringResource(R.string.interaction_auto_start_section))
        SwitchItem(
            title = stringResource(R.string.auto_start_enabled_title),
            summary = stringResource(R.string.auto_start_enabled_summary),
            checked = uiState.autoStartEnabled,
            onCheckedChange = { onEvent(InteractionEvent.AutoStartEnabled(it)) },
        )
        val availablePackageNames = remember(userLaunchableApps) {
            userLaunchableApps.mapTo(mutableSetOf()) { it.packageName }
        }
        val selectedInstalledCount = uiState.autoStartPackages.count {
            it in availablePackageNames
        }
        ListItem(
            headlineContent = { Text(stringResource(R.string.auto_start_select_apps_title)) },
            supportingContent = {
                Text(
                    stringResource(
                        R.string.auto_start_select_apps_summary,
                        selectedInstalledCount,
                    )
                )
            },
            trailingContent = {
                Button(onClick = { showAutoStartAppPicker = true }) {
                    Text(stringResource(R.string.auto_start_select_apps_button))
                }
            },
        )

        SectionTitle(stringResource(R.string.interaction_notification_section))
        ListItem(
            headlineContent = { Text(stringResource(R.string.notification_access_title)) },
            supportingContent = {
                val permissionStatus = stringResource(
                        if (notificationAccessGranted) R.string.permission_granted
                        else R.string.permission_not_granted
                    )
                Text(
                    stringResource(
                        R.string.notification_access_summary,
                        permissionStatus,
                    )
                )
            },
            trailingContent = {
                Button(onClick = {
                    notificationSettings.launch(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }) {
                    Text(stringResource(R.string.open_notification_access))
                }
            }
        )
        SwitchItem(
            title = stringResource(R.string.wake_on_notification_title),
            summary = stringResource(R.string.wake_on_notification_summary),
            checked = uiState.wakeOnNotification,
            onCheckedChange = { onEvent(InteractionEvent.WakeOnNotification(it)) }
        )
        SliderItem(
            title = stringResource(R.string.wake_duration_title),
            summary = stringResource(R.string.wake_duration_summary),
            valueLabelResource = R.string.seconds_value,
            valueToLabelNumber = { it.roundToInt().coerceIn(1, 10) },
            value = uiState.wakeDurationSeconds.coerceIn(1, 10).toFloat(),
            valueRange = 1f..10f,
            steps = 8,
            onValueChangeFinished = {
                onEvent(InteractionEvent.WakeDuration(it.roundToInt().coerceIn(1, 10)))
            }
        )
        SwitchItem(
            title = stringResource(R.string.wake_only_screen_off_title),
            summary = stringResource(R.string.wake_only_screen_off_summary),
            checked = uiState.wakeOnlyWhenScreenOff,
            onCheckedChange = { onEvent(InteractionEvent.WakeOnlyWhenScreenOff(it)) }
        )
        SwitchItem(
            title = stringResource(R.string.notification_direct_open_title),
            summary = stringResource(R.string.notification_direct_open_summary),
            checked = uiState.notificationDirectOpenAfterAuth,
            onCheckedChange = { onEvent(InteractionEvent.NotificationDirectOpen(it)) }
        )

        SectionTitle(stringResource(R.string.interaction_home_section))
        SwitchItem(
            title = stringResource(R.string.home_swipe_down_search_title),
            summary = stringResource(R.string.home_swipe_down_search_summary),
            checked = uiState.homeSwipeDownSearch,
            onCheckedChange = { onEvent(InteractionEvent.HomeSwipeDownSearch(it)) }
        )
        SwitchItem(
            title = stringResource(R.string.keep_original_swipe_up_title),
            summary = stringResource(R.string.keep_original_swipe_up_summary),
            checked = uiState.keepOriginalSwipeUpSearch,
            onCheckedChange = { onEvent(InteractionEvent.KeepOriginalSwipeUpSearch(it)) }
        )
        SliderItem(
            title = stringResource(R.string.home_swipe_threshold_title),
            summary = stringResource(R.string.home_swipe_threshold_summary),
            valueLabelResource = R.string.dp_value,
            valueToLabelNumber = { it.roundToInt() },
            value = uiState.homeSwipeDownThresholdDp.coerceIn(64f, 160f),
            valueRange = 64f..160f,
            steps = 5,
            onValueChangeFinished = {
                onEvent(InteractionEvent.HomeSwipeThreshold(it.coerceIn(64f, 160f)))
            }
        )

        SectionTitle(stringResource(R.string.interaction_lockscreen_section))
        SwitchItem(
            title = stringResource(R.string.customize_lockscreen_swipe_title),
            summary = stringResource(R.string.customize_lockscreen_swipe_summary),
            checked = uiState.customizeLockscreenSwipeDistance,
            onCheckedChange = { onEvent(InteractionEvent.CustomizeLockscreenSwipe(it)) }
        )
        SliderItem(
            title = stringResource(R.string.lockscreen_swipe_distance_title),
            summary = stringResource(R.string.lockscreen_swipe_distance_summary),
            valueLabelResource = R.string.percent_value,
            valueToLabelNumber = { (it * 100).roundToInt() },
            value = uiState.lockscreenSwipeDistanceScale.coerceIn(0.1f, 1.0f),
            valueRange = 0.1f..1.0f,
            steps = 8,
            onValueChangeFinished = {
                onEvent(InteractionEvent.LockscreenSwipeScale(it.coerceIn(0.1f, 1.0f)))
            }
        )

        SectionTitle(stringResource(R.string.interaction_diagnostics_section))
        val systemUiVersion = remember { packageVersion(context, "com.android.systemui") }
        val launcherVersion = remember { packageVersion(context, "com.sec.android.app.launcher") }
        ListItem(
            headlineContent = { Text(stringResource(R.string.environment_title)) },
            supportingContent = {
                Text(
                    stringResource(R.string.environment_summary) + "\n" +
                        "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · " +
                        "One UI $ONE_UI_VERSION\nSystemUI $systemUiVersion · One UI Home $launcherVersion\n" +
                        InteractionDiagnostics.lastWakeReason(context) + "\n" +
                        InteractionDiagnostics.lastAutoStartResult(context)
                )
            }
        )
    }

    if (showSoftRestartConfirmation) {
        AlertDialog(
            onDismissRequest = { showSoftRestartConfirmation = false },
            title = { Text(stringResource(R.string.soft_restart_confirm_title)) },
            text = { Text(stringResource(R.string.soft_restart_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSoftRestartConfirmation = false
                        scope.launch {
                            val succeeded = SoftRestartController.softRestartLikeKernelSu(context)
                            if (!succeeded) {
                                Toast.makeText(
                                    context,
                                    R.string.soft_restart_failed,
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                        }
                    },
                ) {
                    Text(stringResource(R.string.soft_restart_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSoftRestartConfirmation = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showAutoStartAppPicker) {
        AutoStartAppDialog(
            apps = userLaunchableApps,
            selectedPackageNames = uiState.autoStartPackages,
            onSelectionChange = { packageName, selected ->
                onEvent(InteractionEvent.AutoStartPackage(packageName, selected))
            },
            onClear = { onEvent(InteractionEvent.ClearAutoStartPackages) },
            onDismiss = { showAutoStartAppPicker = false },
        )
    }
}

@Composable
private fun AutoStartAppDialog(
    apps: List<UserLaunchableApp>,
    selectedPackageNames: Set<String>,
    onSelectionChange: (String, Boolean) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filteredApps = remember(apps, query) {
        if (query.isBlank()) {
            apps
        } else {
            apps.filter {
                it.label.contains(query, ignoreCase = true) ||
                    it.packageName.contains(query, ignoreCase = true)
            }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.auto_start_picker_title)) },
        text = {
            Column {
                Text(stringResource(R.string.auto_start_picker_summary))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.search_apps)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                if (filteredApps.isEmpty()) {
                    Text(stringResource(R.string.no_user_apps_found))
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 480.dp)) {
                        items(filteredApps, key = { it.packageName }) { app ->
                            val selected = app.packageName in selectedPackageNames
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .toggleable(
                                        value = selected,
                                        onValueChange = {
                                            onSelectionChange(app.packageName, it)
                                        },
                                        role = Role.Checkbox,
                                    )
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Image(
                                    bitmap = app.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp),
                                    contentScale = ContentScale.Fit,
                                )
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 12.dp),
                                ) {
                                    Text(app.label)
                                    Text(
                                        text = app.packageName,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                Checkbox(checked = selected, onCheckedChange = null)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.done))
            }
        },
        dismissButton = {
            TextButton(onClick = onClear) {
                Text(stringResource(R.string.clear_selection))
            }
        },
    )
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, modifier = Modifier.padding(start = 16.dp, top = 20.dp, end = 16.dp, bottom = 4.dp))
}

@Composable
private fun SliderItem(
    title: String,
    summary: String,
    valueLabelResource: Int,
    valueToLabelNumber: (Float) -> Int,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChangeFinished: (Float) -> Unit,
) {
    val hapticView = LocalView.current
    val intervalCount = steps + 1
    fun stepIndex(sliderValue: Float): Int {
        val fraction = if (valueRange.endInclusive == valueRange.start) {
            0f
        } else {
            (sliderValue - valueRange.start) /
                (valueRange.endInclusive - valueRange.start)
        }
        return (fraction * intervalCount).roundToInt().coerceIn(0, intervalCount)
    }
    var pendingValue by remember(value) { mutableFloatStateOf(value) }
    var lastHapticStep by remember(value, valueRange, steps) {
        mutableIntStateOf(stepIndex(value))
    }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        val liveValueLabel = stringResource(
            valueLabelResource,
            valueToLabelNumber(pendingValue),
        )
        Text("$title · $liveValueLabel")
        Text(
            text = summary,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp),
        )
        Slider(
            value = pendingValue,
            onValueChange = {
                pendingValue = it
                val currentStep = stepIndex(it)
                if (currentStep != lastHapticStep) {
                    val feedback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        HapticFeedbackConstants.SEGMENT_TICK
                    } else {
                        HapticFeedbackConstants.CLOCK_TICK
                    }
                    hapticView.performHapticFeedback(feedback)
                    lastHapticStep = currentStep
                }
            },
            onValueChangeFinished = { onValueChangeFinished(pendingValue) },
            valueRange = valueRange,
            steps = steps,
        )
    }
}

private fun isNotificationAccessGranted(context: Context): Boolean {
    val component = ComponentName(context, NotificationWakeService::class.java).flattenToString()
    return Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        ?.split(':')
        ?.any { it.equals(component, ignoreCase = true) } == true
}

private fun packageVersion(context: Context, packageName: String): String = try {
    context.packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
} catch (_: Throwable) {
    "?"
}

sealed interface InteractionEvent {
    @JvmInline value class WakeOnNotification(val value: Boolean) : InteractionEvent
    @JvmInline value class WakeDuration(val value: Int) : InteractionEvent
    @JvmInline value class WakeOnlyWhenScreenOff(val value: Boolean) : InteractionEvent
    @JvmInline value class NotificationDirectOpen(val value: Boolean) : InteractionEvent
    @JvmInline value class HomeSwipeDownSearch(val value: Boolean) : InteractionEvent
    @JvmInline value class KeepOriginalSwipeUpSearch(val value: Boolean) : InteractionEvent
    @JvmInline value class HomeSwipeThreshold(val value: Float) : InteractionEvent
    @JvmInline value class CustomizeLockscreenSwipe(val value: Boolean) : InteractionEvent
    @JvmInline value class LockscreenSwipeScale(val value: Float) : InteractionEvent
    @JvmInline value class AutoStartEnabled(val value: Boolean) : InteractionEvent
    data class AutoStartPackage(val packageName: String, val selected: Boolean) : InteractionEvent
    data object ClearAutoStartPackages : InteractionEvent
}

fun SettingViewModel.onInteractionEvent(event: InteractionEvent) {
    updateData { preference ->
        val current = preference.interaction
        val next = when (event) {
            is InteractionEvent.WakeOnNotification -> current.copy(wakeOnNotification = event.value)
            is InteractionEvent.WakeDuration -> current.copy(wakeDurationSeconds = event.value.coerceIn(1, 10))
            is InteractionEvent.WakeOnlyWhenScreenOff -> current.copy(wakeOnlyWhenScreenOff = event.value)
            is InteractionEvent.NotificationDirectOpen -> current.copy(notificationDirectOpenAfterAuth = event.value)
            is InteractionEvent.HomeSwipeDownSearch -> current.copy(homeSwipeDownSearch = event.value)
            is InteractionEvent.KeepOriginalSwipeUpSearch -> current.copy(keepOriginalSwipeUpSearch = event.value)
            is InteractionEvent.HomeSwipeThreshold -> current.copy(homeSwipeDownThresholdDp = event.value.coerceIn(64f, 160f))
            is InteractionEvent.CustomizeLockscreenSwipe -> current.copy(customizeLockscreenSwipeDistance = event.value)
            is InteractionEvent.LockscreenSwipeScale -> current.copy(lockscreenSwipeDistanceScale = event.value.coerceIn(0.1f, 1.0f))
            is InteractionEvent.AutoStartEnabled -> current.copy(autoStartEnabled = event.value)
            is InteractionEvent.AutoStartPackage -> {
                val packages = current.autoStartPackages.toMutableSet()
                if (event.selected) packages.add(event.packageName) else packages.remove(event.packageName)
                current.copy(autoStartPackages = packages)
            }
            InteractionEvent.ClearAutoStartPackages -> current.copy(autoStartPackages = emptySet())
        }
        preference.copy(interaction = next)
    }
}
