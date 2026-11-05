package io.github.soclear.oneuix

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings

class TilePreferencesActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val className = intent.getParcelableExtra(Intent.EXTRA_COMPONENT_NAME, ComponentName::class.java)?.className
            ?: intent.getStringExtra(Intent.EXTRA_COMPONENT_NAME)?.let(ComponentName::unflattenFromString)?.className

        when (className) {
            FiveGTileService::class.java.name -> listOf(
                Intent("com.samsung.android.app.telephonyui.action.OPEN_SIMCARD_ACTIVITY"),
                Intent().setClassName("com.samsung.android.app.telephonyui", "com.samsung.android.app.telephonyui.netsettings.ui.simcardmanager.SimCardMgrActivity"),
                Intent("settings.SIM_CARD_NETWORK"),
                Intent(Settings.ACTION_NETWORK_OPERATOR_SETTINGS)
            ).any { runCatching { startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess }

            OutdoorModeTileService::class.java.name,
            AutoBrightnessTileService::class.java.name -> runCatching {
                startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
        finish()
    }
}
