package io.github.soclear.oneuix.hook.systemui

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement.returnConstant
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedBridge.hookAllConstructors
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.XposedHelpers.callMethod
import de.robv.android.xposed.XposedHelpers.findAndHookMethod
import de.robv.android.xposed.XposedHelpers.findClass
import de.robv.android.xposed.XposedHelpers.getObjectField
import de.robv.android.xposed.XposedHelpers.setBooleanField
import de.robv.android.xposed.XposedHelpers.setIntField
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import io.github.soclear.oneuix.data.ONE_UI_VERSION
import io.github.soclear.oneuix.data.Package

object Notification {
    fun setStatusBarMaxNotificationIcons(loadPackageParam: LoadPackageParam, max: Int) {
        if (loadPackageParam.packageName != Package.SYSTEMUI ||
            max < 0 ||
            Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM
        ) return

        if (ONE_UI_VERSION >= 80500) {
            try {
                findAndHookMethod(
                    "com.android.systemui.statusbar.phone.NotificationIconContainer",
                    loadPackageParam.classLoader,
                    "shouldForceOverflow",
                    Int::class.javaPrimitiveType,
                    Float::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            param.args[2] = max
                        }
                    }
                )
            } catch (t: Throwable) {
                XposedBridge.log(t)
            }

            try {
                hookAllConstructors(
                    findClass(
                        "com.android.systemui.statusbar.notification.icon.ui.viewmodel.NotificationIconContainerStatusBarViewModel",
                        loadPackageParam.classLoader
                    ),
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            setIntField(param.thisObject, "maxIcons", Int.MAX_VALUE)
                        }
                    }
                )
            } catch (t: Throwable) {
                XposedBridge.log(t)
            }
            return
        }
        try {
            findAndHookMethod(
                "com.android.systemui.statusbar.phone.NotificationIconContainer",
                loadPackageParam.classLoader,
                "shouldForceOverflow",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Float::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        param.args[3] = max
                    }
                }
            )

            findAndHookMethod(
                "com.android.systemui.statusbar.phone.NotificationIconContainer",
                loadPackageParam.classLoader,
                "initResources",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        setIntField(param.thisObject, "mMaxStaticIcons", Int.MAX_VALUE)
                    }
                }
            )
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }
    }

    fun disableNotificationGrouping(loadPackageParam: LoadPackageParam) {
        if (loadPackageParam.packageName != Package.SYSTEMUI) return
        try {
            findAndHookMethod(
                "android.service.notification.StatusBarNotification",
                loadPackageParam.classLoader,
                "isGroup",
                returnConstant(false)
            )
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }
        // isGroup()=false lets children show individually, but the group summary
        // (FLAG_GROUP_SUMMARY) leaks through as a standalone entry whose dismissal
        // clears all the app's notifications. Filter it out of the shade list
        // while keeping it in NotifCollection so lifecycle events stay consistent.
        try {
            findAndHookMethod(
                "com.android.systemui.statusbar.notification.collection.ShadeListBuilder",
                loadPackageParam.classLoader,
                "applyFilters",
                "com.android.systemui.statusbar.notification.collection.NotificationEntry",
                Long::class.javaPrimitiveType,
                List::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val entry = param.args[0] ?: return
                            val sbn = getObjectField(entry, "mSbn") ?: return
                            val notification = callMethod(sbn, "getNotification") ?: return
                            if (callMethod(notification, "isGroupSummary") as Boolean) {
                                param.result = true
                            }
                        } catch (t: Throwable) {
                            XposedBridge.log(t)
                        }
                    }
                }
            )
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }
    }

    fun hideOngoingActivityMedia(loadPackageParam: LoadPackageParam, packages: Set<String>) {
        if (loadPackageParam.packageName != Package.SYSTEMUI || packages.isEmpty()) return
        try {
            // Only change visibility for Samsung's synthetic MediaOngoingActivity notification.
            // Keep the shared media pipeline and all player instances intact for QS playback.
            findAndHookMethod(
                "com.android.systemui.statusbar.phone.ongoingactivity.OngoingActivityController\$mediaPanelVisibilityListener\$1",
                loadPackageParam.classLoader,
                "onMediaVisibilityChanged",
                Boolean::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        try {
                            if (param.args[0] != true) return
                            val controller = getObjectField(param.thisObject, "this\$0")
                            val mediaHost = getObjectField(controller, "mediaHost")
                            val mediaData = if (ONE_UI_VERSION >= 80500) {
                                // 8.5 shares and sorts media data across surfaces. The latest
                                // notification may belong to a different player.
                                val repository = getObjectField(mediaHost, "mSecMediaDataRepository")
                                val data = callMethod(repository, "getMediaData") as Map<*, *>
                                data.values.firstOrNull { value ->
                                    value != null &&
                                        (getObjectField(value, "packageName") as? String) in packages
                                }
                            } else {
                                val currentData = getObjectField(mediaHost, "mCurrentMediaData") ?: return
                                getObjectField(currentData, "data")
                            } ?: return
                            val packageName = getObjectField(mediaData, "packageName") as? String
                            if (packageName in packages) {
                                // Notifications survive a SystemUI restart, while isMediaVisible
                                // starts false. Cancel stale entries even if the callback returns early.
                                val context = getObjectField(controller, "mContext") as Context
                                context.getSystemService(NotificationManager::class.java)
                                    ?.cancel(12030705)
                                // Let the original callback cancel an existing live activity
                                // and update its own isMediaVisible state normally.
                                param.args[0] = false
                            }
                        } catch (t: Throwable) {
                            XposedBridge.log(t)
                        }
                    }
                }
            )
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }
    }

    fun autoExpandNotifications(loadPackageParam: LoadPackageParam) {
        if (loadPackageParam.packageName != Package.SYSTEMUI) return
        try {
            findAndHookMethod(
                "com.android.systemui.statusbar.notification.row.ExpandableNotificationRow",
                loadPackageParam.classLoader,
                "isExpanded",
                Boolean::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val row = param.thisObject
                            // 确保非分组展开开关被打开
                            setBooleanField(row, "mEnableNonGroupedNotificationExpand", true)
                            // 1. 锁屏敏感隐私校验
                            val shouldShowPublic = callMethod(row, "shouldShowPublic") as Boolean
                            if (shouldShowPublic) {
                                // 锁屏隐藏敏感内容时不展开
                                return
                            }
                            // 2. 锁屏状态与 keyguard 约束校验
                            val onKeyguard = XposedHelpers.getBooleanField(row, "mOnKeyguard")
                            val allowOnKeyguard = param.args[0] as Boolean
                            if (onKeyguard && !allowOnKeyguard) {
                                return
                            }
                            // 3. 用户手动折叠校验（若用户手动折叠了该单条通知，则不强制展开）
                            val hasUserChanged =
                                XposedHelpers.getBooleanField(row, "mHasUserChangedExpansion")
                            if (!hasUserChanged) {
                                param.setResult(true)
                            }
                        } catch (t: Throwable) {
                            XposedBridge.log(t)
                        }
                    }
                }
            )
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }
    }
}
