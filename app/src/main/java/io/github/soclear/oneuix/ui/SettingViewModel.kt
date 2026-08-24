package io.github.soclear.oneuix.ui

import android.app.Application
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.datastore.core.DataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.github.soclear.oneuix.data.IgnoreUnknownKeysJson
import io.github.soclear.oneuix.data.Preference
import io.github.soclear.oneuix.R
import io.github.soclear.oneuix.ui.category.Category
import io.github.soclear.oneuix.ui.category.CategoryAppInfo
import io.github.soclear.oneuix.ui.category.UserLaunchableApp
import java.io.InputStream
import java.io.OutputStream
import java.text.Collator

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
            val label = if (category == Category.Interaction) {
                application.getString(R.string.interaction_category)
            } else {
                applicationInfo?.loadLabel(packageManager)?.toString() ?: category.packageName
            }
            val icon = applicationInfo?.loadIcon(packageManager)?.toBitmap()?.asImageBitmap()
                ?: fallbackIcon
            (applicationInfo != null) to CategoryAppInfo(category, label, icon)
        }.partition { it.first }
            .let { (installed, missing) -> (installed + missing).map { it.second } }
        emit(categoryAppInfoList)
    }.flowOn(Dispatchers.IO).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val userLaunchableApps: StateFlow<List<UserLaunchableApp>> = flow {
        val packageManager = application.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val apps = packageManager.queryIntentActivities(launcherIntent, 0)
            .asSequence()
            .mapNotNull { resolveInfo ->
                val applicationInfo = resolveInfo.activityInfo?.applicationInfo ?: return@mapNotNull null
                val isSystemApp = applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0 ||
                    applicationInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
                if (
                    isSystemApp ||
                    !applicationInfo.enabled ||
                    applicationInfo.packageName == application.packageName
                ) {
                    return@mapNotNull null
                }
                val packageName = applicationInfo.packageName
                UserLaunchableApp(
                    packageName = packageName,
                    label = applicationInfo.loadLabel(packageManager).toString(),
                    icon = applicationInfo.loadIcon(packageManager)
                        .toBitmap(width = 96, height = 96)
                        .asImageBitmap(),
                )
            }
            .distinctBy { it.packageName }
            .sortedWith { left, right ->
                Collator.getInstance().compare(left.label, right.label)
            }
            .toList()
        emit(apps)
    }.flowOn(Dispatchers.IO).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val dataStore: DataStore<Preference> = application.dataStore

    val preference = dataStore.data.stateIn(
        scope = viewModelScope, started = SharingStarted.Eagerly, initialValue = Preference()
    )

    fun updateData(nextPreference: (currentPreference: Preference) -> Preference) {
        viewModelScope.launch {
            dataStore.updateData {
                nextPreference(it)
            }
        }
    }

    fun setCategoryVisible(category: Category, visible: Boolean) {
        updateData { current ->
            val hidden = current.hiddenMainMenuCategories.toMutableSet()
            if (visible) hidden.remove(category.name) else hidden.add(category.name)
            current.copy(hiddenMainMenuCategories = hidden)
        }
    }

    fun showAllCategories() {
        updateData { current -> current.copy(hiddenMainMenuCategories = emptySet()) }
    }

    suspend fun backupTo(output: OutputStream) = withContext(Dispatchers.IO) {
        output.write(
            IgnoreUnknownKeysJson.encodeToString(
                Preference.serializer(), dataStore.data.first()
            ).encodeToByteArray()
        )
    }

    suspend fun restoreFrom(input: InputStream) = withContext(Dispatchers.IO) {
        val restored = IgnoreUnknownKeysJson.decodeFromString(
            Preference.serializer(), input.readBytes().decodeToString()
        )
        dataStore.updateData { restored }
    }
}
