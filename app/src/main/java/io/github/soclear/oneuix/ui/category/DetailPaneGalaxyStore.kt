package io.github.soclear.oneuix.ui.category

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import io.github.soclear.oneuix.R
import io.github.soclear.oneuix.common.Package
import io.github.soclear.oneuix.common.Preference
import io.github.soclear.oneuix.ui.SettingViewModel
import io.github.soclear.oneuix.ui.component.ForceStopApp
import io.github.soclear.oneuix.ui.component.SimOperatorItem
import io.github.soclear.oneuix.ui.component.SwitchItem

@Composable
fun DetailPaneGalaxyStore(
    uiState: Preference.Other,
    onEvent: (GalaxyStoreEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    PackagePane(modifier) {
        ForceStopApp(Package.STORE)
        SwitchItem(
            icon = ImageVector.vectorResource(id = R.drawable.ad_off),
            title = stringResource(id = R.string.blockGalaxyStoreAds_title),
            summary = stringResource(id = R.string.blockGalaxyStoreAds_summary),
            checked = uiState.blockGalaxyStoreAds,
            onCheckedChange = { onEvent(GalaxyStoreEvent.BlockGalaxyStoreAds(it)) }
        )
        SwitchItem(
            icon = ImageVector.vectorResource(R.drawable.ad_off),
            title = stringResource(R.string.hideGalaxyStoreDiscover_title),
            checked = uiState.hideGalaxyStoreDiscover,
            onCheckedChange = { onEvent(GalaxyStoreEvent.HideDiscover(it)) }
        )
        SwitchItem(
            icon = ImageVector.vectorResource(R.drawable.ad_off),
            title = stringResource(R.string.hideGalaxyStoreBenefits_title),
            checked = uiState.hideGalaxyStoreBenefits,
            onCheckedChange = { onEvent(GalaxyStoreEvent.HideBenefits(it)) }
        )
        SimOperatorItem(
            title = stringResource(R.string.galaxyStoreRegion_title),
            checked = uiState.setGalaxyStoreSimOperator,
            value = uiState.galaxyStoreSimOperator,
            onCheckedChange = { onEvent(GalaxyStoreEvent.SetSimOperator(it)) },
            onConfirm = { onEvent(GalaxyStoreEvent.SimOperator(it)) },
        )
    }
}

sealed interface GalaxyStoreEvent {
    @JvmInline
    value class BlockGalaxyStoreAds(val value: Boolean) : GalaxyStoreEvent

    @JvmInline
    value class HideDiscover(val value: Boolean) : GalaxyStoreEvent

    @JvmInline
    value class HideBenefits(val value: Boolean) : GalaxyStoreEvent

    @JvmInline
    value class SetSimOperator(val value: Boolean) : GalaxyStoreEvent

    @JvmInline
    value class SimOperator(val value: String) : GalaxyStoreEvent
}

fun SettingViewModel.onGalaxyStoreEvent(event: GalaxyStoreEvent) {
    updateData { preference ->
        preference.copy(other = when (event) {
            is GalaxyStoreEvent.SetSimOperator -> preference.other.copy(setGalaxyStoreSimOperator = event.value)
            is GalaxyStoreEvent.SimOperator -> preference.other.copy(galaxyStoreSimOperator = event.value)
            is GalaxyStoreEvent.BlockGalaxyStoreAds -> preference.other.copy(blockGalaxyStoreAds = event.value)
            is GalaxyStoreEvent.HideDiscover -> preference.other.copy(hideGalaxyStoreDiscover = event.value)
            is GalaxyStoreEvent.HideBenefits -> preference.other.copy(hideGalaxyStoreBenefits = event.value)
        })
    }
}
