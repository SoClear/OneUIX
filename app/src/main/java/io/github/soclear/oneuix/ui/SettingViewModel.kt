package io.github.soclear.oneuix.ui

import android.app.Application
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
import io.github.soclear.oneuix.common.IgnoreUnknownKeysJson
import io.github.soclear.oneuix.common.Preference
import io.github.soclear.oneuix.ui.category.Category
import io.github.soclear.oneuix.ui.category.CategoryAppInfo
import io.github.soclear.oneuix.ui.charging.ChargingControlRepository
import io.github.soclear.oneuix.ui.charging.ChargingControlState
import java.io.InputStream
import java.io.OutputStream

class SettingViewModel(application: Application) : ViewModel() {
    private val chargingRepository = ChargingControlRepository(application)
    private val _chargingState = kotlinx.coroutines.flow.MutableStateFlow(ChargingUiState())
    val chargingState: StateFlow<ChargingUiState> = _chargingState

    fun refreshChargingControl() {
        viewModelScope.launch {
            _chargingState.value = _chargingState.value.copy(
                system = withContext(Dispatchers.IO) { chargingRepository.read() }
            )
        }
    }

    fun setRememberChargingLimit(enabled: Boolean) {
        if (_chargingState.value.busy) return
        _chargingState.value = _chargingState.value.copy(busy = true, error = null)
        viewModelScope.launch {
            val applied = withContext(Dispatchers.IO) { chargingRepository.setRemember(enabled) }
            val system = withContext(Dispatchers.IO) { chargingRepository.read() }
            _chargingState.value = ChargingUiState(
                system = system, error = if (applied) null else ChargingError.Remember
            )
        }
    }

    fun setChargingLimit(limit: Int) {
        if (_chargingState.value.busy) return
        _chargingState.value = _chargingState.value.copy(busy = true, error = null)
        viewModelScope.launch {
            val applied = withContext(Dispatchers.IO) { chargingRepository.setLimit(limit) }
            val system = withContext(Dispatchers.IO) { chargingRepository.read() }
            _chargingState.value = ChargingUiState(
                system = system, error = if (applied) null else ChargingError.Limit
            )
        }
    }

    fun pauseChargingAtCurrentLevel() {
        if (_chargingState.value.busy) return
        _chargingState.value = _chargingState.value.copy(busy = true, error = null)
        viewModelScope.launch {
            val applied = withContext(Dispatchers.IO) { chargingRepository.toggleQuickPause() }
            val system = withContext(Dispatchers.IO) { chargingRepository.read() }
            _chargingState.value = ChargingUiState(
                system = system, error = if (applied) null else ChargingError.Bypass
            )
        }
    }

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

data class ChargingUiState(
    val system: ChargingControlState? = null,
    val busy: Boolean = false,
    val error: ChargingError? = null,
)

enum class ChargingError { Limit, Bypass, Remember }
