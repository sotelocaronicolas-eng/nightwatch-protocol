package com.example.nightwatchprotocol.data.notifications

class FakeReminderScheduler : ReminderScheduler {
    val scheduled = mutableListOf<ReminderKind>()
    val cancelled = mutableListOf<ReminderKind>()

    /** The reminders currently planned, as WorkManager unique work would hold them. */
    val active = mutableSetOf<ReminderKind>()

    override fun schedule(kind: ReminderKind) {
        scheduled += kind
        active += kind
    }

    override fun cancel(kind: ReminderKind) {
        cancelled += kind
        active -= kind
    }
}

class FakeNotificationPermission(
    var granted: Boolean = true,
    override var requiresRuntimePermission: Boolean = true,
) : NotificationPermission {
    override fun isGranted(): Boolean = granted
}
