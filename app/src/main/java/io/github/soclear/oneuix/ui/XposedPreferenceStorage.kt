package io.github.soclear.oneuix.ui

import android.os.ParcelFileDescriptor
import io.github.libxposed.service.XposedService
import io.github.soclear.oneuix.common.IgnoreUnknownKeysJson
import io.github.soclear.oneuix.common.Preference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream

@OptIn(ExperimentalSerializationApi::class)
class XposedPreferenceStorage(val service: XposedService) : PreferenceStorage {
    override suspend fun read(): Preference = withContext(Dispatchers.IO) {
        ParcelFileDescriptor.AutoCloseInputStream(service.openRemoteFile(Preference.FILE_NAME)).use { input ->
            if (input.channel.size() == 0L) Preference() else IgnoreUnknownKeysJson.decodeFromStream<Preference>(input)
        }
    }

    override suspend fun write(preference: Preference) = withContext(Dispatchers.IO) {
        ParcelFileDescriptor.AutoCloseOutputStream(service.openRemoteFile(Preference.FILE_NAME)).use { output ->
            output.channel.truncate(0)
            IgnoreUnknownKeysJson.encodeToStream(Preference.serializer(), preference, output)
            output.channel.force(true)
        }
    }
}
