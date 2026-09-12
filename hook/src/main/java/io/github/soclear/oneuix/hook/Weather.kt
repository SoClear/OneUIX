package io.github.soclear.oneuix.hook

import android.os.Build
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers.findAndHookMethod
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import io.github.soclear.oneuix.common.Package

object Weather {
    fun setProviderCN(loadPackageParam: LoadPackageParam) {
        if (loadPackageParam.packageName != Package.WEATHER) return
        try {
            if (loadPackageParam.appInfo.targetSdkVersion >= Build.VERSION_CODES.BAKLAVA) {
                setProvider(loadPackageParam, "CN")
                return
            }
            findAndHookMethod(
                "com.samsung.android.weather.domain.entity.forecast.ForecastProvider",
                loadPackageParam.classLoader,
                "dispatchByCountryCode",
                String::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        param.args[0] = "CN"
                    }
                }
            )
        } catch (t: Throwable) {
            XposedBridge.log(t)
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

    fun setProvider(loadPackageParam: LoadPackageParam, countryCode: String) {
        if (loadPackageParam.packageName != Package.WEATHER) return

        val callback = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                param.args[0] = countryCode
            }
        }

        findAndHookMethod(
            "com.samsung.android.weather.domain.WeatherRegion",
            loadPackageParam.classLoader,
            "getActiveCp",
            String::class.java,
            Int::class.javaPrimitiveType,
            callback
        )

        findAndHookMethod(
            "com.samsung.android.weather.domain.WeatherRegion",
            loadPackageParam.classLoader,
            "isChina",
            String::class.java,
            callback
        )

        findAndHookMethod(
            "com.samsung.android.weather.domain.WeatherRegion",
            loadPackageParam.classLoader,
            "isGlobal",
            String::class.java,
            Int::class.javaPrimitiveType,
            callback
        )

        findAndHookMethod(
            "com.samsung.android.weather.domain.WeatherRegion",
            loadPackageParam.classLoader,
            "isJapan",
            String::class.java,
            callback
        )

        findAndHookMethod(
            "com.samsung.android.weather.domain.WeatherRegion",
            loadPackageParam.classLoader,
            "isKorea",
            String::class.java,
            callback
        )
    }
}
