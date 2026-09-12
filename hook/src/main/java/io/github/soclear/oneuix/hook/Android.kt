package io.github.soclear.oneuix.hook

import android.app.NotificationChannel
import android.os.Bundle
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement.DO_NOTHING
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedBridge.hookAllConstructors
import de.robv.android.xposed.XposedBridge.hookAllMethods
import de.robv.android.xposed.XposedHelpers.findAndHookMethod
import de.robv.android.xposed.XposedHelpers.findClass
import de.robv.android.xposed.XposedHelpers.findClassIfExists
import de.robv.android.xposed.XposedHelpers.setBooleanField
import de.robv.android.xposed.XposedHelpers.setStaticIntField
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import io.github.soclear.oneuix.common.Package


object Android {
    fun disableWritingToolkitGlobally(loadPackageParam: LoadPackageParam) {
        if (loadPackageParam.packageName != Package.ANDROID) return

        val galaxyAiRestrictionsPackage = "com.samsung.android.knox.galaxyai"
        val writingToolkitKey = "key_writing_toolkit"
        val grayoutKey = "grayout"

        try {
            val proxyClass = findClassIfExists(
                "com.android.server.enterprise.EDMProxyService",
                loadPackageParam.classLoader
            ) ?: return

            hookAllMethods(
                proxyClass,
                "getApplicationRestrictions",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (param.hasThrowable()) return
                        if (param.args.getOrNull(0) != galaxyAiRestrictionsPackage) return

                        val restrictions = Bundle(param.result as? Bundle ?: Bundle.EMPTY)
                        val writingToolkit = Bundle(
                            restrictions.getBundle(writingToolkitKey) ?: Bundle.EMPTY
                        )
                        writingToolkit.putBoolean(grayoutKey, true)
                        restrictions.putBundle(writingToolkitKey, writingToolkit)
                        param.result = restrictions
                    }
                }
            )
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }
    }

    fun setBlockableNotificationChannel() {
        try {
            val notificationChannelClass = NotificationChannel::class.java

            hookAllConstructors(notificationChannelClass, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    setBooleanField(param.thisObject, "mBlockableSystem", true)
                    setBooleanField(param.thisObject, "mImportanceLockedByOEM", false)
                    setBooleanField(param.thisObject, "mImportanceLockedDefaultApp", false)
                }
            })

            findAndHookMethod(
                notificationChannelClass,
                "setBlockable",
                Boolean::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        param.args[0] = true
                    }
                }
            )

            val unlockHook = object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    param.args[0] = false
                }
            }

            findAndHookMethod(
                notificationChannelClass,
                "setImportanceLockedByOEM",
                Boolean::class.javaPrimitiveType,
                unlockHook
            )

            findAndHookMethod(
                notificationChannelClass,
                "setImportanceLockedByCriticalDeviceFunction",
                Boolean::class.javaPrimitiveType,
                unlockHook
            )
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }
    }


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
}
