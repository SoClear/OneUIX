package io.github.soclear.oneuix.ui.component

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

@Composable
fun ColumnScope.ForceStopApp(packageName: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    // 缓存解析的应用名，解析失败或未安装时回退为 packageName
    val appName = remember(packageName) {
        runCatching {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        }.getOrDefault(packageName)
    }

    Spacer(modifier = Modifier.height(30.dp))
    Button(
        modifier = modifier.align(Alignment.CenterHorizontally),
        onClick = {
            runCatching { ProcessBuilder("su", "-c", "killall", packageName).start() }
        }
    ) {
        Text(text = stringResource(R.string.force_stop_app, appName))
    }
}