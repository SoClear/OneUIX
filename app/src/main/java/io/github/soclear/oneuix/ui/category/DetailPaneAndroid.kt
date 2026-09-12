package io.github.soclear.oneuix.ui.category

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import io.github.soclear.oneuix.R
import io.github.soclear.oneuix.common.Preference
import io.github.soclear.oneuix.ui.SettingViewModel
import io.github.soclear.oneuix.ui.component.SwitchItem
import kotlin.math.roundToInt

@Composable
fun DetailPaneAndroid(
    uiState: Preference.Android,
    onEvent: (AndroidEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.lock_clock),
            title = stringResource(id = R.string.disablePinVerifyPer72h_title),
            checked = uiState.disablePinVerifyPer72h,
            onCheckedChange = { onEvent(AndroidEvent.DisablePinVerifyPer72h(it)) }
        )
        Column {
            var max by remember { mutableIntStateOf(uiState.maxNeverKilledAppNum) }
            var expanded by rememberSaveable { mutableStateOf(false) }

            SwitchItem(
                title = stringResource(id = R.string.modifyMaxNeverKilledAppNum_title),
                modifier = Modifier.animateContentSize(),
                summary = if (uiState.modifyMaxNeverKilledAppNum) max.toString() else null,
                icon = ImageVector.vectorResource(id = R.drawable.apps),
                clickable = true,
                onClick = { expanded = !expanded },
                checked = uiState.modifyMaxNeverKilledAppNum,
                onCheckedChange = {
                    if (it && max == 5) {
                        expanded = true
                    } else if (!it) {
                        expanded = false
                    }
                    onEvent(AndroidEvent.ModifyMaxNeverKilledAppNum(it))
                }
            )

            AnimatedVisibility(expanded && uiState.modifyMaxNeverKilledAppNum) {
                Slider(
                    value = max.toFloat(),
                    onValueChange = { max = it.roundToInt() },
                    modifier = Modifier.padding(horizontal = 16.dp),
                    valueRange = 5f..30f,
                    steps = 24,
                    onValueChangeFinished = { onEvent(AndroidEvent.MaxNeverKilledAppNum(max)) }
                )
            }
        }
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.notifications),
            title = stringResource(id = R.string.setBlockableNotificationChannel_title),
            summary = stringResource(id = R.string.setBlockableNotificationChannel_summary),
            checked = uiState.setBlockableNotificationChannel,
            onCheckedChange = { onEvent(AndroidEvent.SetBlockableNotificationChannel(it)) }
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            SwitchItem(
                icon = ImageVector.vectorResource(id = R.drawable.block),
                title = stringResource(id = R.string.supportAppJumpBlock_title),
                summary = stringResource(id = R.string.supportAppJumpBlock_summary),
                checked = uiState.supportAppJumpBlock,
                onCheckedChange = { onEvent(AndroidEvent.SupportAppJumpBlock(it)) }
            )
        }
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.expand),
            title = stringResource(id = R.string.allowAllRotation_title),
            summary = stringResource(id = R.string.allowAllRotation_summary),
            checked = uiState.allowAllRotation,
            onCheckedChange = { onEvent(AndroidEvent.AllowAllRotation(it)) }
        )
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.google_play),
            title = stringResource(id = R.string.liftFcmNetworkLimit_title),
            summary = stringResource(id = R.string.liftFcmNetworkLimit_summary),
            checked = uiState.liftFcmNetworkLimit,
            onCheckedChange = { onEvent(AndroidEvent.LiftFcmNetworkLimit(it)) }
        )
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.mobile_screensaver),
            title = stringResource(id = R.string.disableScreenWakeOnPowerUnplugged_title),
            checked = uiState.disableScreenWakeOnPowerUnplugged,
            onCheckedChange = { onEvent(AndroidEvent.DisableScreenWakeOnPowerUnplugged(it)) }
        )
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.format_letter_spacing),
            title = stringResource(id = R.string.disableWritingToolkitGlobally_title),
            summary = stringResource(id = R.string.disableWritingToolkitGlobally_summary),
            checked = uiState.disableWritingToolkitGlobally,
            onCheckedChange = { onEvent(AndroidEvent.DisableWritingToolkitGlobally(it)) }
        )
    }
}

sealed interface AndroidEvent {
    @JvmInline
    value class DisablePinVerifyPer72h(val value: Boolean) : AndroidEvent

    @JvmInline
    value class ModifyMaxNeverKilledAppNum(val value: Boolean) : AndroidEvent

    @JvmInline
    value class MaxNeverKilledAppNum(val value: Int) : AndroidEvent

    @JvmInline
    value class SetBlockableNotificationChannel(val value: Boolean) : AndroidEvent

    @JvmInline
    value class SupportAppJumpBlock(val value: Boolean) : AndroidEvent

    @JvmInline
    value class AllowAllRotation(val value: Boolean) : AndroidEvent

    @JvmInline
    value class LiftFcmNetworkLimit(val value: Boolean) : AndroidEvent

    @JvmInline
    value class DisableScreenWakeOnPowerUnplugged(val value: Boolean) : AndroidEvent

    @JvmInline
    value class DisableWritingToolkitGlobally(val value: Boolean) : AndroidEvent
}

fun SettingViewModel.onAndroidEvent(event: AndroidEvent) {
    updateData { preference ->
        when (event) {
            is AndroidEvent.DisablePinVerifyPer72h -> {
                preference.copy(
                    android = preference.android.copy(
                        disablePinVerifyPer72h = event.value
                    )
                )
            }

            is AndroidEvent.ModifyMaxNeverKilledAppNum -> {
                preference.copy(
                    android = preference.android.copy(
                        modifyMaxNeverKilledAppNum = event.value
                    )
                )
            }

            is AndroidEvent.MaxNeverKilledAppNum -> {
                preference.copy(
                    android = preference.android.copy(
                        maxNeverKilledAppNum = event.value
                    )
                )
            }

            is AndroidEvent.SetBlockableNotificationChannel -> {
                preference.copy(
                    android = preference.android.copy(
                        setBlockableNotificationChannel = event.value
                    )
                )
            }

            is AndroidEvent.SupportAppJumpBlock -> {
                preference.copy(
                    android = preference.android.copy(
                        supportAppJumpBlock = event.value
                    )
                )
            }

            is AndroidEvent.AllowAllRotation -> {
                preference.copy(
                    android = preference.android.copy(
                        allowAllRotation = event.value
                    )
                )
            }

            is AndroidEvent.LiftFcmNetworkLimit -> {
                preference.copy(
                    android = preference.android.copy(
                        liftFcmNetworkLimit = event.value
                    )
                )
            }

            is AndroidEvent.DisableScreenWakeOnPowerUnplugged -> {
                preference.copy(
                    android = preference.android.copy(
                        disableScreenWakeOnPowerUnplugged = event.value
                    )
                )
            }

            is AndroidEvent.DisableWritingToolkitGlobally -> {
                preference.copy(
                    android = preference.android.copy(
                        disableWritingToolkitGlobally = event.value
                    )
                )
            }
        }
    }
}
