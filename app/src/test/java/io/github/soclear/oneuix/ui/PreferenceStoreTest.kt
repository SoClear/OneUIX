package io.github.soclear.oneuix.ui

import io.github.soclear.oneuix.common.Preference
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class PreferenceStoreTest {
    private class Storage(var saved: Preference = Preference()) : PreferenceStorage {
        var reads = 0
        var writes = 0
        var failRead = false
        var failWrite = false
        var readGate: CompletableDeferred<Unit>? = null
        var writeGate: CompletableDeferred<Unit>? = null

        override suspend fun read(): Preference {
            reads++
            readGate?.await()
            if (failRead) throw IOException("Read failed")
            return saved
        }

        override suspend fun write(preference: Preference) {
            writes++
            writeGate?.await()
            if (failWrite) throw IOException("Write failed")
            saved = preference
        }
    }

    private fun changed() = Preference(android = Preference.Android(allowAllRotation = true))

    private fun scenario(block: suspend (MutableStateFlow<PreferenceStorage?>, PreferenceStore) -> Unit) = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val services = MutableStateFlow<PreferenceStorage?>(null)
        val store = PreferenceStore(services, scope, connectionTimeoutMillis = 20)
        try { block(services, store) } finally { scope.cancel() }
    }

    @Test
    fun delayedConnectionLoadsSavedSettingsInsteadOfDefaults() = scenario { services, store ->
        assertEquals(PreferenceStatus.Connecting, store.state.value.status)
        assertFalse(store.update { changed() })
        withTimeout(2_000) { store.state.first { it.status == PreferenceStatus.Unavailable } }
        val storage = Storage(changed())
        services.value = storage
        withTimeout(2_000) { store.state.first { it.status == PreferenceStatus.Ready } }
        assertEquals(changed(), store.state.value.preference)
        assertEquals(0, storage.writes)
    }

    @Test
    fun loadingAndDisconnectedStatesRejectChangesAndReconnectReloads() = scenario { services, store ->
        val gate = CompletableDeferred<Unit>()
        val storage = Storage().apply { readGate = gate }
        services.value = storage
        withTimeout(2_000) { store.state.first { it.status == PreferenceStatus.Loading } }
        assertFalse(store.update { changed() })
        gate.complete(Unit)
        withTimeout(2_000) { store.state.first { it.status == PreferenceStatus.Ready } }
        services.value = null
        assertFalse(store.update { changed() })
        assertNull(store.snapshot())
        withTimeout(2_000) { store.state.first { it.status == PreferenceStatus.Unavailable } }
        storage.saved = changed()
        services.value = storage
        withTimeout(2_000) { store.state.first { it.status == PreferenceStatus.Ready } }
        assertEquals(changed(), store.snapshot())
        assertEquals(2, storage.reads)
        assertEquals(0, storage.writes)
    }

    @Test
    fun failedWriteDoesNotReportUnsavedValuesAsSavedAndCanRetry() = scenario { services, store ->
        val storage = Storage().apply { failWrite = true }
        services.value = storage
        withTimeout(2_000) { store.state.first { it.status == PreferenceStatus.Ready } }
        assertFalse(store.update { changed() })
        assertEquals(PreferenceStatus.Error, store.state.value.status)
        assertEquals(Preference(), store.state.value.preference)
        storage.failWrite = false
        store.retry()
        withTimeout(2_000) { store.state.first { it.status == PreferenceStatus.Ready } }
        assertTrue(store.update { changed() })
        assertEquals(changed(), storage.saved)
        assertEquals(changed(), store.snapshot())
    }

    @Test
    fun failedReadDoesNotEnableEditingWithDefaults() = scenario { services, store ->
        val storage = Storage().apply { failRead = true }
        services.value = storage
        withTimeout(2_000) { store.state.first { it.status == PreferenceStatus.Error } }
        assertFalse(store.update { changed() })
        assertEquals(0, storage.writes)
        assertTrue(store.restore(changed()))
        withTimeout(2_000) { store.state.first { it.status == PreferenceStatus.Ready } }
        assertEquals(changed(), store.snapshot())
    }

    @Test
    fun restoreFromErrorRequiresLiveConnectionAndSuccessfulWrite() = scenario { services, store ->
        val storage = Storage().apply { failRead = true }
        services.value = storage
        withTimeout(2_000) { store.state.first { it.status == PreferenceStatus.Error } }
        services.value = null
        assertFalse(store.restore(changed()))
        withTimeout(2_000) { store.state.first { it.status == PreferenceStatus.Unavailable } }
        services.value = storage
        withTimeout(2_000) { store.state.first { it.status == PreferenceStatus.Error } }
        storage.failRead = false
        storage.failWrite = true
        assertFalse(store.restore(changed()))
        assertEquals(PreferenceStatus.Error, store.state.value.status)
        storage.failWrite = false
        assertTrue(store.restore(changed()))
        assertEquals(changed(), store.snapshot())
    }

    @Test
    fun replacementConnectionCannotPublishOldRead() = scenario { services, store ->
        val old = Storage().apply { readGate = CompletableDeferred() }
        services.value = old
        withTimeout(2_000) { store.state.first { it.status == PreferenceStatus.Loading } }
        services.value = Storage(changed())
        withTimeout(2_000) { store.state.first { it.status == PreferenceStatus.Ready } }
        old.readGate!!.complete(Unit)
        assertEquals(changed(), store.snapshot())
    }

    @Test
    fun writeFromOldConnectionCannotReplaceReconnectedSettings() = scenario { services, store ->
        coroutineScope {
            val old = Storage().apply { writeGate = CompletableDeferred() }
            services.value = old
            withTimeout(2_000) { store.state.first { it.status == PreferenceStatus.Ready } }
            val write = async(start = CoroutineStart.UNDISPATCHED) { store.update { changed() } }
            val replacement = Storage()
            services.value = replacement
            withTimeout(2_000) { store.state.first { it.status == PreferenceStatus.Loading } }
            old.writeGate!!.complete(Unit)
            assertFalse(write.await())
            withTimeout(2_000) { store.state.first { it.status == PreferenceStatus.Ready } }
            assertEquals(replacement.saved, store.snapshot())
            assertEquals(0, replacement.writes)
        }
    }

    @Test
    fun concurrentEditsPreserveBothChanges() = scenario { services, store ->
        coroutineScope {
            val storage = Storage().apply { writeGate = CompletableDeferred() }
            services.value = storage
            withTimeout(2_000) { store.state.first { it.status == PreferenceStatus.Ready } }
            val first = async(start = CoroutineStart.UNDISPATCHED) { store.update { changed() } }
            val second = async(start = CoroutineStart.UNDISPATCHED) {
                store.update { it.copy(android = it.android.copy(maxNeverKilledAppNum = 10)) }
            }
            storage.writeGate!!.complete(Unit)
            assertTrue(first.await())
            assertTrue(second.await())
            assertTrue(storage.saved.android.allowAllRotation)
            assertEquals(10, storage.saved.android.maxNeverKilledAppNum)
        }
    }
}
