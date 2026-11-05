package com.example.nightwatchprotocol.data.notifications

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.nightwatchprotocol.MainActivity
import com.example.nightwatchprotocol.R
import com.example.nightwatchprotocol.data.common.ContextTextResolver
import com.example.nightwatchprotocol.data.common.TextResolver
import com.example.nightwatchprotocol.data.common.resolve
import com.example.nightwatchprotocol.data.language.AppLanguage

/** The three texts of a reminder, already in one language. */
data class ReminderTexts(val channelName: String, val title: String, val text: String)

/** The texts of [kind] with [resolver]; pure, so the language switch is tested without Android. */
fun reminderTexts(kind: ReminderKind, resolver: TextResolver): ReminderTexts =
    ReminderTexts(kind.channelName.resolve(resolver), kind.title.resolve(resolver), kind.text.resolve(resolver))

/**
 * Creates the notification channels and posts the local reminders, in the language the app is in (D11).
 *
 * [language] is read when the channel is created and when the notification is posted, never earlier. The application [Context] a
 * Worker gets does not follow AppCompat's language before Android 13, so the text is looked up with an explicit locale
 * ([ContextTextResolver]); null uses the context as it is (the phone's language).
 */
object ReminderNotifier {

    /**
     * Idempotent; the channels must exist before a notification is posted. Calling it again with a new language renames the channels
     * (Android updates the name of a channel that already exists with the same id), so it runs at every start of the app and Activity.
     */
    fun createChannels(context: Context, language: () -> AppLanguage? = { null }) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val resolver = ContextTextResolver(context, language)
        ReminderKind.entries.forEach { kind ->
            manager.createNotificationChannel(
                NotificationChannel(kind.channelId, reminderTexts(kind, resolver).channelName, NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
    }

    // The caller checks NotificationPermission.isGranted() first.
    @SuppressLint("MissingPermission")
    fun post(context: Context, kind: ReminderKind, language: () -> AppLanguage? = { null }) {
        val openApp = PendingIntent.getActivity(
            context,
            kind.notificationId,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val texts = reminderTexts(kind, ContextTextResolver(context, language))
        val notification = NotificationCompat.Builder(context, kind.channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(texts.title)
            .setContentText(texts.text)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(context).notify(kind.notificationId, notification)
    }
}
