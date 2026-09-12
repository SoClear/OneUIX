package io.github.soclear.oneuix.hook

import android.content.Context
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement.returnConstant
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.XposedHelpers.findAndHookMethod
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import kotlinx.serialization.Serializable
import io.github.soclear.oneuix.common.Package
import io.github.soclear.oneuix.hook.util.HookConfig
import io.github.soclear.oneuix.hook.util.afterAttach
import io.github.soclear.oneuix.hook.util.getHookConfig
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.result.MethodData
import org.luckypray.dexkit.wrap.DexField
import org.luckypray.dexkit.wrap.DexMethod
import java.lang.reflect.Field
import java.lang.reflect.Modifier

object Call {
    fun supportVoiceCallRecording(
        loadPackageParam: LoadPackageParam,
        preferRecordingButton: Boolean
    ) {
        if (loadPackageParam.packageName != Package.TELEPHONYUI &&
            loadPackageParam.packageName != Package.INCALLUI &&
            loadPackageParam.packageName != Package.DIALER
        ) return

        val callback = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (param.args[0] == "CscFeature_VoiceCall_ConfigRecording") {
                    param.result = "RecordingAllowed" + if (preferRecordingButton) "" else "ByMenu"
                }
            }
        }

        try {
            findAndHookMethod(
                "com.samsung.android.feature.SemCscFeature",
                loadPackageParam.classLoader,
                "getString",
                String::class.java,
                String::class.java,
                callback
            )

            findAndHookMethod(
                "com.samsung.android.feature.SemCscFeature",
                loadPackageParam.classLoader,
                "getString",
                String::class.java,
                callback
            )
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }
    }

    fun showGeocodedLocationInRecentCall(loadPackageParam: LoadPackageParam) {
        if (loadPackageParam.packageName != Package.DIALER) return
        afterAttach {
            val hookConfig = getHookConfig { getHookConfigFromDexKit() } ?: return@afterAttach

            XposedBridge.hookMethod(
                DexMethod(hookConfig.setSubTextMethod).getMethodInstance(classLoader),
                object : XC_MethodHook() {
                    val geoCodedLocationField =
                        DexField(hookConfig.geoCodedLocationField).getFieldInstance(classLoader)
                    val subTextField =
                        DexField(hookConfig.subTextField).getFieldInstance(classLoader)

                    override fun afterHookedMethod(param: MethodHookParam) {
                        val baseCallLog = param.args[0]
                        val callLogViewItem = param.args[1]
                        val geocodedLocation = geoCodedLocationField.get(baseCallLog)
                        val subText = subTextField.get(callLogViewItem)
                        val subTextWithLocation = "$subText $geocodedLocation".trim()
                        subTextField.set(callLogViewItem, subTextWithLocation)
                    }
                }
            )
        }
    }

    fun isOpStyleCHN(loadPackageParam: LoadPackageParam) {
        if (loadPackageParam.packageName != Package.DIALER) return
        try {
            findAndHookMethod(
                "com.samsung.android.dialtacts.util.CscFeatureUtil",
                loadPackageParam.classLoader,
                "isOpStyleCHNImpl",
                returnConstant(true)
            )
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }
    }

    @Serializable
    private data class CallHookConfig(
        override val versionCode: Long,
        val geoCodedLocationField: String,
        val subTextField: String,
        val setSubTextMethod: String,
    ) : HookConfig

    private fun Context.getHookConfigFromDexKit(): CallHookConfig? {
        val exclusions = listOf(
            "android",
            "androidx",
            "appfunctions_aggregated_deps",
            "com",
            "dagger",
            "kotlin",
            "kotlinx"
        )

        fun geoCodedLocation(bridge: DexKitBridge): Field? {
            val baseCallLogClassData = bridge.findClass {
                excludePackages(exclusions)
                matcher {
                    modifiers = Modifier.PUBLIC or Modifier.FINAL
                    superClass = "java.lang.Object"
                    usingStrings("BaseCallLog(id=")
                }
            }.singleOrNull() ?: return null
            val baseCallLogClass = baseCallLogClassData.getInstance(classLoader)
            val instance = XposedHelpers.newInstance(baseCallLogClass)

            val tokenToField = mutableMapOf<String, Field>()

            baseCallLogClass.declaredFields.forEach { field ->
                if (field.type == String::class.java && !Modifier.isStatic(field.modifiers)) {
                    field.isAccessible = true
                    val token = "TOKEN_${field.name}"

                    // 将 token 注入到实例中
                    field.set(instance, token)
                    tokenToField[token] = field
                }
            }

            // 调用 toString() 方法
            // 目标代码： ... + ", geoCodedLocation=" + this.p + ...
            val result = instance.toString()

            // 分析结果
            val keyword = "geoCodedLocation="
            val index = result.indexOf(keyword)
            if (index != -1) {
                // 截取 geoCodedLocation= 之后的内容
                val after = result.substring(index + keyword.length)

                // 检查内容是以哪个 TOKEN 开头
                for (entry in tokenToField.entries) {
                    if (after.startsWith(entry.key)) {
                        // 找到了 geoCodedLocation 对应字段
                        return entry.value
                    }
                }
            }
            return null
        }

        fun subText(bridge: DexKitBridge): Field? {
            val callLogViewItemClassData = bridge.findClass {
                excludePackages(exclusions)
                matcher {
                    modifiers = Modifier.PUBLIC or Modifier.FINAL
                    superClass = "java.lang.Object"
                    usingStrings("CallLogViewItem {id=")
                }
            }.singleOrNull() ?: return null

            val toStingMethodData = callLogViewItemClassData.findMethod {
                matcher {
                    usingStrings("CallLogViewItem {id=")
                }
            }.singleOrNull() ?: return null

            val callLogGroupClassData = bridge.findClass {
                excludePackages(exclusions)
                matcher {
                    modifiers = Modifier.PUBLIC or Modifier.FINAL
                    superClass = "java.lang.Object"
                    usingStrings("CallLogGroup(isDateChanged=")
                }
            }.singleOrNull() ?: return null

            val callLogGroupClass = callLogGroupClassData.getInstance(classLoader)
            val callLogGroup = XposedHelpers.newInstance(callLogGroupClass, true, true)

            val callLogViewItemClass = callLogViewItemClassData.getInstance(classLoader)
            val callLogViewItem = XposedHelpers.newInstance(callLogViewItemClass, callLogGroup)

            val tokenToField = mutableMapOf<String, Field>()

            callLogViewItemClass.declaredFields.forEach { field ->
                if (field.type == String::class.java && !Modifier.isStatic(field.modifiers)) {
                    field.isAccessible = true
                    val token = "TOKEN_${field.name}"

                    // 将 token 注入到实例中
                    field.set(callLogViewItem, token)
                    tokenToField[token] = field
                }
            }

            val toStingMethodInstance = toStingMethodData.getMethodInstance(classLoader)

            val result = if (toStingMethodData.paramCount == 0) {
                toStingMethodInstance.invoke(callLogViewItem)
            } else {
                toStingMethodInstance.invoke(null, callLogViewItem)
            } as String

            // 分析结果
            val keyword = "subText="
            val index = result.indexOf(keyword)
            if (index != -1) {
                // 截取 geoCodedLocation= 之后的内容
                val after = result.substring(index + keyword.length)

                // 检查内容是以哪个 TOKEN 开头
                for (entry in tokenToField.entries) {
                    if (after.startsWith(entry.key)) {
                        // 找到了 geoCodedLocation 对应字段
                        return entry.value
                    }
                }
            }
            return null
        }

        fun setSubText(bridge: DexKitBridge, subText: Field): MethodData? {
            return bridge.findClass {
                excludePackages(exclusions)
                matcher {
                    modifiers = Modifier.PUBLIC or Modifier.FINAL
                    usingStrings("screencall;autopickupreply")
                }
            }.findMethod {
                matcher {
                    paramCount = 2
                    returnType = "void"
                    usingStrings("screencall;autopickupreply")
                    addUsingField {
                        name = subText.name
                        declaredClass(subText.declaringClass)
                    }
                }
            }.singleOrNull()
        }

        System.loadLibrary("dexkit")
        DexKitBridge.create(classLoader, true).use { bridge ->
            val geoCodedLocation = geoCodedLocation(bridge) ?: return null
            val subText = subText(bridge) ?: return null
            val setSubText = setSubText(bridge, subText) ?: return null
            return CallHookConfig(
                versionCode = packageManager.getPackageInfo(packageName, 0).longVersionCode,
                geoCodedLocationField = DexField(geoCodedLocation).serialize(),
                subTextField = DexField(subText).serialize(),
                setSubTextMethod = setSubText.toDexMethod().serialize(),
            )
        }
    }
}
