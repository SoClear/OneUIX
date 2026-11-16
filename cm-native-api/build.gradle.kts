// Compile-only stubs of the SystemUI / Compose members used by cm-native-bridge. Never packaged.
import java.io.File
import java.util.Properties

plugins { `java-library` }

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

fun androidSdkDir(): File {
    val fromEnv = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
    if (fromEnv != null && File(fromEnv).isDirectory) return File(fromEnv)
    val local = rootProject.file("local.properties")
    if (local.isFile) {
        val dir = Properties().apply { local.inputStream().use { load(it) } }.getProperty("sdk.dir")
        if (dir != null && File(dir).isDirectory) return File(dir)
    }
    error("Android SDK not found: set ANDROID_HOME or sdk.dir in local.properties")
}

// Newest installed platform that is at least API 35.
val androidJar: File = File(androidSdkDir(), "platforms").listFiles().orEmpty()
    .mapNotNull { dir -> dir.name.removePrefix("android-").toDoubleOrNull()?.let { it to dir } }
    .filter { it.first >= 35 && File(it.second, "android.jar").isFile }
    .maxByOrNull { it.first }
    ?.let { File(it.second, "android.jar") }
    ?: error("No Android platform 35+ under ${androidSdkDir()}/platforms")

dependencies {
    compileOnly(files(androidJar))
}
