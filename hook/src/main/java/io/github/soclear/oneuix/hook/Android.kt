package io.github.soclear.oneuix.hook

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.os.Bundle
import android.util.Log
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import io.github.soclear.oneuix.common.Package
import io.github.soclear.oneuix.hook.util.set
import java.lang.reflect.Executable

@SuppressLint("PrivateApi")
object Android {
    private const val TAG = "Android"

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun disableWritingToolkitGlobally() {
        if (param.packageName != Package.ANDROID) return

        val galaxyAiRestrictionsPackage = "com.samsung.android.knox.galaxyai"
        val writingToolkitKey = "key_writing_toolkit"
        val grayoutKey = "grayout"

        val classLoader = param.classLoader

        try {
            val proxyClass = classLoader.loadClass("com.android.server.enterprise.EDMProxyService")
            proxyClass.declaredMethods.filter {
                it.name == "getApplicationRestrictions"
            }.forEach {
                xposedModule.hook(it).intercept { chain ->
                    val result = chain.proceed()
                    if (chain.args.getOrNull(0) != galaxyAiRestrictionsPackage) {
                        result
                    } else {
                        val restrictions = Bundle(result as? Bundle ?: Bundle.EMPTY)
                        val writingToolkit = Bundle(restrictions.getBundle(writingToolkitKey) ?: Bundle.EMPTY)
                        writingToolkit.putBoolean(grayoutKey, true)
                        restrictions.putBundle(writingToolkitKey, writingToolkit)
                        restrictions
                    }
                }
            }
        } catch (t: Throwable) {
            xposedModule.log(Log.ERROR, TAG, "disableWritingToolkitGlobally", t)
        }
    }

    @SuppressLint("BlockedPrivateApi")
    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun setBlockableNotificationChannel() {
        try {
            val notificationChannelClass = NotificationChannel::class.java

            notificationChannelClass.declaredConstructors.forEach {
                xposedModule.hook(it).intercept { chain ->
                    val result = chain.proceed()
                    chain.thisObject["mBlockableSystem"] = true
                    chain.thisObject["mImportanceLockedByOEM"] = false
                    chain.thisObject["mImportanceLockedDefaultApp"] = false
                    result
                }
            }

            notificationChannelClass
                .getDeclaredMethod("setBlockable", Boolean::class.javaPrimitiveType)
                .let { xposedModule.hook(it) }
                .intercept { chain ->
                    chain.args[0] = true
                    chain.proceed()
                }

            notificationChannelClass
                .getDeclaredMethod("setImportanceLockedByOEM", Boolean::class.javaPrimitiveType)
                .let { xposedModule.hook(it) }
                .intercept { chain ->
                    chain.args[0] = false
                    chain.proceed()
                }

            notificationChannelClass
                .getDeclaredMethod("setImportanceLockedByCriticalDeviceFunction", Boolean::class.javaPrimitiveType)
                .let { xposedModule.hook(it) }
                .intercept { chain ->
                    chain.args[0] = false
                    chain.proceed()
                }




//            hookAllConstructors(notificationChannelClass, object : XC_MethodHook() {
//                override fun afterHookedMethod(param: MethodHookParam) {
//                    setBooleanField(param.thisObject, "mBlockableSystem", true)
//                    setBooleanField(param.thisObject, "mImportanceLockedByOEM", false)
//                    setBooleanField(param.thisObject, "mImportanceLockedDefaultApp", false)
//                }
//            })
//
//            findAndHookMethod(
//                notificationChannelClass,
//                "setBlockable",
//                Boolean::class.javaPrimitiveType,
//                object : XC_MethodHook() {
//                    override fun beforeHookedMethod(param: MethodHookParam) {
//                        param.args[0] = true
//                    }
//                }
//            )
//
//            val unlockHook = object : XC_MethodHook() {
//                override fun beforeHookedMethod(param: MethodHookParam) {
//                    param.args[0] = false
//                }
//            }
//
//            findAndHookMethod(
//                notificationChannelClass,
//                "setImportanceLockedByOEM",
//                Boolean::class.javaPrimitiveType,
//                unlockHook
//            )
//
//            findAndHookMethod(
//                notificationChannelClass,
//                "setImportanceLockedByCriticalDeviceFunction",
//                Boolean::class.javaPrimitiveType,
//                unlockHook
//            )
        } catch (t: Throwable) {
            xposedModule.log(Log.ERROR, TAG, "setBlockableNotificationChannel", t)
        }
    }


    /*
    fun setMaxNeverKilledAppNum(loadPackageParam: LoadPackageParam, num: Int) {
        if (loadPackageParam.packageName != Package.ANDROID) return
        try {
            val clazz = findClass(
                "com.android.server.am.DynamicHiddenApp",
                loadPackageParam.classLoader
            )
            setStaticIntField(clazz, "MAX_NEVERKILLEDAPP_NUM", num)
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }
    }

    // 解除国行/港版对 GMS（含 FCM 推送）的网络限制
    fun liftFcmNetworkLimit(loadPackageParam: LoadPackageParam) {
        if (loadPackageParam.packageName != Package.ANDROID) return
        val clazz = findClassIfExists(
            "com.android.server.alarm.GmsAlarmManager",
            loadPackageParam.classLoader
        ) ?: return
        try {
            hookAllConstructors(clazz, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    setBooleanField(param.thisObject, "isChinaMode", false)
                    setBooleanField(param.thisObject, "isHongKongMode", false)
                }
            })
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }
    }

    // 禁用每 72 小时验证锁屏密码
    fun disablePinVerifyPer72h(loadPackageParam: LoadPackageParam) {
        if (loadPackageParam.packageName != Package.ANDROID) return
        try {
            hookAllMethods(
                findClass(
                    "com.android.server.locksettings.LockSettingsStrongAuth",
                    loadPackageParam.classLoader
                ),
                "rescheduleStrongAuthTimeoutAlarm",
                DO_NOTHING
            )
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }
    }

    // 移除充电器时禁止亮屏
    // PowerManagerService.updateIsPoweredLocked 在插拔充电器时会调用 wakePowerGroupLocked 点亮屏幕，
    // 唤醒理由字符串为 "android.server.power:PLUGGED:" + mIsPowered。
    // 拔出充电器时 mIsPowered 为 false，拦截该次唤醒即可（插入仍正常亮屏）。
    fun disableScreenWakeOnPowerUnplugged(loadPackageParam: LoadPackageParam) {
        if (loadPackageParam.packageName != Package.ANDROID) return
        try {
            hookAllMethods(
                findClass(
                    "com.android.server.power.PowerManagerService",
                    loadPackageParam.classLoader
                ),
                "wakePowerGroupLocked",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val details = param.args.getOrNull(3) as? String ?: return
                        if (details == "android.server.power:PLUGGED:false") {
                            param.result = null
                        }
                    }
                }
            )
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }
    }
     */
}
