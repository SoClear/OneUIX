package io.github.soclear.oneuix

import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper

object XposedServiceManager {

    @Volatile
    var xposedService: XposedService? = null
        private set

    init {
        XposedServiceHelper.registerListener(object : XposedServiceHelper.OnServiceListener {
            override fun onServiceBind(service: XposedService) {
                xposedService = service
            }

            override fun onServiceDied(service: XposedService) {
                if (xposedService == service) {
                    xposedService = null
                }
            }
        })
    }
}
