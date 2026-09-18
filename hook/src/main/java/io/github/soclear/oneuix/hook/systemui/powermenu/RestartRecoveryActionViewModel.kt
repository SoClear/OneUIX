package io.github.soclear.oneuix.hook.systemui.powermenu

import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.samsung.android.globalactions.presentation.SamsungGlobalActions
import com.samsung.android.globalactions.presentation.viewmodel.ActionInfo
import com.samsung.android.globalactions.presentation.viewmodel.ActionViewModel
import com.samsung.android.globalactions.presentation.viewmodel.ViewType
import io.github.soclear.oneuix.RebootActivity
import io.github.soclear.oneuix.common.BuildConfig
import io.github.soclear.oneuix.common.PowerMenuAction
import io.github.soclear.oneuix.hook.R
import io.github.soclear.oneuix.hook.util.currentApplication
import io.github.soclear.oneuix.common.R as CommonR

class RestartRecoveryActionViewModel(
    private val globalActions: SamsungGlobalActions,
) : ActionViewModel {
    private val actionInfo = ActionInfo().apply {
        val context = currentApplication()
        name = PowerMenuAction.RESTART_RECOVERY
        viewType = ViewType.CENTER_ICON_3P_VIEW
        icon = R.drawable.ic_restart_recovery
        label = context.getString(CommonR.string.restartRecovery)
    }

    override fun getActionInfo(): ActionInfo = actionInfo

    override fun onPress() {
        if (!globalActions.isActionConfirming()) {
            globalActions.confirmAction(this)
            return
        }

        globalActions.dismissDialog(false)
        Handler(Looper.getMainLooper()).postDelayed({
            val intent = Intent("recovery").apply {
                setClassName(BuildConfig.MODULE_APPLICATION_ID, RebootActivity::class.java.name)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            currentApplication().startActivity(intent)
        }, 100L)
    }

    override fun setActionInfo(actionInfo: ActionInfo) {}
}
