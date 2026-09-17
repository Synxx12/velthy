package com.velthy.client.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Checks GitHub Releases in the background and raises [UpdateNotifier] when
 * something newer is out.
 *
 * ### Why WorkManager and not an in-process timer
 *
 * The app is deliberately serverless — there is no backend to push a "new
 * release" event — so the only way to hear about one while the app is closed is
 * to ask, on a schedule. An in-process coroutine timer would only run while the
 * app happened to be alive, which is exactly when the user is not waiting to be
 * told about an update. WorkManager is the one scheduler Android will run
 * across process death, Doze and reboots, so that is what carries it.
 *
 * ### Cost
 *
 * One conditional GET against GitHub's API (and the cheap redirect probe behind
 * it) per run, on a network that must be unmetered at the default cadence — a
 * release check is not worth a slice of someone's mobile data. Cadence is a
 * day: often enough that a critical fix is not silent for long, rare enough
 * that it is background noise rather than a poll.
 */
class UpdateCheckWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // check() reads BuildConfig.VERSION_NAME and returns null when the
        // installed build is already current, which is the common case.
        val info = AppUpdateChecker.check(force = true) ?: return Result.success()
        UpdateNotifier.notifyIfNeeded(applicationContext, info)
        return Result.success()
    }

    companion object {
        private const val UNIQUE_NAME = "velthy_update_check"

        /**
         * Registers the periodic check if it is not already scheduled.
         *
         * [ExistingPeriodicWorkPolicy.KEEP] so that whatever cadence and backoff
         * an already-running schedule has developed is left alone — re-enqueueing
         * on every launch would reset the interval, and a user who opens the app
         * often would then never actually reach a run.
         */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(1, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.UNMETERED)
                        .build(),
                )
                .build()

            runCatching {
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    UNIQUE_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request,
                )
            }
        }
    }
}
