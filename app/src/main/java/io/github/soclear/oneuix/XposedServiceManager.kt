package io.github.soclear.oneuix

import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

object XposedServiceManager {
    private val xposedServiceFlow = MutableStateFlow<XposedService?>(null)

    val xposedService: XposedService?
        get() = xposedServiceFlow.value

    val isModuleActive: Boolean
        get() = xposedServiceFlow.value != null

    init {
        XposedServiceHelper.registerListener(object : XposedServiceHelper.OnServiceListener {
            override fun onServiceBind(service: XposedService) {
                xposedServiceFlow.value = service
            }

            override fun onServiceDied(service: XposedService) {
                if (xposedServiceFlow.value == service) {
                    xposedServiceFlow.value = null
                }
            }
        })
    }

    suspend fun awaitXposedService(): XposedService {
        return xposedServiceFlow.filterNotNull().first()
    }
}
