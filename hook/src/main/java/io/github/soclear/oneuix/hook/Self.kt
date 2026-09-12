package io.github.soclear.oneuix.hook


import android.content.Context
import de.robv.android.xposed.XC_MethodReplacement.returnConstant
import de.robv.android.xposed.XposedHelpers.findAndHookMethod
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import io.github.soclear.oneuix.common.BuildConfig
import io.github.soclear.oneuix.hook.util.PreferenceProvider

object Self {
    fun enableDataStoreFileSharing(loadPackageParam: LoadPackageParam) {
        if (loadPackageParam.packageName != BuildConfig.MODULE_APPLICATION_ID) return
        val callback = returnConstant(PreferenceProvider.getPreferenceFile())

        findAndHookMethod(
            "androidx.datastore.core.DeviceProtectedDataStoreFile",
            loadPackageParam.classLoader,
            "deviceProtectedDataStoreFile",
            Context::class.java,
            String::class.java,
            callback
        )
        findAndHookMethod(
            "androidx.datastore.DataStoreFile",
            loadPackageParam.classLoader,
            "dataStoreFile",
            Context::class.java,
            String::class.java,
            callback
        )
    }
}
