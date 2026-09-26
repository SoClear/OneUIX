package io.github.soclear.oneuix

import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import io.github.soclear.oneuix.ui.PreferenceStorage
import io.github.soclear.oneuix.ui.XposedPreferenceStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object XposedServiceManager {

    private val storage = MutableStateFlow<PreferenceStorage?>(null)
    val preferences = storage.asStateFlow()

    init {
        XposedServiceHelper.registerListener(object : XposedServiceHelper.OnServiceListener {
            override fun onServiceBind(service: XposedService) {
                storage.value = XposedPreferenceStorage(service)
            }

            override fun onServiceDied(service: XposedService) {
                storage.update { current ->
                    if ((current as? XposedPreferenceStorage)?.service === service) null else current
                }
            }
        })
    }
}
