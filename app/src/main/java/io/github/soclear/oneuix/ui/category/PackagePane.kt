package io.github.soclear.oneuix.ui.category

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.soclear.oneuix.R

@Composable
internal fun PackagePane(
    modifier: Modifier = Modifier,
    category: Category? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        category?.let { ForceStopButton(it) }
        content()
    }
}

@Composable
internal fun ColumnScope.ForceStopButton(category: Category) {
    Spacer(modifier = Modifier.height(30.dp))
    Button(
        modifier = Modifier.align(Alignment.CenterHorizontally),
        onClick = {
            runCatching {
                ProcessBuilder(
                    "su", "-c",
                    category.packages.joinToString("; ") { "am force-stop --user current $it" }
                ).start()
            }
        }
    ) {
        Text(text = stringResource(R.string.forceStopApp))
    }
    Spacer(modifier = Modifier.height(16.dp))
}
