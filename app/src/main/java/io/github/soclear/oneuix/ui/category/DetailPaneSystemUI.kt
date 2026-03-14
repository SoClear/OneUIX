package io.github.soclear.oneuix.ui.category

import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import io.github.soclear.oneuix.R
import io.github.soclear.oneuix.data.Preference
import io.github.soclear.oneuix.ui.SettingViewModel
import io.github.soclear.oneuix.ui.component.SwitchItem
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
fun DetailPaneSystemUI(
    uiState: Preference.SystemUI,
    onEvent: (SystemUIEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        DividerText(R.string.status_bar)
        Column {
            var padding by remember {
                mutableFloatStateOf(uiState.statusBar.statusBarLeftPaddingDp)
            }
            var expanded by rememberSaveable { mutableStateOf(false) }

            SwitchItem(
                title = stringResource(id = R.string.statusBarLeftPaddingDp_title),
                modifier = Modifier.animateContentSize(),
                summary = if (uiState.statusBar.modifyStatusBarLeftPadding) {
                    "%.1fdp".format(padding)
                } else null,
                icon = ImageVector.vectorResource(id = R.drawable.padding),
                clickable = true,
                onClick = { expanded = !expanded },
                checked = uiState.statusBar.modifyStatusBarLeftPadding,
                onCheckedChange = {
                    if (it && padding == 0f) {
                        expanded = true
                    } else if (!it) {
                        expanded = false
                    }
                    onEvent(SystemUIEvent.StatusBar.ModifyStatusBarLeftPadding(it))
                }
            )
            AnimatedVisibility(expanded && uiState.statusBar.modifyStatusBarLeftPadding) {
                Slider(
                    value = padding,
                    onValueChange = { padding = it },
                    modifier = Modifier.padding(horizontal = 16.dp),
                    valueRange = 0f..100f,
                    onValueChangeFinished = {
                        onEvent(SystemUIEvent.StatusBar.StatusBarLeftPaddingDp(padding))
                    }
                )
            }
        }
        Column {
            var padding by remember {
                mutableFloatStateOf(uiState.statusBar.statusBarRightPaddingDp)
            }
            var expanded by rememberSaveable { mutableStateOf(false) }

            SwitchItem(
                title = stringResource(id = R.string.statusBarRightPaddingDp_title),
                modifier = Modifier.animateContentSize(),
                summary = if (uiState.statusBar.modifyStatusBarRightPadding) {
                    "%.1fdp".format(padding)
                } else null,
                icon = ImageVector.vectorResource(id = R.drawable.padding),
                clickable = true,
                onClick = { expanded = !expanded },
                checked = uiState.statusBar.modifyStatusBarRightPadding,
                onCheckedChange = {
                    if (it && padding == 0f) {
                        expanded = true
                    } else if (!it) {
                        expanded = false
                    }
                    onEvent(SystemUIEvent.StatusBar.ModifyStatusBarRightPadding(it))
                }
            )
            AnimatedVisibility(expanded && uiState.statusBar.modifyStatusBarRightPadding) {
                Slider(
                    value = padding,
                    onValueChange = { padding = it },
                    modifier = Modifier.padding(horizontal = 16.dp),
                    valueRange = 0f..100f,
                    onValueChangeFinished = {
                        onEvent(SystemUIEvent.StatusBar.StatusBarRightPaddingDp(padding))
                    }
                )
            }
        }
        Column {
            var widthScale by remember {
                mutableFloatStateOf(uiState.statusBar.batteryIconWidthScale)
            }
            var expanded by rememberSaveable { mutableStateOf(false) }

            SwitchItem(
                title = stringResource(id = R.string.setBatteryIconWidthScale_title),
                modifier = Modifier.animateContentSize(),
                summary = if (uiState.statusBar.setBatteryIconWidthScale) {
                    "%.2f".format(widthScale)
                } else null,
                icon = ImageVector.vectorResource(id = R.drawable.battery),
                clickable = true,
                onClick = { expanded = !expanded },
                checked = uiState.statusBar.setBatteryIconWidthScale,
                onCheckedChange = {
                    if (it && widthScale == 0f) {
                        expanded = true
                    } else if (!it) {
                        expanded = false
                    }
                    onEvent(SystemUIEvent.StatusBar.SetBatteryIconWidthScale(it))
                }
            )
            AnimatedVisibility(expanded && uiState.statusBar.setBatteryIconWidthScale) {
                Slider(
                    value = widthScale,
                    onValueChange = { widthScale = it },
                    modifier = Modifier.padding(horizontal = 16.dp),
                    valueRange = 0.5f..2f,
                    onValueChangeFinished = {
                        onEvent(SystemUIEvent.StatusBar.BatteryIconWidthScale(widthScale))
                    }
                )
            }
        }
        Column {
            var heightScale by remember {
                mutableFloatStateOf(uiState.statusBar.batteryIconHeightScale)
            }
            var expanded by rememberSaveable { mutableStateOf(false) }

            SwitchItem(
                title = stringResource(id = R.string.setBatteryIconHeightScale_title),
                modifier = Modifier.animateContentSize(),
                summary = if (uiState.statusBar.setBatteryIconHeightScale) {
                    "%.2f".format(heightScale)
                } else null,
                icon = ImageVector.vectorResource(id = R.drawable.battery),
                clickable = true,
                onClick = { expanded = !expanded },
                checked = uiState.statusBar.setBatteryIconHeightScale,
                onCheckedChange = {
                    if (it && heightScale == 0f) {
                        expanded = true
                    } else if (!it) {
                        expanded = false
                    }
                    onEvent(SystemUIEvent.StatusBar.SetBatteryIconHeightScale(it))
                }
            )
            AnimatedVisibility(expanded && uiState.statusBar.setBatteryIconHeightScale) {
                Slider(
                    value = heightScale,
                    onValueChange = { heightScale = it },
                    modifier = Modifier.padding(horizontal = 16.dp),
                    valueRange = 0.5f..2f,
                    onValueChangeFinished = {
                        onEvent(SystemUIEvent.StatusBar.BatteryIconHeightScale(heightScale))
                    }
                )
            }
        }
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            SwitchItem(
                icon = ImageVector.vectorResource(id = R.drawable.battery),
                title = stringResource(id = R.string.hideBatteryPercentageSign_title),
                summary = stringResource(id = R.string.hideBatteryPercentageSign_summary),
                checked = uiState.statusBar.hideBatteryPercentageSign,
                onCheckedChange = {
                    onEvent(SystemUIEvent.StatusBar.HideBatteryPercentageSign(it))
                }
            )
        }
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.net_speed),
            title = stringResource(id = R.string.supportRealTimeNetworkSpeed_title),
            summary = stringResource(id = R.string.supportRealTimeNetworkSpeed_summary),
            checked = uiState.statusBar.supportRealTimeNetworkSpeed,
            onCheckedChange = {
                onEvent(SystemUIEvent.StatusBar.SupportRealTimeNetworkSpeed(it))
            }
        )
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.net_speed),
            title = stringResource(id = R.string.showSeparateUpDownNetworkSpeeds_title),
            summary = stringResource(id = R.string.showSeparateUpDownNetworkSpeeds_summary),
            checked = uiState.statusBar.showSeparateUpDownNetworkSpeeds,
            onCheckedChange = {
                onEvent(SystemUIEvent.StatusBar.ShowSeparateUpDownNetworkSpeeds(it))
            }
        )
        Column {
            var expanded by rememberSaveable { mutableStateOf(false) }

            SwitchItem(
                title = stringResource(id = R.string.setStatusBarClockFormat_title),
                modifier = Modifier.animateContentSize(),
                summary = if (uiState.statusBar.setStatusBarClockFormat) {
                    uiState.statusBar.statusBarClockFormat
                } else null,
                icon = ImageVector.vectorResource(id = R.drawable.nest_clock_farsight_digital),
                clickable = true,
                onClick = { expanded = !expanded },
                checked = uiState.statusBar.setStatusBarClockFormat,
                onCheckedChange = {
                    if (it && uiState.statusBar.statusBarClockFormat == "HH:mm") {
                        expanded = true
                    } else if (!it) {
                        expanded = false
                    }
                    onEvent(SystemUIEvent.StatusBar.SetStatusBarClockFormat(it))
                }
            )

            AnimatedVisibility(expanded && uiState.statusBar.setStatusBarClockFormat) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) {
                    var tempDataTimeFormat by remember {
                        mutableStateOf(uiState.statusBar.statusBarClockFormat)
                    }
                    var label by remember { mutableStateOf("") }
                    OutlinedTextField(
                        value = tempDataTimeFormat,
                        onValueChange = { tempDataTimeFormat = it },
                        modifier = Modifier.weight(1f),
                        label = { Text(text = label) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            var right = true
                            label = try {
                                DateTimeFormatter
                                    .ofPattern(tempDataTimeFormat)
                                    .format(LocalDateTime.now())
                            } catch (_: Throwable) {
                                right = false
                                "error"
                            }
                            if (right) {
                                onEvent(
                                    SystemUIEvent.StatusBar.StatusBarClockFormat(
                                        tempDataTimeFormat
                                    )
                                )
                            }
                        }
                    ) {
                        Text(text = stringResource(id = R.string.confirm))
                    }
                }
            }
        }
        SwitchItem(
            title = stringResource(id = R.string.updateStatusBarClockEverySecond_title),
            summary = stringResource(id = R.string.updateStatusBarClockEverySecond_summary),
            icon = ImageVector.vectorResource(id = R.drawable.nest_clock_farsight_digital),
            checked = uiState.statusBar.updateStatusBarClockEverySecond,
            onCheckedChange = {
                onEvent(SystemUIEvent.StatusBar.UpdateStatusBarClockEverySecond(it))
            }
        )
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.folder_managed),
            title = stringResource(id = R.string.hideSecureFolderStatusBarIcon_title),
            checked = uiState.statusBar.hideSecureFolderStatusBarIcon,
            onCheckedChange = {
                onEvent(SystemUIEvent.StatusBar.HideSecureFolderStatusBarIcon(it))
            }
        )
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.mobile_screensaver),
            title = stringResource(id = R.string.doubleTapStatusBarToSleep_title),
            checked = uiState.statusBar.doubleTapStatusBarToSleep,
            onCheckedChange = {
                onEvent(SystemUIEvent.StatusBar.DoubleTapStatusBarToSleep(it))
            }
        )
        Column {
            var max by remember {
                mutableIntStateOf(uiState.statusBar.statusBarMaxNotificationIcons)
            }
            var expanded by rememberSaveable { mutableStateOf(false) }

            SwitchItem(
                title = stringResource(id = R.string.setStatusBarMaxNotificationIcons_title),
                modifier = Modifier.animateContentSize(),
                summary = if (uiState.statusBar.modifyStatusBarMaxNotificationIcons) " $max" else null,
                icon = ImageVector.vectorResource(id = R.drawable.notifications),
                clickable = true,
                onClick = { expanded = !expanded },
                checked = uiState.statusBar.modifyStatusBarMaxNotificationIcons,
                onCheckedChange = {
                    if (it && max == 4) {
                        expanded = true
                    } else if (!it) {
                        expanded = false
                    }
                    onEvent(SystemUIEvent.StatusBar.ModifyStatusBarMaxNotificationIcons(it))
                }
            )
            AnimatedVisibility(expanded && uiState.statusBar.modifyStatusBarMaxNotificationIcons) {
                Slider(
                    value = max.toFloat(),
                    onValueChange = { max = it.roundToInt() },
                    modifier = Modifier.padding(horizontal = 16.dp),
                    valueRange = 4f..20f,
                    steps = 15,
                    onValueChangeFinished = {
                        onEvent(SystemUIEvent.StatusBar.StatusBarMaxNotificationIcons(max))
                    }
                )
            }
        }
        Column {
            var expanded by rememberSaveable { mutableStateOf(false) }

            SwitchItem(
                title = stringResource(id = R.string.setCustomCarrierName_title),
                modifier = Modifier.animateContentSize(),
                summary = if (uiState.statusBar.setCustomCarrierName) {
                    uiState.statusBar.customCarrierName
                } else null,
                icon = ImageVector.vectorResource(id = R.drawable.sim_card),
                clickable = true,
                onClick = { expanded = !expanded },
                checked = uiState.statusBar.setCustomCarrierName,
                onCheckedChange = {
                    if (it && uiState.statusBar.customCarrierName.isEmpty()) {
                        expanded = true
                    } else if (!it) {
                        expanded = false
                    }
                    onEvent(SystemUIEvent.StatusBar.SetCustomCarrierName(it))
                }
            )

            AnimatedVisibility(expanded && uiState.statusBar.setCustomCarrierName) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) {
                    var tempCustomCarrierName by remember {
                        mutableStateOf(uiState.statusBar.customCarrierName)
                    }

                    OutlinedTextField(
                        value = tempCustomCarrierName,
                        onValueChange = { tempCustomCarrierName = it },
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onEvent(
                                SystemUIEvent.StatusBar.CustomCarrierName(
                                    tempCustomCarrierName
                                )
                            )
                        }
                    ) {
                        Text(text = stringResource(id = R.string.confirm))
                    }
                }
            }
        }

        DividerText(R.string.qs)
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.format_letter_spacing),
            title = stringResource(id = R.string.setQsClockMonospaced_title),
            checked = uiState.qs.setQsClockMonospaced,
            onCheckedChange = {
                onEvent(SystemUIEvent.QS.SetQsClockMonospaced(it))
            }
        )
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            SwitchItem(
                icon = ImageVector.vectorResource(id = R.drawable.tile_medium),
                title = stringResource(id = R.string.hideDeviceControlQsTile_title),
                checked = uiState.qs.hideDeviceControlQsTile,
                onCheckedChange = {
                    onEvent(SystemUIEvent.QS.HideDeviceControlQsTile(it))
                }
            )
            SwitchItem(
                icon = ImageVector.vectorResource(id = R.drawable.five_g),
                title = stringResource(id = R.string.turnOn5gQsTile_title),
                summary = stringResource(id = R.string.turnOn5gQsTile_summary),
                checked = uiState.qs.turnOn5gQsTile,
                onCheckedChange = {
                    onEvent(SystemUIEvent.QS.TurnOn5gQsTile(it))
                }
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            ListItem(
                headlineContent = { Text(stringResource(id = R.string.turnOn5gQsTile_title)) },
                leadingContent = {
                    Icon(
                        ImageVector.vectorResource(id = R.drawable.five_g),
                        stringResource(id = R.string.turnOn5gQsTile_title)
                    )
                },
                supportingContent = { Text(stringResource(id = R.string.root5gQsTile_summary)) }
            )
            SwitchItem(
                icon = ImageVector.vectorResource(id = R.drawable.tile_medium),
                title = stringResource(id = R.string.hideQsBarMediaPlayer_title),
                checked = uiState.qs.hideQsBarMediaPlayer,
                onCheckedChange = {
                    onEvent(SystemUIEvent.QS.HideQsBarMediaPlayer(it))
                }
            )
            SwitchItem(
                icon = ImageVector.vectorResource(id = R.drawable.tile_medium),
                title = stringResource(id = R.string.hideQsBarNearbyDevicesAndDeviceControl_title),
                checked = uiState.qs.hideQsBarNearbyDevicesAndDeviceControl,
                onCheckedChange = {
                    onEvent(SystemUIEvent.QS.HideQsBarNearbyDevicesAndDeviceControl(it))
                }
            )
            SwitchItem(
                icon = ImageVector.vectorResource(id = R.drawable.tile_medium),
                title = stringResource(id = R.string.hideQsBarSecurityFooter_title),
                summary = stringResource(id = R.string.hideQsBarSecurityFooter_summary),
                checked = uiState.qs.hideQsBarSecurityFooter,
                onCheckedChange = {
                    onEvent(SystemUIEvent.QS.HideQsBarSecurityFooter(it))
                }
            )
            SwitchItem(
                icon = ImageVector.vectorResource(id = R.drawable.tile_medium),
                title = stringResource(id = R.string.hideQsBarSmartViewAndModes_title),
                checked = uiState.qs.hideQsBarSmartViewAndModes,
                onCheckedChange = {
                    onEvent(SystemUIEvent.QS.HideQsBarSmartViewAndModes(it))
                }
            )
            SwitchItem(
                icon = ImageVector.vectorResource(id = R.drawable.expand),
                title = stringResource(id = R.string.alwaysExpandQsTileChunk_title),
                checked = uiState.qs.alwaysExpandQsTileChunk,
                onCheckedChange = {
                    onEvent(SystemUIEvent.QS.AlwaysExpandQsTileChunk(it))
                }
            )
            SwitchItem(
                title = stringResource(id = R.string.alwaysShowTimeDateOnQs_title),
                icon = ImageVector.vectorResource(id = R.drawable.nest_clock_farsight_digital),
                checked = uiState.qs.alwaysShowTimeDateOnQs,
                onCheckedChange = {
                    onEvent(SystemUIEvent.QS.AlwaysShowTimeDateOnQs(it))
                }
            )
            SwitchItem(
                icon = ImageVector.vectorResource(id = R.drawable.light_mode),
                title = stringResource(id = R.string.addBrightnessProgressToQsBar_title),
                checked = uiState.qs.addBrightnessProgressToQsBar,
                onCheckedChange = {
                    onEvent(SystemUIEvent.QS.AddBrightnessProgressToQsBar(it))
                }
            )
            SwitchItem(
                icon = ImageVector.vectorResource(id = R.drawable.music_note),
                title = stringResource(id = R.string.addVolumeProgressToQsBar_title),
                checked = uiState.qs.addVolumeProgressToQsBar,
                onCheckedChange = {
                    onEvent(SystemUIEvent.QS.AddVolumeProgressToQsBar(it))
                }
            )
            SwitchItem(
                icon = ImageVector.vectorResource(id = R.drawable.today),
                title = stringResource(id = R.string.showTraditionalChineseDateOnQS_title),
                checked = uiState.qs.showTraditionalChineseDateOnQS,
                onCheckedChange = {
                    onEvent(SystemUIEvent.QS.ShowTraditionalChineseDateOnQS(it))
                }
            )
            Column {
                var textSize by remember { mutableFloatStateOf(uiState.qs.qsClockTextSize) }
                var expanded by rememberSaveable { mutableStateOf(false) }

                SwitchItem(
                    title = stringResource(id = R.string.modifyQSClockTextSize_title),
                    modifier = Modifier.animateContentSize(),
                    summary = if (uiState.qs.modifyQSClockTextSize) {
                        " %.1fsp".format(textSize)
                    } else null,
                    icon = ImageVector.vectorResource(id = R.drawable.format_size),
                    clickable = true,
                    onClick = { expanded = !expanded },
                    checked = uiState.qs.modifyQSClockTextSize,
                    onCheckedChange = {
                        if (it && textSize == 32f) {
                            expanded = true
                        } else if (!it) {
                            expanded = false
                        }
                        onEvent(SystemUIEvent.QS.ModifyQSClockTextSize(it))
                    }
                )

                AnimatedVisibility(expanded && uiState.qs.modifyQSClockTextSize) {
                    Slider(
                        value = textSize,
                        onValueChange = { textSize = it },
                        modifier = Modifier.padding(horizontal = 16.dp),
                        valueRange = 15f..70f,
                        onValueChangeFinished = {
                            onEvent(SystemUIEvent.QS.QSClockTextSize(textSize))
                        }
                    )
                }
            }


            DividerText(R.string.aod)
            SwitchItem(
                icon = ImageVector.vectorResource(id = R.drawable.battery),
                title = stringResource(id = R.string.hideAODStatusBar_title),
                checked = uiState.aod.hideAODStatusBar,
                onCheckedChange = {
                    onEvent(SystemUIEvent.AOD.HideAODStatusBar(it))
                }
            )
            SwitchItem(
                icon = ImageVector.vectorResource(id = R.drawable.today),
                title = stringResource(id = R.string.aodLockSupportLunar_title),
                checked = uiState.aod.aodLockSupportLunar,
                onCheckedChange = {
                    onEvent(SystemUIEvent.AOD.AODLockSupportLunar(it))
                }
            )
        }

        DividerText(R.string.other)
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.screenshot),
            title = stringResource(id = R.string.disableScreenshotCaptureSound_title),
            checked = uiState.other.disableScreenshotCaptureSound,
            onCheckedChange = {
                onEvent(SystemUIEvent.Other.DisableScreenshotCaptureSound(it))
            }
        )
    }
}

@Composable
private fun DividerText(@StringRes id: Int) = Text(
    text = stringResource(id),
    modifier = Modifier.padding(start = 16.dp, top = 32.dp, end = 16.dp),
    color = MaterialTheme.colorScheme.primary,
)


sealed interface SystemUIEvent {
    sealed interface StatusBar : SystemUIEvent {
        @JvmInline
        value class ModifyStatusBarLeftPadding(val value: Boolean) : StatusBar

        @JvmInline
        value class StatusBarLeftPaddingDp(val value: Float) : StatusBar

        @JvmInline
        value class ModifyStatusBarRightPadding(val value: Boolean) : StatusBar

        @JvmInline
        value class StatusBarRightPaddingDp(val value: Float) : StatusBar

        @JvmInline
        value class SetBatteryIconWidthScale(val value: Boolean) : StatusBar

        @JvmInline
        value class BatteryIconWidthScale(val value: Float) : StatusBar

        @JvmInline
        value class SetBatteryIconHeightScale(val value: Boolean) : StatusBar

        @JvmInline
        value class BatteryIconHeightScale(val value: Float) : StatusBar

        @JvmInline
        value class HideBatteryPercentageSign(val value: Boolean) : StatusBar

        @JvmInline
        value class SupportRealTimeNetworkSpeed(val value: Boolean) : StatusBar

        @JvmInline
        value class ShowSeparateUpDownNetworkSpeeds(val value: Boolean) : StatusBar

        @JvmInline
        value class SetStatusBarClockFormat(val value: Boolean) : StatusBar

        @JvmInline
        value class StatusBarClockFormat(val value: String) : StatusBar

        @JvmInline
        value class UpdateStatusBarClockEverySecond(val value: Boolean) : StatusBar

        @JvmInline
        value class HideSecureFolderStatusBarIcon(val value: Boolean) : StatusBar

        @JvmInline
        value class DoubleTapStatusBarToSleep(val value: Boolean) : StatusBar

        @JvmInline
        value class ModifyStatusBarMaxNotificationIcons(val value: Boolean) : StatusBar

        @JvmInline
        value class StatusBarMaxNotificationIcons(val value: Int) : StatusBar

        @JvmInline
        value class SetCustomCarrierName(val value: Boolean) : StatusBar

        @JvmInline
        value class CustomCarrierName(val value: String) : StatusBar
    }

    sealed interface QS : SystemUIEvent {
        @JvmInline
        value class SetQsClockMonospaced(val value: Boolean) : QS

        @JvmInline
        value class HideDeviceControlQsTile(val value: Boolean) : QS

        @JvmInline
        value class TurnOn5gQsTile(val value: Boolean) : QS

        @JvmInline
        value class HideQsBarMediaPlayer(val value: Boolean) : QS

        @JvmInline
        value class HideQsBarNearbyDevicesAndDeviceControl(val value: Boolean) : QS

        @JvmInline
        value class HideQsBarSecurityFooter(val value: Boolean) : QS

        @JvmInline
        value class HideQsBarSmartViewAndModes(val value: Boolean) : QS

        @JvmInline
        value class AlwaysExpandQsTileChunk(val value: Boolean) : QS

        @JvmInline
        value class AlwaysShowTimeDateOnQs(val value: Boolean) : QS

        @JvmInline
        value class AddBrightnessProgressToQsBar(val value: Boolean) : QS

        @JvmInline
        value class AddVolumeProgressToQsBar(val value: Boolean) : QS

        @JvmInline
        value class ShowTraditionalChineseDateOnQS(val value: Boolean) : QS

        @JvmInline
        value class ModifyQSClockTextSize(val value: Boolean) : QS

        @JvmInline
        value class QSClockTextSize(val value: Float) : QS
    }

    sealed interface AOD : SystemUIEvent {
        @JvmInline
        value class HideAODStatusBar(val value: Boolean) : AOD

        @JvmInline
        value class AODLockSupportLunar(val value: Boolean) : AOD
    }

    sealed interface Other : SystemUIEvent {
        @JvmInline
        value class DisableScreenshotCaptureSound(val value: Boolean) : Other
    }
}

fun SettingViewModel.onSystemUIEvent(event: SystemUIEvent) {
    when (event) {
        is SystemUIEvent.StatusBar -> onStatusBarEvent(event)
        is SystemUIEvent.QS -> onQSEvent(event)
        is SystemUIEvent.AOD -> onAODEvent(event)
        is SystemUIEvent.Other -> onOtherEvent(event)
    }
}

private fun SettingViewModel.onStatusBarEvent(event: SystemUIEvent.StatusBar) {
    updateData { preference ->
        when (event) {
            is SystemUIEvent.StatusBar.ModifyStatusBarLeftPadding -> {
                preference.copy(
                    systemUI = preference.systemUI.copy(
                        statusBar = preference.systemUI.statusBar.copy(
                            modifyStatusBarLeftPadding = event.value
                        )
                    )
                )
            }

            is SystemUIEvent.StatusBar.StatusBarLeftPaddingDp -> {
                preference.copy(
                    systemUI = preference.systemUI.copy(
                        statusBar = preference.systemUI.statusBar.copy(
                            statusBarLeftPaddingDp = event.value
                        )
                    )
                )
            }

            is SystemUIEvent.StatusBar.ModifyStatusBarRightPadding -> {
                preference.copy(
                    systemUI = preference.systemUI.copy(
                        statusBar = preference.systemUI.statusBar.copy(
                            modifyStatusBarRightPadding = event.value
                        )
                    )
                )
            }

            is SystemUIEvent.StatusBar.StatusBarRightPaddingDp -> {
                preference.copy(
                    systemUI = preference.systemUI.copy(
                        statusBar = preference.systemUI.statusBar.copy(
                            statusBarRightPaddingDp = event.value
                        )
                    )
                )
            }

            is SystemUIEvent.StatusBar.SetBatteryIconWidthScale -> {
                preference.copy(
                    systemUI = preference.systemUI.copy(
                        statusBar = preference.systemUI.statusBar.copy(
                            setBatteryIconWidthScale = event.value
                        )
                    )
                )
            }

            is SystemUIEvent.StatusBar.BatteryIconWidthScale -> {
                preference.copy(
                    systemUI = preference.systemUI.copy(
                        statusBar = preference.systemUI.statusBar.copy(
                            batteryIconWidthScale = event.value
                        )
                    )
                )
            }

            is SystemUIEvent.StatusBar.SetBatteryIconHeightScale -> {
                preference.copy(
                    systemUI = preference.systemUI.copy(
                        statusBar = preference.systemUI.statusBar.copy(
                            setBatteryIconHeightScale = event.value
                        )
                    )
                )
            }

            is SystemUIEvent.StatusBar.BatteryIconHeightScale -> {
                preference.copy(
                    systemUI = preference.systemUI.copy(
                        statusBar = preference.systemUI.statusBar.copy(
                            batteryIconHeightScale = event.value
                        )
                    )
                )
            }

            is SystemUIEvent.StatusBar.HideBatteryPercentageSign -> {
                preference.copy(
                    systemUI = preference.systemUI.copy(
                        statusBar = preference.systemUI.statusBar.copy(
                            hideBatteryPercentageSign = event.value
                        )
                    )
                )
            }

            is SystemUIEvent.StatusBar.SupportRealTimeNetworkSpeed -> {
                preference.copy(
                    systemUI = preference.systemUI.copy(
                        statusBar = preference.systemUI.statusBar.copy(
                            supportRealTimeNetworkSpeed = event.value
                        )
                    )
                )
            }


            is SystemUIEvent.StatusBar.ShowSeparateUpDownNetworkSpeeds -> {
                preference.copy(
                    systemUI = preference.systemUI.copy(
                        statusBar = preference.systemUI.statusBar.copy(
                            showSeparateUpDownNetworkSpeeds = event.value
                        )
                    )
                )
            }

            is SystemUIEvent.StatusBar.SetStatusBarClockFormat -> {
                preference.copy(
                    systemUI = preference.systemUI.copy(
                        statusBar = preference.systemUI.statusBar.copy(
                            setStatusBarClockFormat = event.value
                        )
                    )
                )
            }

            is SystemUIEvent.StatusBar.StatusBarClockFormat -> {
                preference.copy(
                    systemUI = preference.systemUI.copy(
                        statusBar = preference.systemUI.statusBar.copy(
                            statusBarClockFormat = event.value
                        )
                    )
                )
            }

            is SystemUIEvent.StatusBar.UpdateStatusBarClockEverySecond -> {
                preference.copy(
                    systemUI = preference.systemUI.copy(
                        statusBar = preference.systemUI.statusBar.copy(
                            updateStatusBarClockEverySecond = event.value
                        )
                    )
                )
            }

            is SystemUIEvent.StatusBar.HideSecureFolderStatusBarIcon -> {
                preference.copy(
                    systemUI = preference.systemUI.copy(
                        statusBar = preference.systemUI.statusBar.copy(
                            hideSecureFolderStatusBarIcon = event.value
                        )
                    )
                )
            }

            is SystemUIEvent.StatusBar.DoubleTapStatusBarToSleep -> {
                preference.copy(
                    systemUI = preference.systemUI.copy(
                        statusBar = preference.systemUI.statusBar.copy(
                            doubleTapStatusBarToSleep = event.value
                        )
                    )
                )
            }

            is SystemUIEvent.StatusBar.ModifyStatusBarMaxNotificationIcons -> {
                preference.copy(
                    systemUI = preference.systemUI.copy(
                        statusBar = preference.systemUI.statusBar.copy(
                            modifyStatusBarMaxNotificationIcons = event.value
                        )
                    )
                )
            }

            is SystemUIEvent.StatusBar.StatusBarMaxNotificationIcons -> {
                preference.copy(
                    systemUI = preference.systemUI.copy(
                        statusBar = preference.systemUI.statusBar.copy(
                            statusBarMaxNotificationIcons = event.value
                        )
                    )
                )
            }

            is SystemUIEvent.StatusBar.SetCustomCarrierName -> {
                preference.copy(
                    systemUI = preference.systemUI.copy(
                        statusBar = preference.systemUI.statusBar.copy(
                            setCustomCarrierName = event.value
                        )
                    )
                )
            }

            is SystemUIEvent.StatusBar.CustomCarrierName -> {
                preference.copy(
                    systemUI = preference.systemUI.copy(
                        statusBar = preference.systemUI.statusBar.copy(
                            customCarrierName = event.value
                        )
                    )
                )
            }
        }
    }
}

private fun SettingViewModel.onQSEvent(event: SystemUIEvent.QS) {
    updateData { preference ->
        when (event) {
            is SystemUIEvent.QS.SetQsClockMonospaced -> {
                preference.copy(
                    systemUI = preference.systemUI.copy(
                        qs = preference.systemUI.qs.copy(
                            setQsClockMonospaced = event.value
                        )
                    )
                )
            }

            is SystemUIEvent.QS.HideDeviceControlQsTile -> {
                preference.copy(
                    systemUI = preference.systemUI.copy(
                        qs = preference.systemUI.qs.copy(
                            hideDeviceControlQsTile = event.value
                        )
                    )
                )
            }

            is SystemUIEvent.QS.TurnOn5gQsTile -> {
                preference.copy(
                    systemUI = preference.systemUI.copy(
                        qs = preference.systemUI.qs.copy(
                            turnOn5gQsTile = event.value
                        )
                    )
                )
            }

            is SystemUIEvent.QS.HideQsBarMediaPlayer -> {
                preference.copy(
                    systemUI = preference.systemUI.copy(
                        qs = preference.systemUI.qs.copy(
                            hideQsBarMediaPlayer = event.value
                        )
                    )
                )
            }

            is SystemUIEvent.QS.HideQsBarNearbyDevicesAndDeviceControl -> {
                preference.copy(
                    systemUI = preference.systemUI.copy(
                        qs = preference.systemUI.qs.copy(
                            hideQsBarNearbyDevicesAndDeviceControl = event.value
                        )
                    )
                )
            }

            is SystemUIEvent.QS.HideQsBarSecurityFooter -> {
                preference.copy(
                    systemUI = preference.systemUI.copy(
                        qs = preference.systemUI.qs.copy(
                            hideQsBarSecurityFooter = event.value
                        )
                    )
                )
            }

            is SystemUIEvent.QS.HideQsBarSmartViewAndModes -> {
                preference.copy(
                    systemUI = preference.systemUI.copy(
                        qs = preference.systemUI.qs.copy(
                            hideQsBarSmartViewAndModes = event.value
                        )
                    )
                )
            }

            is SystemUIEvent.QS.AlwaysExpandQsTileChunk -> {
                preference.copy(
                    systemUI = preference.systemUI.copy(
                        qs = preference.systemUI.qs.copy(
                            alwaysExpandQsTileChunk = event.value
                        )
                    )
                )
            }

            is SystemUIEvent.QS.AlwaysShowTimeDateOnQs -> {
                preference.copy(
                    systemUI = preference.systemUI.copy(
                        qs = preference.systemUI.qs.copy(
                            alwaysShowTimeDateOnQs = event.value
                        )
                    )
                )
            }

            is SystemUIEvent.QS.AddBrightnessProgressToQsBar -> {
                preference.copy(
                    systemUI = preference.systemUI.copy(
                        qs = preference.systemUI.qs.copy(
                            addBrightnessProgressToQsBar = event.value
                        )
                    )
                )
            }

            is SystemUIEvent.QS.AddVolumeProgressToQsBar -> {
                preference.copy(
                    systemUI = preference.systemUI.copy(
                        qs = preference.systemUI.qs.copy(
                            addVolumeProgressToQsBar = event.value
                        )
                    )
                )
            }

            is SystemUIEvent.QS.ShowTraditionalChineseDateOnQS -> {
                preference.copy(
                    systemUI = preference.systemUI.copy(
                        qs = preference.systemUI.qs.copy(
                            showTraditionalChineseDateOnQS = event.value
                        )
                    )
                )
            }

            is SystemUIEvent.QS.ModifyQSClockTextSize -> {
                preference.copy(
                    systemUI = preference.systemUI.copy(
                        qs = preference.systemUI.qs.copy(
                            modifyQSClockTextSize = event.value
                        )
                    )
                )
            }

            is SystemUIEvent.QS.QSClockTextSize -> {
                preference.copy(
                    systemUI = preference.systemUI.copy(
                        qs = preference.systemUI.qs.copy(
                            qsClockTextSize = event.value
                        )
                    )
                )
            }
        }
    }
}

private fun SettingViewModel.onAODEvent(event: SystemUIEvent.AOD) {
    updateData { preference ->
        when (event) {
            is SystemUIEvent.AOD.HideAODStatusBar -> {
                preference.copy(
                    systemUI = preference.systemUI.copy(
                        aod = preference.systemUI.aod.copy(
                            hideAODStatusBar = event.value
                        )
                    )
                )
            }

            is SystemUIEvent.AOD.AODLockSupportLunar -> {
                preference.copy(
                    systemUI = preference.systemUI.copy(
                        aod = preference.systemUI.aod.copy(
                            aodLockSupportLunar = event.value
                        )
                    )
                )
            }
        }
    }
}

private fun SettingViewModel.onOtherEvent(event: SystemUIEvent.Other) {
    updateData { preference ->
        when (event) {
            is SystemUIEvent.Other.DisableScreenshotCaptureSound -> {
                preference.copy(
                    systemUI = preference.systemUI.copy(
                        other = preference.systemUI.other.copy(
                            disableScreenshotCaptureSound = event.value
                        )
                    )
                )
            }
        }
    }
}
