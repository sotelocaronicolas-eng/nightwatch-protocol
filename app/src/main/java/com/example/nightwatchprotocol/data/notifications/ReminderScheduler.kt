package com.example.nightwatchprotocol.data.notifications

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

interface ReminderScheduler {
    /** Plans the daily reminder. Calling it again for an already planned reminder keeps its current schedule. */
    fun schedule(kind: ReminderKind)

    fun cancel(kind: ReminderKind)
}

/**
 * Local daily reminders on WorkManager: a 24-hour periodic job whose first run is the next [ReminderKind] time.
 * Android may delay a run by some minutes (battery optimizations); no server is involved.
 */
class WorkManagerReminderScheduler(
    private val context: Context,
    private val clock: () -> Long = System::currentTimeMillis,
) : ReminderScheduler {

    override fun schedule(kind: ReminderKind) {
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(millisUntilNext(clock(), kind.hour, kind.minute), TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(ReminderWorker.KEY_KIND to kind.name))
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(kind.workName, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    override fun cancel(kind: ReminderKind) {
        WorkManager.getInstance(context).cancelUniqueWork(kind.workName)
    }
}
