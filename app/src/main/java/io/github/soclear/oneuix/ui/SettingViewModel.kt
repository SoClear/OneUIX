package io.github.soclear.oneuix.ui

import android.app.Application
import android.content.pm.PackageManager
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.github.soclear.oneuix.common.IgnoreUnknownKeysJson
import io.github.soclear.oneuix.common.Preference
import io.github.soclear.oneuix.XposedServiceManager
import io.github.soclear.oneuix.ui.category.Category
import io.github.soclear.oneuix.ui.category.CategoryAppInfo
import java.io.InputStream
import java.io.OutputStream

class SettingViewModel(application: Application) : ViewModel() {
    val categoryAppInfoList: StateFlow<List<CategoryAppInfo>> = flow {
        val packageManager = application.packageManager
        val fallbackIcon = application.applicationInfo
            .loadIcon(packageManager)
            .toBitmap()
            .asImageBitmap()
        val categoryAppInfoList = Category.entries.map { category ->
            val applicationInfo = try {
                packageManager.getApplicationInfo(category.packageName, 0)
            } catch (_: PackageManager.NameNotFoundException) {
                null
            }
            val label = applicationInfo?.loadLabel(packageManager)?.toString()
                ?: category.packageName
            val icon = applicationInfo?.loadIcon(packageManager)?.toBitmap()?.asImageBitmap()
                ?: fallbackIcon
            (applicationInfo != null) to CategoryAppInfo(category, label, icon)
        }.partition { it.first }
            .let { (installed, missing) -> (installed + missing).map { it.second } }
        emit(categoryAppInfoList)
    }.flowOn(Dispatchers.IO).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val preferenceStore = PreferenceStore(XposedServiceManager.preferences, viewModelScope)
    val preferenceState = preferenceStore.state

    fun retryConnection() = preferenceStore.retry()

    fun updateData(nextPreference: (currentPreference: Preference) -> Preference) {
        viewModelScope.launch {
            preferenceStore.update(nextPreference)
        }
    }

    suspend fun backupTo(output: OutputStream): Boolean = withContext(Dispatchers.IO) {
        val preference = preferenceStore.snapshot() ?: return@withContext false
        output.write(
            IgnoreUnknownKeysJson.encodeToString(
                Preference.serializer(), preference
            ).encodeToByteArray()
        )
        true
    }

    suspend fun restoreFrom(input: InputStream): Boolean = withContext(Dispatchers.IO) {
        val restored = IgnoreUnknownKeysJson.decodeFromString(
            Preference.serializer(), input.readBytes().decodeToString()
        )
        preferenceStore.restore(restored)
    }
}
