package io.github.soclear.oneuix.hook

import android.content.Context
import android.os.Build
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers.findAndHookMethod
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import io.github.soclear.oneuix.data.Package
import java.io.File
import java.lang.reflect.Modifier
import java.util.Locale

object Bixby {

    fun init(lpparam: LoadPackageParam, injectModel: Boolean, labsMgr: Boolean, wwvBypass: Boolean) {
        when (lpparam.packageName) {
            Package.BIXBY_AGENT -> initBixbyAgent(lpparam, injectModel, labsMgr, wwvBypass)
            Package.BIXBY_WAKEUP -> initBixbyWakeup(lpparam, wwvBypass)
        }
    }

    private fun initBixbyAgent(
        lpparam: LoadPackageParam,
        injectModel: Boolean,
        labsMgr: Boolean,
        wwvBypass: Boolean,
    ) {
        XposedBridge.log("[OneUIX-Bixby] Init injectModel=$injectModel labsMgr=$labsMgr wwvBypass=$wwvBypass")

        if (injectModel) {
            try {
                hookInjectModel(lpparam)
                XposedBridge.log("[OneUIX-Bixby]   [+] injectModel")
            } catch (e: Throwable) {
                XposedBridge.log("[OneUIX-Bixby]   [-] injectModel: ${e.message}")
            }
        }

        if (labsMgr) {
            try {
                hookLabsFeatureManager(lpparam)
                XposedBridge.log("[OneUIX-Bixby]   [+] labsFeatureManager")
            } catch (e: Throwable) {
                XposedBridge.log("[OneUIX-Bixby]   [-] labsFeatureManager: ${e.message}")
            }
        }

        if (wwvBypass) {
            try {
                hookWakeupWordValidator(lpparam)
                XposedBridge.log("[OneUIX-Bixby]   [+] wakeupWordValidator")
            } catch (e: Throwable) {
                XposedBridge.log("[OneUIX-Bixby]   [-] wakeupWordValidator: ${e.message}")
            }
        }
    }

    private fun initBixbyWakeup(lpparam: LoadPackageParam, wwvBypass: Boolean) {
        hookWakeupCustomPhrase(lpparam)
        if (wwvBypass) {
            hookWakeupWordTypeValidator(lpparam)
            hookKwdCjkFix(lpparam)
        }
    }

    private fun hookInjectModel(lpparam: LoadPackageParam) {
        findAndHookMethod(
            "android.app.SharedPreferencesImpl",
            lpparam.classLoader,
            "getString",
            String::class.java,
            String::class.java,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (param.args[0] == "pref_key_on_device_config_cache") {
                        val orig = (param.result ?: param.args[1] ?: "") as String
                        if (!orig.contains(Build.MODEL)) {
                            param.result = if (orig.isEmpty()) Build.MODEL else "$orig,${Build.MODEL}"
                        }
                    }
                }
            }
        )
    }

    private fun hookLabsFeatureManager(lpparam: LoadPackageParam) {
        try {
            val clazz = Class.forName(
                "com.samsung.android.bixby.agent.common.util.datamanager.LabsFeatureManager",
                true,
                lpparam.classLoader,
            )
            for (name in arrayOf("isSupported", "isAvailable", "isEnabled", "isLabs")) {
                findAndHookMethod(
                    clazz,
                    name,
                    String::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            if (param.args[0] == "labs_custom_wakeup") {
                                param.result = true
                            }
                        }
                    }
                )
            }
            findAndHookMethod(
                clazz,
                "isLabsMenuSupported",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        param.result = true
                    }
                }
            )
        } catch (_: Throwable) {
        }
    }

    private fun hookWakeupWordValidator(lpparam: LoadPackageParam) {
        val clazz = lpparam.classLoader.loadClass("com.samsung.voicewakeup.wwv.WakeupWordValidator")
        for (method in clazz.declaredMethods) {
            if (!Modifier.isPublic(method.modifiers)) continue
            val pts = method.parameterTypes
            if (method.returnType == Boolean::class.javaPrimitiveType &&
                pts.contentEquals(arrayOf(Locale::class.java, String::class.java, String::class.java, String::class.java))
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
                pts.contentEquals(arrayOf(Context::class.java, String::class.java, Locale::class.java, String::class.java))
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

    private fun hookWakeupWordTypeValidator(lpparam: LoadPackageParam) {
        for (className in arrayOf(
            "com.samsung.voicewakeup.kwv.normal.custom.WakeupKwvNormalCommon",
            "com.samsung.voicewakeup.kwv.bargein.custom.WakeupKwvBargeinCommon",
            "com.samsung.voicewakeup.kwv.acousticecho.custom.WakeupKwvAcousticEchoCommon",
        )) {
            try {
                val clazz = lpparam.classLoader.loadClass(className)
                for (method in clazz.declaredMethods) {
                    if (method.returnType != Boolean::class.javaPrimitiveType) continue
                    if (!method.parameterTypes.contentEquals(arrayOf(String::class.java, Locale::class.java))) continue
                    XposedBridge.hookMethod(
                        method,
                        object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                param.result = true
                            }
                        }
                    )
                }
            } catch (_: Throwable) {
            }
        }
    }

    private fun hookKwdCjkFix(lpparam: LoadPackageParam) {
        for (className in arrayOf(
            "com.samsung.voicewakeup.kwd.normal.custom.WakeupKwdNormalCustom",
            "com.samsung.voicewakeup.kwd.bargein.custom.WakeupKwdBargeinCustom",
            "com.samsung.voicewakeup.kwd.acousticecho.custom.WakeupKwdAcousticEchoCustom",
        )) {
            try {
                val clazz = lpparam.classLoader.loadClass(className)
                var kwField: java.lang.reflect.Field? = null
                try {
                    kwField = clazz.getDeclaredField("mKeyword")
                    kwField.isAccessible = true
                } catch (_: Throwable) {
                }

                for (method in clazz.declaredMethods) {
                    if (method.returnType != Int::class.javaPrimitiveType) continue
                    val pts = method.parameterTypes
                    val isVr = (pts.size == 1 && pts[0].isArray && pts[0].componentType == Short::class.javaPrimitiveType) ||
                        (pts.size == 3 && pts[0].isArray && pts[0].componentType == Short::class.javaPrimitiveType &&
                            pts[1] == Int::class.javaPrimitiveType && pts[2] == Int::class.javaPrimitiveType)
                    if (!isVr) continue

                    val kwRef = kwField
                    XposedBridge.hookMethod(
                        method,
                        object : XC_MethodHook() {
                            override fun afterHookedMethod(param: MethodHookParam) {
                                val ret = param.result as? Int ?: return
                                if (ret != 0) return
                                val kw = try {
                                    kwRef?.get(param.thisObject) ?: ""
                                } catch (_: Throwable) {
                                    ""
                                }
                                if ((kw as? String)?.any { it in '\u4E00'..'\u9FFF' } == true) {
                                    param.result = 1
                                }
                            }
                        }
                    )
                }
            } catch (_: Throwable) {
            }
        }
    }

    private fun hookWakeupCustomPhrase(lpparam: LoadPackageParam) {
        findAndHookMethod(
            "android.app.SharedPreferencesImpl",
            lpparam.classLoader,
            "getString",
            String::class.java,
            String::class.java,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (param.args[0] == "myvoice_string_custom") {
                        val orig = param.result ?: param.args[1] ?: ""
                        if (orig.toString().isEmpty()) {
                            val txt = readWakeupSP("myvoice_string_custom")
                            if (txt.isNotEmpty()) param.result = txt
                        }
                    }
                }
            }
        )
        try {
            val cursorClass = lpparam.classLoader.loadClass("android.database.MatrixCursor")
            findAndHookMethod(
                cursorClass,
                "addRow",
                arrayOfNulls<Any>(0).javaClass,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val row = param.args[0] as? Array<Any?> ?: return
                        try {
                            val cols = param.thisObject.javaClass
                                .getDeclaredField("columnNames")
                                .apply { isAccessible = true }
                                .get(param.thisObject) as? Array<String> ?: return
                            for (i in cols.indices) {
                                if (cols[i] != "customKeyword") continue
                                if (row[i] == null || row[i].toString().isEmpty()) {
                                    val txt = readWakeupSP("myvoice_string_custom")
                                    if (txt.isNotEmpty()) row[i] = txt
                                }
                            }
                        } catch (_: Throwable) {
                        }
                    }
                }
            )
        } catch (_: Throwable) {
        }
    }

    private fun readWakeupSP(key: String): String {
        try {
            val dir = File("/data/data/com.samsung.android.bixby.wakeup/shared_prefs")
            if (!dir.exists() || !dir.isDirectory) return ""
            for (file in dir.listFiles() ?: emptyArray()) {
                if (!file.name.endsWith(".xml")) continue
                val match = Regex("<string name=\"$key\">(.*?)</string>").find(file.readText())
                if (match != null) return match.groupValues[1]
            }
        } catch (_: Throwable) {
        }
        return ""
    }
}
