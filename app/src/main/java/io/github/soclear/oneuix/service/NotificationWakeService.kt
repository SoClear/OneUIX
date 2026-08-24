package io.github.soclear.oneuix.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import io.github.soclear.oneuix.BuildConfig
import io.github.soclear.oneuix.data.Preference
import io.github.soclear.oneuix.ui.dataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class NotificationWakeService : NotificationListenerService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val wakeController by lazy { ScreenWakeController(this) }
    @Volatile
    private var currentSetting: Preference.Interaction = Preference.Interaction()
    @Volatile
    private var preferenceReady = false

    override fun onCreate() {
        super.onCreate()
        serviceScope.launch {
            dataStore.data
                .catch {
                    InteractionDiagnostics.log(
                        "Wake",
                        "preferenceFlowFailed=${it.javaClass.simpleName}"
                    )
                }
                .collectLatest {
                    currentSetting = it.interaction
                    preferenceReady = true
                    InteractionDiagnostics.log(
                        "Wake",
                        "preferenceReady wakeOnNotification=${it.interaction.wakeOnNotification}"
                    )
                }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        InteractionDiagnostics.recordPermissionStatus(this, true)
    }

    override fun onListenerDisconnected() {
        InteractionDiagnostics.recordPermissionStatus(this, false)
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn?.notification ?: return
        val setting = currentSetting
        if (!preferenceReady || !setting.wakeOnNotification) {
            InteractionDiagnostics.log(
                "Wake",
                "skip=preferenceNotReadyOrDisabled ready=$preferenceReady"
            )
            return
        }

        val reason = when {
            sbn.packageName == BuildConfig.APPLICATION_ID -> "skip=self"
            notification.flags and Notification.FLAG_ONGOING_EVENT != 0 -> "skip=ongoing"
            notification.flags and Notification.FLAG_GROUP_SUMMARY != 0 -> "skip=groupSummary"
            setting.wakeOnlyWhenScreenOff && wakeController.isInteractive -> "skip=screenInteractive"
            else -> null
        }
        if (reason != null) {
            InteractionDiagnostics.log("Wake", "$reason package=${sbn.packageName}")
            return
        }

        val durationSeconds = setting.wakeDurationSeconds.coerceIn(1, 10)
        val woke = wakeController.wake(durationSeconds)
        InteractionDiagnostics.recordWake(
            this,
            "package=${sbn.packageName} screenOff=true notificationEligible=true " +
                "duration=${durationSeconds}s woke=$woke"
        )
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
