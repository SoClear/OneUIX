package io.github.soclear.oneuix.hook.systemui.powermenu

import com.samsung.android.globalactions.presentation.SamsungGlobalActions
import com.samsung.android.globalactions.presentation.SamsungGlobalActionsPresenter
import com.samsung.android.globalactions.presentation.viewmodel.ActionViewModel
import com.samsung.android.globalactions.presentation.viewmodel.ViewType
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import io.github.soclear.oneuix.common.Package
import io.github.soclear.oneuix.common.PowerMenuAction
import io.github.soclear.oneuix.hook.util.callMethod
import io.github.soclear.oneuix.hook.util.get
import io.github.soclear.oneuix.hook.util.xlog

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
        val viewModelFactory = globalActions["mViewModelFactory"]
        viewModelFactory?.callMethod(
            "createActionViewModel",
            globalActions,
            actionName
        ) as ActionViewModel
    }

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun hookPowerMenuActions(
        configuredActions: List<PowerMenuAction>,
    ) {
        val normalizedActions = PowerMenuAction.normalize(configuredActions)
        val visibleActions = normalizedActions.filter { it.visible }
        if (param.packageName != Package.SYSTEMUI || installed) {
            return
        }
        try {
            val presenterClass = param.classLoader.loadClass(
                "com.samsung.android.globalactions.presentation.SamsungGlobalActionsPresenter"
            )
            val createActionsMethod = presenterClass.getDeclaredMethod("createActions")
            xposedModule.hook(createActionsMethod).intercept { chain ->
                val result = chain.proceed()
                val presenter = chain.thisObject as? SamsungGlobalActionsPresenter
                if (presenter != null) {
                    PowerMenuAction.DEFAULT_ORDER.forEach(presenter::clearActions)
                    visibleActions.forEachIndexed { index, action ->
                        runCatching {
                            presenter.addAction(createAction(presenter, action.name, index))
                        }.onFailure {
                            xlog(it)
                        }
                    }
                }
                result
            }
            installed = true
        } catch (t: Throwable) {
            xlog(t)
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
