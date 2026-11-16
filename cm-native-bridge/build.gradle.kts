// The typed ColorMotion bridge: compiled against stubs, dexed with D8 and shipped as
// assets/colormotion-native.dex. It is loaded at runtime with SystemUI's class loader as parent.
import java.io.File
import java.util.Properties
import javax.inject.Inject
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.bundling.Jar
import org.gradle.process.ExecOperations

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

val sdkDir: File = androidSdkDir()

// Newest installed platform that is at least API 35.
val androidJar: File = File(sdkDir, "platforms").listFiles().orEmpty()
    .mapNotNull { dir -> dir.name.removePrefix("android-").toDoubleOrNull()?.let { it to dir } }
    .filter { it.first >= 35 && File(it.second, "android.jar").isFile }
    .maxByOrNull { it.first }
    ?.let { File(it.second, "android.jar") }
    ?: error("No Android platform 35+ under $sdkDir/platforms")

// Newest installed build-tools that ships D8.
fun versionPart(name: String, index: Int): Int =
    name.split('.').getOrNull(index)?.takeWhile { it.isDigit() }?.toIntOrNull() ?: 0

val d8Jar: File = File(sdkDir, "build-tools").listFiles().orEmpty()
    .filter { File(it, "lib/d8.jar").isFile }
    .maxWithOrNull(
        compareBy<File>({ versionPart(it.name, 0) }, { versionPart(it.name, 1) }, { versionPart(it.name, 2) })
    )
    ?.let { File(it, "lib/d8.jar") }
    ?: error("No build-tools with lib/d8.jar under $sdkDir/build-tools")

dependencies {
    compileOnly(files(androidJar))
    compileOnly(project(":cm-native-api"))
    compileOnly("org.jetbrains.kotlin:kotlin-stdlib:2.2.10")
}

abstract class DexBridge @Inject constructor(private val execOps: ExecOperations) : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val bridgeJar: RegularFileProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val libraryJars: ConfigurableFileCollection

    @get:Input
    abstract val androidJarPath: Property<String>

    @get:Input
    abstract val d8JarPath: Property<String>

    @get:OutputDirectory
    abstract val outDir: DirectoryProperty

    @TaskAction
    fun dex() {
        val out = outDir.get().asFile
        out.deleteRecursively()
        out.mkdirs()
        val androidJarFile = androidJarPath.get()
        val jars = libraryJars.files.filter { it.isFile && it.name.endsWith(".jar") }
        execOps.javaexec {
            classpath(d8JarPath.get())
            mainClass.set("com.android.tools.r8.D8")
            val arguments = mutableListOf("--release", "--min-api", "35", "--lib", androidJarFile)
            jars.filter { it.absolutePath != androidJarFile }.forEach { arguments += listOf("--lib", it.absolutePath) }
            arguments += listOf("--output", out.absolutePath, bridgeJar.get().asFile.absolutePath)
            args = arguments
        }
    }
}

val apiJar = project(":cm-native-api").tasks.named<Jar>("jar")

val dexBridge = tasks.register<DexBridge>("dexBridge") {
    bridgeJar.set(tasks.named<Jar>("jar").flatMap { it.archiveFile })
    libraryJars.from(configurations.named("compileClasspath"))
    libraryJars.from(apiJar.flatMap { it.archiveFile })
    androidJarPath.set(androidJar.absolutePath)
    d8JarPath.set(d8Jar.absolutePath)
    outDir.set(layout.buildDirectory.dir("dex"))
}

tasks.register<Sync>("bridgeAssets") {
    from(dexBridge.flatMap { it.outDir.file("classes.dex") }) { rename { "colormotion-native.dex" } }
    into(layout.buildDirectory.dir("assets"))
}
