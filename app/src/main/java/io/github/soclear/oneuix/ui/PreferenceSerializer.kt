package io.github.soclear.oneuix.ui

import android.content.Context
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import io.github.soclear.oneuix.data.IgnoreUnknownKeysJson
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import io.github.soclear.oneuix.data.Preference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream

object PreferenceSerializer : Serializer<Preference> {
    override suspend fun readFrom(input: InputStream): Preference {
        return try {
            IgnoreUnknownKeysJson.decodeFromString(
                deserializer = Preference.serializer(),
                string = input.readBytes().decodeToString()
            )
        } catch (e: SerializationException) {
            e.printStackTrace()
            defaultValue
        }
    }

    override suspend fun writeTo(t: Preference, output: OutputStream) = withContext(Dispatchers.IO) {
        output.write(
            Json.encodeToString(
                serializer = Preference.serializer(),
                value = t
            ).encodeToByteArray()
        )
    }

    override val defaultValue: Preference  = Preference()
}

val Context.dataStore by dataStore("whatever", PreferenceSerializer)
