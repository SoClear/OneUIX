package io.github.soclear.oneuix.hook.systemui

import android.content.Intent
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.view.View
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.samsung.android.globalactions.presentation.SamsungGlobalActions
import com.samsung.android.globalactions.presentation.SamsungGlobalActionsPresenter
import com.samsung.android.globalactions.presentation.viewmodel.ActionInfo
import com.samsung.android.globalactions.presentation.viewmodel.ActionViewModel
import com.samsung.android.globalactions.presentation.viewmodel.ViewType
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import io.github.soclear.oneuix.RebootActivity
import io.github.soclear.oneuix.common.BuildConfig
import io.github.soclear.oneuix.common.Package
import io.github.soclear.oneuix.common.PowerMenuAction
import io.github.soclear.oneuix.hook.R
import io.github.soclear.oneuix.hook.util.currentContext
import io.github.soclear.oneuix.hook.util.reflect
import io.github.soclear.oneuix.hook.util.xlog
import io.github.soclear.oneuix.common.R as CommonR

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

    private class CustomActionSpec(
        @param:DrawableRes val icon: Int,
        @param:StringRes val label: Int,
        val execute: () -> Unit,
    )

    private val customActions = mapOf(
        PowerMenuAction.RESTART_SYSTEMUI to CustomActionSpec(
            icon = R.drawable.ic_restart_system_ui,
            label = CommonR.string.restartSystemUI,
            execute = { Process.killProcess(Process.myPid()) },
        ),
        PowerMenuAction.RESTART_RECOVERY to CustomActionSpec(
            icon = R.drawable.ic_restart_recovery,
            label = CommonR.string.restartRecovery,
            execute = { startRebootActivity("recovery") },
        ),
        PowerMenuAction.RESTART_DOWNLOAD to CustomActionSpec(
            icon = R.drawable.ic_restart_download,
            label = CommonR.string.restartDownload,
            execute = { startRebootActivity("download") },
        ),
        PowerMenuAction.SOFT_REBOOT to CustomActionSpec(
            icon = R.drawable.ic_soft_reboot,
            label = CommonR.string.softReboot,
            execute = { startRebootActivity(PowerMenuAction.SOFT_REBOOT) },
        ),
    )

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    fun hookPowerMenuActions(
        configuredActions: List<PowerMenuAction>,
    ) {
        if (param.packageName != Package.SYSTEMUI || installed) return
        val visibleActions = PowerMenuAction.normalize(configuredActions).filter { it.visible }
        try {
            val presenterClass = param.classLoader.loadClass(
                "com.samsung.android.globalactions.presentation.SamsungGlobalActionsPresenter"
            )
            val createActionsMethod = presenterClass.getDeclaredMethod("createActions")
            if (visibleActions.any { it.name in customActions }) hookCustomActionIcons()
            xposedModule.hook(createActionsMethod).intercept { chain ->
                val result = chain.proceed()
                val presenter = chain.thisObject as? SamsungGlobalActionsPresenter ?: return@intercept result
                val moduleResources by lazy(LazyThreadSafetyMode.NONE) {
                    currentContext().packageManager.getResourcesForApplication(BuildConfig.MODULE_APPLICATION_ID)
                }

                PowerMenuAction.DEFAULT_ORDER.forEach(presenter::clearActions)
                visibleActions.forEachIndexed { index, action ->
                    runCatching {
                        val model = customActions[action.name]?.let { spec ->
                            CustomActionViewModel(presenter, moduleResources, action.name, spec)
                        } ?: createSystemAction(presenter, action.name)
                        model.getActionInfo().viewType = centerViewTypes.getOrElse(index) {
                            ViewType.CENTER_ICON_CUSTOM_VIEW
                        }
                        presenter.addAction(model)
                    }.onFailure {
                        xlog(it)
                    }
                }
                result
            }
            installed = true
        } catch (t: Throwable) {
            xlog(t)
        }
    }

    context(xposedModule: XposedModule, param: XposedModuleInterface.PackageReadyParam)
    private fun hookCustomActionIcons() {
        val itemClass = param.classLoader.loadClass(
            "com.samsung.android.globalactions.presentation.view.GlobalActionsContentItemView"
        )
        val resourceType = param.classLoader.loadClass(
            "com.samsung.android.globalactions.presentation.view.ResourceType"
        )
        val bind = itemClass.getDeclaredMethod("setViewAttrs", View::class.java, Boolean::class.javaPrimitiveType)
        val viewModel = itemClass.getDeclaredField("mViewModel").apply { isAccessible = true }
        val resourceFactory = itemClass.getDeclaredField("mResourceFactory").apply { isAccessible = true }
        val getResource = resourceFactory.type.getMethod("get", resourceType)
        val iconType = resourceType.getField("ID_ICON").get(null)
        xposedModule.hook(bind).intercept { chain ->
            val result = chain.proceed()
            val item = chain.thisObject
            val model = viewModel.get(item) as? CustomActionViewModel ?: return@intercept result
            val iconId = getResource.invoke(resourceFactory.get(item), iconType) as Int
            (chain.args[0] as View).findViewById<ImageView>(iconId)?.setImageDrawable(model.createIcon())
            result
        }
    }

    private fun createSystemAction(globalActions: SamsungGlobalActions, actionName: String): ActionViewModel =
        globalActions.reflect["mViewModelFactory"]?.reflect?.call(
            "createActionViewModel", globalActions, actionName,
        ) as ActionViewModel

    private fun startRebootActivity(action: String) {
        val intent = Intent(action).apply {
            setClassName(BuildConfig.MODULE_APPLICATION_ID, RebootActivity::class.java.name)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        currentContext().startActivity(intent)
    }

    private class CustomActionViewModel(
        private val globalActions: SamsungGlobalActions,
        private val resources: Resources,
        actionName: String,
        private val spec: CustomActionSpec,
    ) : ActionViewModel {
        private val actionInfo = ActionInfo().apply {
            name = actionName
            icon = 0
            label = resources.getString(spec.label)
        }

        fun createIcon(): Drawable? = resources.getDrawable(spec.icon, null)

        override fun getActionInfo(): ActionInfo = actionInfo

        override fun onPress() {
            if (!globalActions.isActionConfirming()) {
                globalActions.confirmAction(this)
                return
            }

            globalActions.dismissDialog(false)
            Handler(Looper.getMainLooper()).postDelayed(spec.execute, 100L)
        }

        override fun setActionInfo(actionInfo: ActionInfo) {}
    }
}
