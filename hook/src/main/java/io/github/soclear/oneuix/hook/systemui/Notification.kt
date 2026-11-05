package io.github.soclear.oneuix.hook.systemui

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.Context
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import io.github.soclear.oneuix.common.ONE_UI_VERSION
import io.github.soclear.oneuix.hook.util.afterAttachTry
import io.github.soclear.oneuix.hook.util.reflect
import io.github.soclear.oneuix.hook.util.xlog

@SuppressLint("PrivateApi")
object Notification {
    context(xposedModule: XposedModule)
    fun disableNotificationGrouping() = afterAttachTry {
        classLoader
            .loadClass("android.service.notification.StatusBarNotification")
            .getDeclaredMethod("isGroup")
            .let { xposedModule.hook(it) }
            .intercept { false }
        // isGroup()=false lets children show individually, but the group summary
        // (FLAG_GROUP_SUMMARY) leaks through as a standalone entry whose dismissal
        // clears all the app's notifications. Filter it out of the shade list
        // while keeping it in NotifCollection so lifecycle events stay consistent.
        val notificationEntryClass = classLoader
            .loadClass("com.android.systemui.statusbar.notification.collection.NotificationEntry")
        xposedModule.hook(
            classLoader
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
                val sbn = entry.reflect["mSbn"] ?: return@intercept result
                val notification = sbn.reflect.call("getNotification") ?: return@intercept result
                if (notification.reflect.call("isGroupSummary") as Boolean) {
                    true
                } else {
                    result
                }
            } catch (t: Throwable) {
                xlog(t)
                result
            }
        }
    }

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun hideOngoingActivityMedia(packages: Set<String>) = afterAttachTry(packages.isNotEmpty()) {
        // Only change visibility for Samsung's synthetic MediaOngoingActivity notification.
        // Keep the shared media pipeline and all player instances intact for QS playback.
        val listenerClass = param.classLoader.loadClass(
            $$"com.android.systemui.statusbar.phone.ongoingactivity.OngoingActivityController$mediaPanelVisibilityListener$1"
        )
        val method = listenerClass.getDeclaredMethod(
            "onMediaVisibilityChanged",
            Boolean::class.javaPrimitiveType
        )
        xposedModule.hook(method).intercept { chain ->
            try {
                if (chain.args.firstOrNull() == true) {
                    val controller = chain.thisObject.reflect[$$"this$0"]
                    val mediaHost = controller?.reflect?.get("mediaHost")
                    val mediaData = if (ONE_UI_VERSION >= 80500) {
                        // 8.5 shares and sorts media data across surfaces. The latest
                        // notification may belong to a different player.
                        val repository = mediaHost?.reflect?.get("mSecMediaDataRepository")
                        val data = repository?.reflect?.call("getMediaData") as? Map<*, *>
                        data?.values?.firstOrNull { value ->
                            value != null &&
                                    (value.reflect["packageName"] as? String) in packages
                        }
                    } else {
                        val currentData = mediaHost?.reflect?.get("mCurrentMediaData")
                        currentData?.reflect?.get("data")
                    }
                    val packageName = mediaData?.reflect?.get("packageName") as? String
                    if (packageName in packages) {
                        // Notifications survive a SystemUI restart, while isMediaVisible
                        // starts false. Cancel stale entries even if the callback returns early.
                        val context = controller?.reflect?.get("mContext") as? Context
                        context?.getSystemService(NotificationManager::class.java)
                            ?.cancel(12030705)
                        // Let the original callback cancel an existing live activity
                        // and update its own isMediaVisible state normally.
                        val newArgs = chain.args.toTypedArray()
                        newArgs[0] = false
                        return@intercept chain.proceed(newArgs)
                    }
                }
            } catch (t: Throwable) {
                xlog(t)
            }
            chain.proceed()
        }
    }

    context(xposedModule: XposedModule)
    fun autoExpandNotifications() = afterAttachTry {
        xposedModule.hook(
            classLoader
                .loadClass("com.android.systemui.statusbar.notification.row.ExpandableNotificationRow")
                .getDeclaredMethod("isExpanded", Boolean::class.java)
        ).intercept { chain ->
            val result = chain.proceed()
            try {
                val row = chain.thisObject
                // 确保非分组展开开关被打开
                row.reflect["mEnableNonGroupedNotificationExpand"] = true
                // 1. 锁屏敏感隐私校验
                val shouldShowPublic = row.reflect.call("shouldShowPublic") as Boolean
                if (shouldShowPublic) {
                    // 锁屏隐藏敏感内容时不展开
                    return@intercept result
                }
                // 2. 锁屏状态与 keyguard 约束校验
                val onKeyguard = row.reflect["mOnKeyguard"] as Boolean
                val allowOnKeyguard = chain.args[0] as Boolean
                if (onKeyguard && !allowOnKeyguard) {
                    return@intercept result
                }
                // 3. 用户手动折叠校验（若用户手动折叠了该单条通知，则不强制展开）
                val hasUserChanged = row.reflect["mHasUserChangedExpansion"] as Boolean
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
    }
}
