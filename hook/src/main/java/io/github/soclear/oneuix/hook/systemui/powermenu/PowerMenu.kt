package io.github.soclear.oneuix.hook.systemui.powermenu

import com.samsung.android.globalactions.presentation.SamsungGlobalActions
import com.samsung.android.globalactions.presentation.SamsungGlobalActionsPresenter
import com.samsung.android.globalactions.presentation.viewmodel.ActionViewModel
import com.samsung.android.globalactions.presentation.viewmodel.ViewType
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import io.github.soclear.oneuix.common.Package
import io.github.soclear.oneuix.common.PowerMenuAction

object PowerMenu {
    private var installed = false
    private val centerViewTypes = listOf(
        ViewType.CENTER_ICON_1P_VIEW,
        ViewType.CENTER_ICON_2P_VIEW,
        ViewType.CENTER_ICON_3P_VIEW,
        ViewType.CENTER_ICON_4P_VIEW,
        ViewType.CENTER_ICON_5P_VIEW,
        ViewType.CENTER_ICON_6P_VIEW,
        ViewType.CENTER_ICON_7P_VIEW,
        ViewType.CENTER_ICON_8P_VIEW,
    )

    private fun systemAction(actionName: String): (SamsungGlobalActions) -> ActionViewModel = { globalActions ->
        val viewModelFactory = XposedHelpers.getObjectField(globalActions, "mViewModelFactory")
        XposedHelpers.callMethod(
            viewModelFactory,
            "createActionViewModel",
            globalActions,
            actionName
        ) as ActionViewModel
    }

    fun hookPowerMenuActions(
        loadPackageParam: LoadPackageParam,
        configuredActions: List<PowerMenuAction>,
    ) {
        val normalizedActions = PowerMenuAction.normalize(configuredActions)
        val visibleActions = normalizedActions.filter { it.visible }
        if (loadPackageParam.processName != Package.SYSTEMUI ||
            installed
        ) {
            return
        }
        val callback = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val presenter = param.thisObject as? SamsungGlobalActionsPresenter ?: return
                PowerMenuAction.DEFAULT_ORDER.forEach(presenter::clearActions)
                visibleActions.forEachIndexed { index, action ->
                    runCatching {
                        presenter.addAction(createAction(presenter, action.name, index))
                    }.onFailure {
                        XposedBridge.log(it)
                    }
                }
            }
        }
        try {
            XposedHelpers.findAndHookMethod(
                "com.samsung.android.globalactions.presentation.SamsungGlobalActionsPresenter",
                loadPackageParam.classLoader,
                "createActions",
                callback
            )
            installed = true
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }
    }

    private fun createAction(
        globalActions: SamsungGlobalActions,
        actionName: String,
        index: Int,
    ): ActionViewModel {
        val action = when (actionName) {
            PowerMenuAction.RESTART_SYSTEMUI -> RestartSystemUIActionViewModel(globalActions)
            PowerMenuAction.RESTART_RECOVERY -> RestartRecoveryActionViewModel(globalActions)
            PowerMenuAction.RESTART_DOWNLOAD -> RestartDownloadActionViewModel(globalActions)
            else -> systemAction(actionName)(globalActions)
        }
        action.getActionInfo().viewType = centerViewTypes.getOrElse(index) {
            ViewType.CENTER_ICON_CUSTOM_VIEW
        }
        return action
    }
}
