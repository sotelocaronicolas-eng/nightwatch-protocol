package com.example.nightwatchprotocol.data.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.nightwatchprotocol.NightWatchApplication
import kotlinx.coroutines.flow.first

/** Runs once a day per enabled reminder and posts it, but only if the user is still signed in and allowed it. */
class ReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val kind = inputData.getString(KEY_KIND)?.let { name -> ReminderKind.entries.firstOrNull { it.name == name } }
            ?: return Result.failure()
        val container = (applicationContext as NightWatchApplication).container

        val stillEnabled = container.userPreferences.settings.first()[kind.setting]
        if (!stillEnabled) {
            container.reminderScheduler.cancel(kind)
            return Result.success()
        }
        if (container.authRepository.currentUserUid != null && container.notificationPermission.isGranted()) {
            ReminderNotifier.post(applicationContext, kind)
        }
        return Result.success()
    }

    companion object {
        const val KEY_KIND = "reminder_kind"
    }
}
