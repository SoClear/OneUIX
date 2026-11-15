package io.github.soclear.oneuix.ui.component

import android.widget.Toast
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.soclear.oneuix.R
import io.github.soclear.oneuix.common.Package

@Composable
fun ColumnScope.ForceStopApp(
    vararg packageNames: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val appNames = remember(*packageNames) {
        val pm = context.packageManager
        packageNames.joinToString(", ") { packageName ->
            runCatching {
                val appInfo = pm.getApplicationInfo(packageName, 0)
                pm.getApplicationLabel(appInfo).toString()
            }.getOrDefault(packageName)
        }
    }
    val successMessage = stringResource(R.string.force_stop_success, appNames)

    Spacer(modifier = Modifier.height(30.dp))
    Button(
        modifier = modifier.align(Alignment.CenterHorizontally),
        onClick = {
            runCatching {
                val command = packageNames.joinToString("; ") { packageName ->
                    if (packageName == Package.SYSTEMUI) {
                        "killall $packageName"
                    } else {
                        "am force-stop --user current $packageName"
                    }
                }
                ProcessBuilder("su", "-c", command).start()
                Toast.makeText(context, successMessage, Toast.LENGTH_SHORT).show()
            }
        }
    ) {
        Text(text = stringResource(R.string.force_stop_app, appNames))
    }
}