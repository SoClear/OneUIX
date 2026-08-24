package io.github.soclear.oneuix.service

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

object SoftRestartController {
    private const val RESTART_COMMAND =
        "exec /system/bin/setprop ctl.restart zygote"

    suspend fun softRestartLikeKernelSu(context: Context): Boolean = withContext(Dispatchers.IO) {
        val scheduled = AutoStartJobService.schedule(
            context.applicationContext,
            AutoStartJobService.REASON_SOFT_RESTART,
        )
        if (!scheduled) {
            InteractionDiagnostics.log("SoftRestart", "failed=autoStartJobSchedule")
        }
        try {
            val process = ProcessBuilder("su", "-c", RESTART_COMMAND)
                .redirectErrorStream(true)
                .start()
            if (!process.waitFor(30, TimeUnit.SECONDS)) {
                process.destroy()
                if (scheduled) AutoStartJobService.cancel(context.applicationContext)
                InteractionDiagnostics.log("SoftRestart", "failed=rootRequestTimeout")
                false
            } else {
                val succeeded = process.exitValue() == 0
                if (!succeeded && scheduled) {
                    AutoStartJobService.cancel(context.applicationContext)
                }
                succeeded
            }
        } catch (error: Throwable) {
            if (scheduled) AutoStartJobService.cancel(context.applicationContext)
            InteractionDiagnostics.log(
                "SoftRestart",
                "failed=${error.javaClass.simpleName}",
            )
            false
        }
    }
}
