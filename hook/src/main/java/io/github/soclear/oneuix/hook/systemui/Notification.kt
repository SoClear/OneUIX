package io.github.soclear.oneuix.hook.systemui

import android.annotation.SuppressLint
import android.os.Build
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import io.github.soclear.oneuix.common.ONE_UI_VERSION
import io.github.soclear.oneuix.common.Package
import io.github.soclear.oneuix.hook.util.afterAttach
import io.github.soclear.oneuix.hook.util.callMethod
import io.github.soclear.oneuix.hook.util.get
import io.github.soclear.oneuix.hook.util.set
import io.github.soclear.oneuix.hook.util.xlog

@SuppressLint("PrivateApi")
object Notification {
    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun setStatusBarMaxNotificationIcons(max: Int)= afterAttach {
        if (param.packageName != Package.SYSTEMUI ||
            max < 0 ||
            Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM
        ) return@afterAttach

        if (ONE_UI_VERSION >= 80500) {
            try {
                val notificationIconContainerClass = param.classLoader
                    .loadClass("com.android.systemui.statusbar.phone.NotificationIconContainer")
                xposedModule.hook(
                    notificationIconContainerClass.getDeclaredMethod(
                        "shouldForceOverflow",
                        Int::class.javaPrimitiveType,
                        Float::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType
                    )
                ).intercept { chain ->
                    chain.args[2] = max
                    chain.proceed()
                }
            } catch (t: Throwable) {
                xlog(t)
            }

            try {
                param.classLoader
                    .loadClass("com.android.systemui.statusbar.notification.icon.ui.viewmodel.NotificationIconContainerStatusBarViewModel")
                    .declaredConstructors
                    .forEach {
                        xposedModule.hook(it).intercept { chain ->
                            val result = chain.proceed()
                            chain.thisObject["maxIcons"] = Int.MAX_VALUE
                            result
                        }
                    }
            } catch (t: Throwable) {
                xlog(t)
            }
            return@afterAttach
        }
        try {
            val notificationIconContainerClass = param.classLoader
                .loadClass("com.android.systemui.statusbar.phone.NotificationIconContainer")
            xposedModule.hook(
                notificationIconContainerClass.getDeclaredMethod(
                    "shouldForceOverflow",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Float::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType
                )
            ).intercept { chain ->
                chain.args[3] = max
                chain.proceed()
            }

            xposedModule.hook(
                notificationIconContainerClass.getDeclaredMethod("initResources")
            ).intercept { chain ->
                val result = chain.proceed()
                chain.thisObject["mMaxStaticIcons"] = Int.MAX_VALUE
                result
            }
        } catch (t: Throwable) {
            xlog(t)
        }
    }

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun disableNotificationGrouping() {
        if (param.packageName != Package.SYSTEMUI) return
        try {
            param.classLoader
                .loadClass("android.service.notification.StatusBarNotification")
                .getDeclaredMethod("isGroup")
                .let { xposedModule.hook(it) }
                .intercept { false }
        } catch (t: Throwable) {
            xlog(t)
        }
        // isGroup()=false lets children show individually, but the group summary
        // (FLAG_GROUP_SUMMARY) leaks through as a standalone entry whose dismissal
        // clears all the app's notifications. Filter it out of the shade list
        // while keeping it in NotifCollection so lifecycle events stay consistent.
        try {
            val notificationEntryClass = param.classLoader
                .loadClass("com.android.systemui.statusbar.notification.collection.NotificationEntry")
            xposedModule.hook(
                param.classLoader
                    .loadClass("com.android.systemui.statusbar.notification.collection.ShadeListBuilder")
                    .getDeclaredMethod(
                        "applyFilters",
                        notificationEntryClass,
                        Long::class.javaPrimitiveType,
                        List::class.java
                    )
            ).intercept { chain ->
                val result = chain.proceed()
                try {
                    val entry = chain.args[0] ?: return@intercept result
                    val sbn = entry["mSbn"] ?: return@intercept result
                    val notification = sbn.callMethod("getNotification") ?: return@intercept result
                    if (notification.callMethod("isGroupSummary") as Boolean) {
                        true
                    } else {
                        result
                    }
                } catch (t: Throwable) {
                    xlog(t)
                    result
                }
            }
        } catch (t: Throwable) {
            xlog(t)
        }
    }

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun hideOngoingActivityMedia(packages: Set<String>) {
        if (param.packageName != Package.SYSTEMUI || packages.isEmpty()) return
        try {
            val statusBarNotificationClass = param.classLoader
                .loadClass("android.service.notification.StatusBarNotification")
            xposedModule.hook(
                param.classLoader
                    .loadClass("com.android.systemui.media.controls.domain.pipeline.LegacyMediaDataManagerImpl")
                    .getDeclaredMethod(
                        "onNotificationAdded",
                        String::class.java,
                        statusBarNotificationClass
                    )
            ).intercept { chain ->
                try {
                    val sbn = chain.args[1] ?: return@intercept chain.proceed()
                    val packageName = sbn.callMethod("getPackageName") as String
                    if (packageName in packages) {
                        null
                    } else {
                        chain.proceed()
                    }
                } catch (t: Throwable) {
                    xlog(t)
                    chain.proceed()
                }
            }
        } catch (t: Throwable) {
            xlog(t)
        }
    }

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun autoExpandNotifications() {
        if (param.packageName != Package.SYSTEMUI) return
        try {
            xposedModule.hook(
                param.classLoader
                    .loadClass("com.android.systemui.statusbar.notification.row.ExpandableNotificationRow")
                    .getDeclaredMethod("isExpanded", Boolean::class.java)
            ).intercept { chain ->
                val result = chain.proceed()
                try {
                    val row = chain.thisObject
                    // 确保非分组展开开关被打开
                    row["mEnableNonGroupedNotificationExpand"] = true
                    // 1. 锁屏敏感隐私校验
                    val shouldShowPublic = row.callMethod("shouldShowPublic") as Boolean
                    if (shouldShowPublic) {
                        // 锁屏隐藏敏感内容时不展开
                        return@intercept result
                    }
                    // 2. 锁屏状态与 keyguard 约束校验
                    val onKeyguard = row["mOnKeyguard"] as Boolean
                    val allowOnKeyguard = chain.args[0] as Boolean
                    if (onKeyguard && !allowOnKeyguard) {
                        return@intercept result
                    }
                    // 3. 用户手动折叠校验（若用户手动折叠了该单条通知，则不强制展开）
                    val hasUserChanged = row["mHasUserChangedExpansion"] as Boolean
                    if (!hasUserChanged) {
                        true
                    } else {
                        result
                    }
                } catch (t: Throwable) {
                    xlog(t)
                    result
                }
            }
        } catch (t: Throwable) {
            xlog(t)
        }
    }
}
