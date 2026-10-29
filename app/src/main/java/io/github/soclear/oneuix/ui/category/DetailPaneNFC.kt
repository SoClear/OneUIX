package io.github.soclear.oneuix.ui.category

import androidx.compose.foundation.clickable
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.soclear.oneuix.R
import io.github.soclear.oneuix.ui.SettingViewModel

@Composable
fun DetailPaneNFC(
    onNavigateToNfcCards: () -> Unit,
    modifier: Modifier = Modifier
) {
    PackagePane(modifier) {
        ListItem(
            headlineContent = { Text(stringResource(R.string.nfc_filter_title)) },
            supportingContent = { Text(stringResource(R.string.nfc_filter_summary)) },
            modifier = Modifier.clickable(onClick = onNavigateToNfcCards)
        )
    }
}

sealed interface NFCEvent {
    @JvmInline
    value class FilterMode(val value: Int) : NFCEvent

    @JvmInline
    value class Cards(val value: Map<String, String>) : NFCEvent
}

fun SettingViewModel.onNFCEvent(event: NFCEvent) {
    updateData { preference ->
        preference.copy(other = when (event) {
            is NFCEvent.FilterMode -> preference.other.copy(nfcFilterMode = event.value)
            is NFCEvent.Cards -> preference.other.copy(nfcCards = event.value)
        })
    }
}
