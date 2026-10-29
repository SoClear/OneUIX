package io.github.soclear.oneuix.ui.category

import android.content.Intent
import android.nfc.NfcAdapter
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.soclear.oneuix.R
import io.github.soclear.oneuix.common.Preference
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtraPaneNfcCards(
    uiState: Preference.Other,
    onEvent: (NFCEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val adapter = remember(context) { NfcAdapter.getDefaultAdapter(context) }
    val scope = rememberCoroutineScope()
    val focused = LocalWindowInfo.current.isWindowFocused
    val cards by rememberUpdatedState(uiState.nfcCards)
    var nfcEnabled by remember { mutableStateOf(adapter?.isEnabled == true) }
    var uid by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    val validUid = uid.isNotEmpty() && uid.length % 2 == 0 && uid.all { it in "0123456789ABCDEF" }

    LifecycleResumeEffect(adapter, activity, focused) {
        nfcEnabled = adapter?.isEnabled == true
        if (nfcEnabled && focused && activity != null) {
            adapter?.enableReaderMode(activity, { tag ->
                val scanned = tag.id?.toHexString(HexFormat.UpperCase) ?: return@enableReaderMode
                scope.launch {
                    uid = scanned
                    note = cards[scanned].orEmpty()
                }
            }, NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V or
                NfcAdapter.FLAG_READER_NFC_BARCODE or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK or
                NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS, null)
        }
        onPauseOrDispose {
            if (activity != null) adapter?.disableReaderMode(activity)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nfc_filter_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(ImageVector.vectorResource(R.drawable.arrow_back), contentDescription = null)
                    }
                }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        PackagePane(Modifier.padding(innerPadding)) {
            Text(stringResource(R.string.nfc_filter_summary), Modifier.padding(16.dp))
            listOf(R.string.nfc_mode_off, R.string.nfc_mode_blacklist, R.string.nfc_mode_whitelist)
                .forEachIndexed { mode, label ->
                    ListItem(
                        headlineContent = { Text(stringResource(label)) },
                        leadingContent = { RadioButton(selected = uiState.nfcFilterMode == mode, onClick = null) },
                        modifier = Modifier.clickable { onEvent(NFCEvent.FilterMode(mode)) }
                    )
                }
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(when {
                    adapter == null -> R.string.nfc_unavailable
                    !nfcEnabled -> R.string.nfc_disabled
                    else -> R.string.nfc_scan_hint
                }))
                if (adapter != null && !nfcEnabled) {
                    TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) }) {
                        Text(stringResource(R.string.nfc_settings))
                    }
                }
                OutlinedTextField(
                    value = uid,
                    onValueChange = {
                        uid = it.filterNot { c -> c.isWhitespace() || c == ':' || c == '-' }.uppercase()
                    },
                    label = { Text("UID") },
                    singleLine = true,
                    isError = uid.isNotEmpty() && !validUid,
                    supportingText = if (uid.isNotEmpty() && !validUid) {
                        { Text(stringResource(R.string.nfc_uid_invalid)) }
                    } else null,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.nfc_note)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        onEvent(NFCEvent.Cards(cards + (uid to note)))
                        uid = ""
                        note = ""
                    },
                    enabled = validUid
                ) { Text(stringResource(R.string.nfc_save)) }
            }
            uiState.nfcCards.forEach { (cardUid, cardNote) ->
                ListItem(
                    headlineContent = { Text(cardUid) },
                    supportingContent = { Text(cardNote) },
                    trailingContent = {
                        TextButton(onClick = { onEvent(NFCEvent.Cards(cards - cardUid)) }) {
                            Text(stringResource(R.string.nfc_delete))
                        }
                    },
                    modifier = Modifier.clickable {
                        uid = cardUid
                        note = cardNote
                    }
                )
            }
        }
    }
}
