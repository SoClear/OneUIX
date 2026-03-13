package io.github.soclear.oneuix.hook

import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers.callMethod
import de.robv.android.xposed.XposedHelpers.findAndHookMethod
import de.robv.android.xposed.XposedHelpers.findClass
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import io.github.soclear.oneuix.data.Package


object HealthMonitor {
    fun bypassCountryCheck(loadPackageParam: LoadPackageParam) {
        if (loadPackageParam.packageName != Package.HEALTH_MONITOR) {
            return
        }
        try {
            val supportedTypeClass = findClass(
                "com.samsung.android.shealthmonitor.util.CommonConstants\$SupportedType",
                loadPackageParam.classLoader
            )

            // 运行时检查枚举类，找到 SUPPORTED 类型
            val supportedType = findSupportedTypeEnumValue(supportedTypeClass)
                ?: run {
                    XposedBridge.log("OneUIX: Failed to find SUPPORTED enum value in HealthMonitor")
                    return
                }

            findAndHookMethod(
                "com.samsung.android.shealthmonitor.util.OnboardingUtil",
                loadPackageParam.classLoader,
                "j",
                object : XC_MethodReplacement() {
                    override fun replaceHookedMethod(param: MethodHookParam): Any {
                        return supportedType
                    }
                }
            )
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }
    }

    /**
     * 运行时遍历枚举值，找到 SUPPORTED 类型
     */
    private fun findSupportedTypeEnumValue(enumClass: Class<*>): Any? {
        val enumConstants = enumClass.enumConstants ?: return null

        // 打印所有枚举值信息，便于调试
        val sb = StringBuilder("OneUIX HealthMonitor SupportedType enum values:\n")
        enumConstants.forEachIndexed { index, enumValue ->
            val name = callMethod(enumValue, "name") as String
            val ordinal = callMethod(enumValue, "ordinal") as Int
            val toString = enumValue.toString()
            sb.append("  [$index] name=$name, ordinal=$ordinal, toString=$toString\n")
        }
        XposedBridge.log(sb.toString())

        // 方式1: 通过枚举名称查找 "SUPPORTED"
        for (enumValue in enumConstants) {
            try {
                val name = callMethod(enumValue, "name") as String
                if (name == "SUPPORTED") {
                    return enumValue
                }
            } catch (_: Exception) {
            }
        }

        // 方式2: toString() 包含 "SUPPORTED" 或类似标识
        for (enumValue in enumConstants) {
            try {
                val str = enumValue.toString()
                if (str.contains("SUPPORTED", ignoreCase = true)) {
                    return enumValue
                }
            } catch (_: Exception) {
            }
        }

        // 方式3: 假设 SUPPORTED 是第一个枚举值 (ordinal = 0)
        if (enumConstants.isNotEmpty()) {
            return enumConstants[0]
        }

        return null
    }
}
