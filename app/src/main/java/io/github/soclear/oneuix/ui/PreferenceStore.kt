package io.github.soclear.oneuix.ui

import io.github.soclear.oneuix.common.Preference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface PreferenceStorage {
    suspend fun read(): Preference
    suspend fun write(preference: Preference)
}

enum class PreferenceStatus { Connecting, Unavailable, Loading, Ready, Error }

data class PreferenceState(val status: PreferenceStatus = PreferenceStatus.Connecting, val preference: Preference = Preference())

class PreferenceStore(
    private val services: StateFlow<PreferenceStorage?>,
    scope: CoroutineScope,
    private val connectionTimeoutMillis: Long = 3_000,
) {
    private val mutableState = MutableStateFlow(PreferenceState())
    val state = mutableState.asStateFlow()
    private val retries = MutableStateFlow(0)
    private val mutex = Mutex()
    private var loadedStorage: PreferenceStorage? = null

    init {
        scope.launch {
            var hasConnected = false
            combine(services, retries) { storage, _ -> storage }.collectLatest { storage ->
                if (storage == null) {
                    setStatus(if (hasConnected) PreferenceStatus.Unavailable else PreferenceStatus.Connecting)
                    if (!hasConnected) {
                        delay(connectionTimeoutMillis)
                        setStatus(PreferenceStatus.Unavailable)
                    }
                } else {
                    hasConnected = true
                    setStatus(PreferenceStatus.Loading)
                    mutex.withLock {
                        try {
                            val preference = storage.read()
                            if (services.value === storage) {
                                loadedStorage = storage
                                mutableState.value = PreferenceState(PreferenceStatus.Ready, preference)
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            if (services.value === storage) setStatus(PreferenceStatus.Error)
                        }
                    }
                }
            }
        }
    }

    fun retry() { retries.update { it + 1 } }

    suspend fun snapshot(): Preference? = mutex.withLock {
        if (isReady(services.value)) state.value.preference else null
    }

    suspend fun update(transform: (Preference) -> Preference): Boolean {
        val storage = services.value ?: return false
        if (!isReady(storage)) return false
        return mutex.withLock {
            if (!isReady(storage)) return@withLock false
            write(storage, transform(state.value.preference))
        }
    }

    /** A valid backup may replace a corrupt remote file while its service is connected. */
    suspend fun restore(preference: Preference): Boolean {
        val storage = services.value ?: return false
        if (!canRestore(storage)) return false
        return mutex.withLock {
            if (!canRestore(storage)) return@withLock false
            write(storage, preference)
        }
    }

    private suspend fun write(storage: PreferenceStorage, preference: Preference): Boolean {
        try {
            storage.write(preference)
            if (services.value !== storage || state.value.status == PreferenceStatus.Loading) return false
            loadedStorage = storage
            mutableState.value = PreferenceState(PreferenceStatus.Ready, preference)
            return true
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            if (services.value === storage) setStatus(PreferenceStatus.Error)
            return false
        }
    }

    private fun isReady(storage: PreferenceStorage?) = storage != null &&
        services.value === storage && loadedStorage === storage && state.value.status == PreferenceStatus.Ready

    private fun canRestore(storage: PreferenceStorage) = services.value === storage &&
        state.value.status in setOf(PreferenceStatus.Ready, PreferenceStatus.Error)

    private fun setStatus(status: PreferenceStatus) { mutableState.update { it.copy(status = status) } }
}
