package com.example.nightwatchprotocol.data.notifications

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.nightwatchprotocol.MainActivity
import com.example.nightwatchprotocol.R

/** Creates the notification channels and posts the local reminders. */
object ReminderNotifier {

    /** Idempotent; Android 8+ needs the channels to exist before a notification is posted. */
    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        ReminderKind.entries.forEach { kind ->
            manager.createNotificationChannel(
                NotificationChannel(kind.channelId, kind.channelName, NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
    }

    // The caller checks NotificationPermission.isGranted() first.
    @SuppressLint("MissingPermission")
    fun post(context: Context, kind: ReminderKind) {
        val openApp = PendingIntent.getActivity(
            context,
            kind.notificationId,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, kind.channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(kind.title)
            .setContentText(kind.text)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(context).notify(kind.notificationId, notification)
    }
}
