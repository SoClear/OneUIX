package io.github.soclear.oneuix.hook

import android.content.Context
import android.os.Build
import android.os.SystemClock
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers.findAndHookMethod
import de.robv.android.xposed.XposedHelpers.findClass
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import io.github.soclear.oneuix.data.Package
import java.io.File
import java.lang.reflect.Modifier
import java.util.Locale

object Bixby {
    fun init(
        lpparam: LoadPackageParam,
        unlockOfflineProcessing: Boolean,
        supportCustomWakeup: Boolean,
        bypassWakeWordRestrictions: Boolean,
    ) {
        when (lpparam.packageName) {
            Package.BIXBY_AGENT -> {
                if (unlockOfflineProcessing) {
                    unlockOfflineProcessing(lpparam)
                }
                if (supportCustomWakeup) {
                    supportCustomWakeup(lpparam)
                }
                if (bypassWakeWordRestrictions) {
                    bypassWakeWordRestrictions(lpparam)
                }
            }

            Package.BIXBY_WAKEUP -> {
                if (supportCustomWakeup || bypassWakeWordRestrictions) {
                    restoreCustomWakeupText(lpparam)
                }
                if (bypassWakeWordRestrictions) {
                    allowWakeupWordTypes(lpparam)
                    fixAsianKeywordDetection(lpparam)
                }
            }
        }
    }

    private fun unlockOfflineProcessing(lpparam: LoadPackageParam) {
        try {
            findAndHookMethod(
                "android.app.SharedPreferencesImpl",
                lpparam.classLoader,
                "getString",
                String::class.java,
                String::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (param.args[0] != DEVICE_CONFIG_CACHE_KEY) return

                        val original = param.result as? String
                        val defaultValue = param.args[1] as? String
                        val cache = original ?: defaultValue.orEmpty()
                        val deviceModel = Build.MODEL

                        if (deviceModel.isBlank() || cache.split(",").contains(deviceModel)) {
                            return
                        }
                        param.result = if (cache.isBlank()) {
                            deviceModel
                        } else {
                            "$cache,$deviceModel"
                        }
                    }
                }
            )
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }
    }

    private fun supportCustomWakeup(lpparam: LoadPackageParam) {
        val labsFeatureManagerClass = try {
            findClass(LABS_FEATURE_MANAGER_CLASS, lpparam.classLoader)
        } catch (t: Throwable) {
            XposedBridge.log(t)
            return
        }

        val featureCallback = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (param.args[0] == CUSTOM_WAKEUP_FEATURE) {
                    param.result = true
                }
            }
        }

        listOf("isSupported", "isAvailable", "isEnabled", "isLabs").forEach { methodName ->
            try {
                findAndHookMethod(
                    labsFeatureManagerClass,
                    methodName,
                    String::class.java,
                    featureCallback
                )
            } catch (t: Throwable) {
                XposedBridge.log(t)
            }
        }

        try {
            findAndHookMethod(
                labsFeatureManagerClass,
                "isLabsMenuSupported",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        param.result = true
                    }
                }
            )
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }
    }

    private fun bypassWakeWordRestrictions(lpparam: LoadPackageParam) {
        val wakeupWordValidatorClass = try {
            findClass(WAKEUP_WORD_VALIDATOR_CLASS, lpparam.classLoader)
        } catch (t: Throwable) {
            XposedBridge.log(t)
            return
        }

        wakeupWordValidatorClass.declaredMethods.forEach { method ->
            if (!Modifier.isPublic(method.modifiers)) return@forEach

            val parameterTypes = method.parameterTypes
            if (method.returnType == Boolean::class.javaPrimitiveType &&
                parameterTypes.contentEquals(
                    arrayOf(
                        Locale::class.java,
                        String::class.java,
                        String::class.java,
                        String::class.java,
                    )
                )
            ) {
                XposedBridge.hookMethod(
                    method,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            param.result = true
                        }
                    }
                )
            }

            if (method.returnType == Int::class.javaPrimitiveType &&
                parameterTypes.contentEquals(
                    arrayOf(
                        Context::class.java,
                        String::class.java,
                        Locale::class.java,
                        String::class.java,
                    )
                )
            ) {
                XposedBridge.hookMethod(
                    method,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            param.result = 0
                        }
                    }
                )
            }
        }
    }

    private fun restoreCustomWakeupText(lpparam: LoadPackageParam) {
        try {
            findAndHookMethod(
                "android.app.SharedPreferencesImpl",
                lpparam.classLoader,
                "getString",
                String::class.java,
                String::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (param.args[0] != CUSTOM_WAKEUP_TEXT_KEY) return
                        if (!(param.result as? String).isNullOrEmpty()) return

                        val text = readCustomWakeupText()
                        if (text.isNotEmpty()) {
                            param.result = text
                        }
                    }
                }
            )
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }

        try {
            val matrixCursorClass = findClass("android.database.MatrixCursor", lpparam.classLoader)
            val columnNamesField = try {
                matrixCursorClass
                    .getDeclaredField("columnNames")
                    .apply { isAccessible = true }
            } catch (t: Throwable) {
                XposedBridge.log(t)
                null
            }
            findAndHookMethod(
                matrixCursorClass,
                "addRow",
                arrayOfNulls<Any>(0).javaClass,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val row = param.args[0] as? Array<*> ?: return
                        val columnNames = try {
                            columnNamesField?.get(param.thisObject) as? Array<*> ?: return
                        } catch (_: Throwable) {
                            return
                        }

                        columnNames.forEachIndexed { index, columnName ->
                            if (columnName != "customKeyword") return@forEachIndexed
                            if (!(row[index] as? String).isNullOrEmpty()) return@forEachIndexed

                            val text = readCustomWakeupText()
                            if (text.isNotEmpty()) {
                                @Suppress("UNCHECKED_CAST")
                                (row as Array<Any?>)[index] = text
                            }
                        }
                    }
                }
            )
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }
    }

    private fun allowWakeupWordTypes(lpparam: LoadPackageParam) {
        WAKEUP_KWV_CLASSES.forEach { className ->
            try {
                val clazz = findClass(className, lpparam.classLoader)
                clazz.declaredMethods.forEach { method ->
                    if (method.returnType != Boolean::class.javaPrimitiveType) return@forEach
                    if (!method.parameterTypes.contentEquals(arrayOf(String::class.java, Locale::class.java))) {
                        return@forEach
                    }

                    XposedBridge.hookMethod(
                        method,
                        object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                param.result = true
                            }
                        }
                    )
                }
            } catch (t: Throwable) {
                XposedBridge.log(t)
            }
        }
    }

    private fun fixAsianKeywordDetection(lpparam: LoadPackageParam) {
        WAKEUP_KWD_CLASSES.forEach { className ->
            try {
                val clazz = findClass(className, lpparam.classLoader)
                val keywordField = try {
                    clazz.getDeclaredField("mKeyword").apply { isAccessible = true }
                } catch (_: Throwable) {
                    null
                }

                clazz.declaredMethods.forEach { method ->
                    if (method.returnType != Int::class.javaPrimitiveType) return@forEach
                    if (!method.hasKwdVerifyRunSignature()) return@forEach

                    XposedBridge.hookMethod(
                        method,
                        object : XC_MethodHook() {
                            override fun afterHookedMethod(param: MethodHookParam) {
                                if (param.result != 0) return

                                val keyword = try {
                                    keywordField?.get(param.thisObject) as? String
                                } catch (_: Throwable) {
                                    null
                                } ?: return

                                if (keyword.any { it.isAsianWakeupCharacter() }) {
                                    param.result = 1
                                }
                            }
                        }
                    )
                }
            } catch (t: Throwable) {
                XposedBridge.log(t)
            }
        }
    }

    private fun Char.isAsianWakeupCharacter(): Boolean {
        return this in '\u4E00'..'\u9FFF' ||
            this in '\uAC00'..'\uD7AF' ||
            this in '\u3040'..'\u30FF'
    }

    private fun java.lang.reflect.Method.hasKwdVerifyRunSignature(): Boolean {
        val types = parameterTypes
        val hasShortArray = types.firstOrNull()?.let {
            it.isArray && it.componentType == Short::class.javaPrimitiveType
        } == true

        return when (types.size) {
            1 -> hasShortArray
            3 -> hasShortArray &&
                types[1] == Int::class.javaPrimitiveType &&
                types[2] == Int::class.javaPrimitiveType

            else -> false
        }
    }

    private fun readCustomWakeupText(): String {
        val now = SystemClock.elapsedRealtime()
        val cachedText = cachedCustomWakeupText
        if (cachedText != null &&
            now - cachedCustomWakeupTextTimeMillis < CUSTOM_WAKEUP_TEXT_CACHE_TTL_MILLIS
        ) {
            return cachedText
        }

        val text = try {
            val directory = File(BIXBY_WAKEUP_SHARED_PREFERENCES_PATH)
            if (!directory.isDirectory) {
                ""
            } else {
                directory.listFiles()
                    ?.asSequence()
                    ?.filter { it.name.endsWith(".xml") }
                    ?.mapNotNull { file ->
                        customWakeupTextRegex
                            .find(file.readText())
                            ?.groupValues
                            ?.get(1)
                    }
                    ?.firstOrNull()
                    .orEmpty()
            }
        } catch (_: Throwable) {
            ""
        }
        cachedCustomWakeupText = text
        cachedCustomWakeupTextTimeMillis = now
        return text
    }

    private const val DEVICE_CONFIG_CACHE_KEY = "pref_key_on_device_config_cache"
    private const val CUSTOM_WAKEUP_FEATURE = "labs_custom_wakeup"
    private const val CUSTOM_WAKEUP_TEXT_KEY = "myvoice_string_custom"
    private const val CUSTOM_WAKEUP_TEXT_CACHE_TTL_MILLIS = 5_000L
    private const val BIXBY_WAKEUP_SHARED_PREFERENCES_PATH =
        "/data/data/com.samsung.android.bixby.wakeup/shared_prefs"
    private const val LABS_FEATURE_MANAGER_CLASS =
        "com.samsung.android.bixby.agent.common.util.datamanager.LabsFeatureManager"
    private const val WAKEUP_WORD_VALIDATOR_CLASS =
        "com.samsung.voicewakeup.wwv.WakeupWordValidator"
    private val WAKEUP_KWV_CLASSES = arrayOf(
        "com.samsung.voicewakeup.kwv.normal.custom.WakeupKwvNormalCommon",
        "com.samsung.voicewakeup.kwv.bargein.custom.WakeupKwvBargeinCommon",
        "com.samsung.voicewakeup.kwv.acousticecho.custom.WakeupKwvAcousticEchoCommon",
    )
    private val WAKEUP_KWD_CLASSES = arrayOf(
        "com.samsung.voicewakeup.kwd.normal.custom.WakeupKwdNormalCustom",
        "com.samsung.voicewakeup.kwd.bargein.custom.WakeupKwdBargeinCustom",
        "com.samsung.voicewakeup.kwd.acousticecho.custom.WakeupKwdAcousticEchoCustom",
    )
    private val customWakeupTextRegex by lazy {
        Regex("<string name=\"$CUSTOM_WAKEUP_TEXT_KEY\">(.*?)</string>")
    }
    @Volatile
    private var cachedCustomWakeupText: String? = null
    @Volatile
    private var cachedCustomWakeupTextTimeMillis: Long = 0
}
