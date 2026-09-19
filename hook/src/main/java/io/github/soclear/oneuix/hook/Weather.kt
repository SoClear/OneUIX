package io.github.soclear.oneuix.hook

import android.os.Build
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import io.github.soclear.oneuix.common.Package
import io.github.soclear.oneuix.hook.util.xlog

object Weather {
    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun setProviderCN() {
        if (param.packageName != Package.WEATHER) return
        try {
            if (param.applicationInfo.targetSdkVersion >= Build.VERSION_CODES.BAKLAVA) {
                setProvider("CN")
                return
            }
            xposedModule.hook(
                param.classLoader.loadClass("com.samsung.android.weather.domain.entity.forecast.ForecastProvider")
                    .getDeclaredMethod("dispatchByCountryCode", String::class.java)
            ).intercept { chain ->
                val newArgs = chain.args.toTypedArray()
                newArgs[0] = "CN"
                chain.proceed(newArgs)
            }
        } catch (t: Throwable) {
            xlog(t)
        }
        /*
        findAndHookMethod(
            "com.samsung.android.weather.data.model.forecast.ForecastProviderManagerImpl",
            loadPackageParam.classLoader,
            "getDeviceCpType",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    param.result = callMethod(param.thisObject, "getInfo", "HUA")
                }
            }
        )
         */
    }

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun setProvider(countryCode: String) {
        if (param.packageName != Package.WEATHER) return

        val weatherRegionClass =
            param.classLoader.loadClass("com.samsung.android.weather.domain.WeatherRegion")

        val hooker = XposedInterface.Hooker { chain ->
            val newArgs = chain.args.toTypedArray()
            newArgs[0] = countryCode
            chain.proceed(newArgs)
        }

        listOf(
            weatherRegionClass.getDeclaredMethod("getActiveCp", String::class.java, Int::class.javaPrimitiveType),
            weatherRegionClass.getDeclaredMethod("isChina", String::class.java),
            weatherRegionClass.getDeclaredMethod("isGlobal", String::class.java, Int::class.javaPrimitiveType),
            weatherRegionClass.getDeclaredMethod("isJapan", String::class.java),
            weatherRegionClass.getDeclaredMethod("isKorea", String::class.java)
        ).forEach { method ->
            xposedModule.hook(method).intercept(hooker)
        }
    }
}
