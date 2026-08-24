package io.github.soclear.oneuix.service

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.os.PersistableBundle
import io.github.soclear.oneuix.ui.dataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class AutoStartJobService : JobService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var runningJob: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        runningJob = serviceScope.launch {
            val reason = params.extras.getString(EXTRA_REASON, REASON_UNKNOWN)
            val result = runSelectedApps(reason)
            InteractionDiagnostics.recordAutoStart(this@AutoStartJobService, result)
            jobFinished(params, false)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        runningJob?.cancel()
        runningJob = null
        // This is a one-shot launch request. Never let JobScheduler repeat it after
        // an interruption, otherwise the same apps could unexpectedly open twice.
        return false
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private suspend fun runSelectedApps(reason: String): String {
        val setting = dataStore.data.first().interaction
        if (!setting.autoStartEnabled) return "reason=$reason skipped=disabled"
        if (setting.autoStartPackages.isEmpty()) return "reason=$reason skipped=noSelection"

        var launched = 0
        var failed = 0
        setting.autoStartPackages.sorted().forEach { packageName ->
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            val component = launchIntent?.component
            if (component == null) {
                failed += 1
                InteractionDiagnostics.log(
                    "AutoStart",
                    "package=$packageName skipped=noLauncherActivity",
                )
                return@forEach
            }
            val succeeded = launchAsRoot(component)
            if (succeeded) launched += 1 else failed += 1
            InteractionDiagnostics.log(
                "AutoStart",
                "package=$packageName launched=$succeeded",
            )
            delay(APP_LAUNCH_INTERVAL_MILLIS)
        }
        return "reason=$reason selected=${setting.autoStartPackages.size} " +
            "launched=$launched failed=$failed"
    }

    private fun launchAsRoot(component: ComponentName): Boolean {
        val flattenedComponent = component.flattenToShortString()
        if (!SAFE_COMPONENT.matches(flattenedComponent)) return false
        return try {
            val command =
                "exec /system/bin/am start --user 0 -n '$flattenedComponent'"
            val process = ProcessBuilder("su", "-c", command)
                .redirectErrorStream(true)
                .start()
            if (!process.waitFor(ROOT_COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroy()
                false
            } else {
                process.exitValue() == 0
            }
        } catch (error: Throwable) {
            InteractionDiagnostics.log(
                "AutoStart",
                "package=${component.packageName} failed=${error.javaClass.simpleName}",
            )
            false
        }
    }

    companion object {
        const val REASON_SYSTEM_BOOT = "systemBoot"
        const val REASON_SOFT_RESTART = "softRestart"
        private const val REASON_UNKNOWN = "unknown"
        private const val EXTRA_REASON = "reason"
        private const val JOB_ID = 0x4F4E4558
        private const val START_DELAY_MILLIS = 15_000L
        private const val APP_LAUNCH_INTERVAL_MILLIS = 1_500L
        private const val ROOT_COMMAND_TIMEOUT_SECONDS = 15L
        private val SAFE_COMPONENT = Regex("^[A-Za-z0-9_.$]+/[A-Za-z0-9_.$]+$")

        fun schedule(context: Context, reason: String): Boolean {
            val extras = PersistableBundle().apply { putString(EXTRA_REASON, reason) }
            val job = JobInfo.Builder(
                JOB_ID,
                ComponentName(context, AutoStartJobService::class.java),
            )
                .setMinimumLatency(START_DELAY_MILLIS)
                .setOverrideDeadline(START_DELAY_MILLIS + 45_000L)
                .setPersisted(true)
                .setExtras(extras)
                .build()
            return context.getSystemService(JobScheduler::class.java).schedule(job) ==
                JobScheduler.RESULT_SUCCESS
        }

        fun cancel(context: Context) {
            context.getSystemService(JobScheduler::class.java).cancel(JOB_ID)
        }
    }
}
