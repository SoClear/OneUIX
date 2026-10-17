package io.github.soclear.oneuix

import android.util.Log
import io.github.soclear.oneuix.common.FeatureOverride
import java.io.File
import java.util.zip.GZIPInputStream

/**
 * Reads the feature gates this device carries, out of the files One UI keeps them in.
 *
 * The floating features are plain XML, in `/system/etc/floating_feature.xml` and
 * `/vendor/etc/floating_feature.xml`. The CSC features are obfuscated rather than encrypted: every
 * byte is rotated left by a value from one fixed table and XORed with a value from another, and the
 * result is gzipped. Neither table is a secret, and both are carried here, so the files can be read
 * on the device without root.
 *
 * Every carrier file the device ships is read, because which carrier is in use is not something an
 * app can ask for without a hidden API; a key takes the value most carriers share. That also means
 * a gate can be given another carrier's value.
 */
object DeviceGates {
    private const val TAG = "OneUIX"

    private const val CARRIER_DIR = "/optics/configs/carriers"
    private const val CARRIER_FILE = "conf/system/cscfeature.xml"
    private const val CSC_PREFIX = "CscFeature_"
    private const val FLOATING_PREFIX = "SEC_FLOATING_FEATURE_"

    private val FLOATING_FILES = listOf(
        "/system/etc/floating_feature.xml",
        "/vendor/etc/floating_feature.xml",
    )

    /** The gates on this device, each off until it is turned on. */
    fun read(): List<FeatureOverride> {
        val perCarrier = carrierFiles().flatMap { file ->
            runCatching { readCsc(file) }
                .onFailure { Log.w(TAG, "could not decode $file", it) }
                .getOrNull()
                .orEmpty()
        }

        val cscGates = perCarrier
            .groupBy({ it.key }, { it.value })
            .map { (key, values) -> gate(key, values.mostCommon()) }

        val floatingGates = FLOATING_FILES.flatMap { path ->
            runCatching { readFloating(File(path)) }
                .onFailure { Log.w(TAG, "could not read $path", it) }
                .getOrNull()
                .orEmpty()
        }

        return (cscGates + floatingGates).distinctBy { it.key }
    }

    /** The value most carriers share, with the lowest of equally common values breaking the tie. */
    private fun List<String>.mostCommon(): String =
        groupingBy { it }.eachCount().entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .first().key

    private fun carrierFiles(): List<File> {
        val dir = File(CARRIER_DIR)
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles().orEmpty().map { File(it, CARRIER_FILE) }.filter { it.isFile }
    }

    private fun readFloating(file: File): List<FeatureOverride> =
        Regex("<($FLOATING_PREFIX[A-Z0-9_]+)>([^<]*)</")
            .findAll(file.readText())
            .map { gate(it.groupValues[1], it.groupValues[2]) }
            .toList()

    private fun readCsc(file: File): List<FeatureOverride> {
        val decoded = decode(file.readBytes())
        val text = runCatching {
            GZIPInputStream(decoded.inputStream()).use { it.readBytes() }.decodeToString()
        }.getOrElse {
            // Not every file is gzipped; the ones that are not are read as they are.
            decoded.decodeToString()
        }
        return Regex("<($CSC_PREFIX[A-Za-z0-9_]+)>([^<]*)</")
            .findAll(text)
            .map { gate(it.groupValues[1], it.groupValues[2]) }
            .toList()
    }

    private fun gate(key: String, value: String) = FeatureOverride(
        key = key,
        value = value,
        kind = FeatureOverride.kindOf(key),
        title = key.removePrefix(CSC_PREFIX).removePrefix(FLOATING_PREFIX)
    )

    /** Undoes the obfuscation: rotate each byte left, then XOR it with the salt for its position. */
    private fun decode(bytes: ByteArray): ByteArray {
        val out = ByteArray(bytes.size)
        for (i in bytes.indices) {
            val b = bytes[i].toInt() and 0xFF
            val shift = SHIFTS[i % SHIFTS.size]
            val rotated = ((b shl shift) or (b shr (8 - shift))) and 0xFF
            out[i] = (rotated xor (SALTS[i % SALTS.size] and 0xFF)).toByte()
        }
        return out
    }

    /** The rotation table, from the community decoder for these files. */
    private val SHIFTS = intArrayOf(
        1, 1, 0, 2, 2, 4, 5, 0, 4, 7, 1, 6, 5, 3, 3, 1,
        2, 5, 0, 6, 2, 2, 4, 2, 2, 3, 0, 2, 1, 2, 4, 3,
        4, 0, 0, 0, 3, 5, 3, 1, 6, 5, 6, 1, 1, 1, 0, 0,
        3, 2, 7, 7, 5, 6, 7, 3, 5, 1, 0, 7, 6, 3, 6, 5,
        4, 5, 3, 5, 1, 3, 3, 1, 5, 4, 1, 0, 0, 2, 6, 6,
        6, 6, 4, 0, 1, 1, 0, 5, 5, 4, 2, 4, 6, 1, 7, 1,
        2, 1, 1, 6, 5, 4, 7, 6, 5, 1, 6, 7, 0, 2, 6, 3,
        1, 7, 1, 1, 7, 4, 0, 4, 2, 5, 3, 1, 1, 5, 6, 0,
        3, 5, 3, 6, 5, 7, 2, 5, 6, 6, 2, 2, 3, 6, 0, 4,
        3, 2, 0, 2, 2, 3, 5, 3, 3, 2, 5, 5, 5, 1, 3, 1,
        1, 1, 4, 5, 1, 6, 2, 4, 7, 1, 4, 6, 0, 6, 4, 3,
        2, 6, 1, 6, 3, 2, 1, 6, 7, 3, 2, 1, 1, 5, 6, 7,
        2, 2, 2, 7, 4, 6, 7, 5, 3, 1, 4, 2, 7, 1, 6, 2,
        4, 1, 5, 6, 5, 4, 5, 0, 1, 1, 6, 3, 7, 2, 0, 2,
        5, 0, 1, 3, 3, 2, 6, 7, 7, 2, 5, 6, 0, 4, 1, 2,
        5, 3, 7, 6, 5, 2, 5, 2, 0, 1, 3, 1, 4, 3, 4, 2,
    )

    /** The salt table, from the same decoder. */
    private val SALTS = intArrayOf(
        65, -59, 33, -34, 107, 28, -107, 55, 78, 17, -81, 6, -80, -121, -35, -23,
        72, 122, -63, -43, 68, 119, -78, -111, -60, 31, 60, 57, 92, -88, -100, -69,
        -106, 91, 69, 93, 110, 23, 93, 53, -44, -51, 64, -80, 46, 2, -4, 12,
        -45, 80, -44, -35, -111, -28, -66, -116, 39, 2, -27, -45, -52, 125, 39, 66,
        -90, 63, -105, -67, 84, -57, -4, -4, 101, -90, 81, 10, -33, 1, 67, -57,
        -71, 18, -74, 102, 96, -89, 64, -17, 54, -94, -84, -66, 14, 119, 121, 2,
        -78, -79, 89, 63, 93, 109, -78, -51, 66, -36, 32, 86, 3, -58, -15, 92,
        58, 2, -89, -80, -13, -1, 122, -4, 48, 63, -44, 59, 100, -42, -45, 59,
        -7, -17, -54, 34, -54, 71, -64, -26, -87, -80, -17, -44, -38, -112, 70, 10,
        -106, 95, -24, -4, -118, 45, -85, -13, 85, 25, -102, -119, 13, -37, 116, 46,
        -69, 59, 42, -90, -38, -105, 101, -119, -36, 97, -3, -62, -91, -97, -125, 17,
        14, 106, -72, -119, 99, 111, 20, 18, -27, 113, 64, -24, 74, -60, -100, 26,
        56, -44, -70, 12, -51, -100, -32, -11, 26, 48, -117, 98, -93, 51, -25, -79,
        -31, 97, 87, -105, -64, 7, -13, -101, 33, -122, 5, -104, 89, -44, -117, 63,
        -80, -6, -71, -110, -29, -105, 116, 107, -93, 91, -41, -13, 20, -115, -78, 43,
        79, -122, 6, 102, -32, 52, -118, -51, 72, -104, 41, -38, 124, 72, -126, -35,
    )
}
