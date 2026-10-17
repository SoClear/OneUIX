package io.github.soclear.oneuix.ui

import android.app.Application
import android.content.pm.PackageManager
import android.util.Log
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.datastore.core.DataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.soclear.oneuix.DeviceGates
import io.github.soclear.oneuix.R
import io.github.soclear.oneuix.common.FeatureOverride
import io.github.soclear.oneuix.common.IgnoreUnknownKeysJson
import io.github.soclear.oneuix.common.Preference
import io.github.soclear.oneuix.ui.category.Category
import io.github.soclear.oneuix.ui.category.CategoryAppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
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
            val label = when (category) {
                // Not an app to hook but a pane of the module's own, so it is named here rather than
                // taken from the package it is listed under.
                Category.FeatureOverrides -> application.getString(R.string.featureOverrides_category)
                else -> applicationInfo?.loadLabel(packageManager)?.toString()
                    ?: category.packageName
            }
            val icon = applicationInfo?.loadIcon(packageManager)?.toBitmap()?.asImageBitmap()
                ?: fallbackIcon
            (applicationInfo != null) to CategoryAppInfo(category, label, icon)
        }.partition { it.first }
            .let { (installed, missing) -> (installed + missing).map { it.second } }
        emit(categoryAppInfoList)
    }.flowOn(Dispatchers.IO).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val dataStore: DataStore<Preference> = application.dataStore

    /**
     * The gates this device carries, read from its own files.
     *
     * They are for showing only, and are deliberately never written into the preference file: a
     * failed or early read of that file would then be written back over it, taking the switches that
     * had been set with it. Nothing has to be answered for a gate the ROM already answers with the
     * same value, so the file only ever holds what the app was told to change.
     */
    private val deviceGates = MutableStateFlow<List<FeatureOverride>>(emptyList())

    init {
        viewModelScope.launch {
            val read = withContext(Dispatchers.IO) {
                runCatching { DeviceGates.read() }
                    .onFailure { Log.w(TAG, "could not read the gates of this device", it) }
                    .getOrDefault(emptyList())
            }
            deviceGates.value = read
            Log.i(TAG, "device gates: ${read.size} read from this device")
        }
    }

    /**
     * What the ROM itself answers for each gate, which is the value its own files hold. A gate that
     * is not in those files is not answered by the ROM at all, which is read as empty, or as zero
     * for a floating feature.
     */
    val deviceValues: StateFlow<Map<String, String>> = deviceGates
        .map { gates -> gates.associate { it.key to it.value } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /**
     * Everything the app lists: the gates the device carries, with whatever has been set for them
     * on top, then the gates that are not on this device at all, such as the catalogue's and the
     * ones added by hand.
     */
    val gates: StateFlow<List<FeatureOverride>> =
        combine(dataStore.data, deviceGates) { preference, device ->
            val set = preference.featureOverrides.associateBy { it.key }
            val onDevice = device.mapTo(mutableSetOf()) { it.key }
            device.map { set[it.key] ?: it } +
                preference.featureOverrides.filterNot { it.key in onDevice }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val preference = dataStore.data
        .map { it.copy(featureOverrides = FeatureOverride.normalize(it.featureOverrides)) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = Preference()
        )

    fun updateData(nextPreference: (currentPreference: Preference) -> Preference) {
        viewModelScope.launch {
            dataStore.updateData {
                nextPreference(it)
            }
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    suspend fun backupTo(output: OutputStream) = withContext(Dispatchers.IO) {
        IgnoreUnknownKeysJson.encodeToStream(
            Preference.serializer(),
            dataStore.data.first(),
            output
        )
    }

    @OptIn(ExperimentalSerializationApi::class)
    suspend fun restoreFrom(input: InputStream) = withContext(Dispatchers.IO) {
        val restored = IgnoreUnknownKeysJson.decodeFromStream<Preference>(input)
        dataStore.updateData { restored }
    }

    private companion object {
        const val TAG = "OneUIX"
    }
}
