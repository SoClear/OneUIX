package io.github.soclear.oneuix.ui

import android.content.Context
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import io.github.soclear.oneuix.XposedServiceManager
import io.github.soclear.oneuix.common.IgnoreUnknownKeysJson
import io.github.soclear.oneuix.common.Preference
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import java.io.InputStream
import java.io.OutputStream

object PreferenceSerializer : Serializer<Preference> {
    private const val TAG = "PreferenceSerializer"

    @OptIn(ExperimentalSerializationApi::class)
    override suspend fun readFrom(input: InputStream): Preference = try {
        val service = XposedServiceManager.xposedService ?: return defaultValue
        val parcelFileDescriptor = service.openRemoteFile(Preference.FILE_NAME)

        ParcelFileDescriptor.AutoCloseInputStream(parcelFileDescriptor).use { inputStream ->
            if (inputStream.channel.size() == 0L) return defaultValue
            IgnoreUnknownKeysJson.decodeFromStream<Preference>(inputStream)
        }
    } catch (e: Exception) {
        Log.e(TAG, "readFrom", e)
        defaultValue
    }


    @OptIn(ExperimentalSerializationApi::class)
    override suspend fun writeTo(t: Preference, output: OutputStream) {
        try {
            val service = XposedServiceManager.xposedService ?: return
            val parcelFileDescriptor = service.openRemoteFile(Preference.FILE_NAME)

            ParcelFileDescriptor.AutoCloseOutputStream(parcelFileDescriptor).use { outputStream ->
                outputStream.channel.truncate(0)
                IgnoreUnknownKeysJson.encodeToStream(Preference.serializer(), t, outputStream)
                outputStream.channel.force(true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "writeTo", e)
        }
    }

    override val defaultValue: Preference = Preference()
}

val Context.dataStore by dataStore("whatever", PreferenceSerializer)
