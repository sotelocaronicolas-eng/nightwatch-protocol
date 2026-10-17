package com.example.nightwatchprotocol

import android.app.Application
import com.example.nightwatchprotocol.data.notifications.ReminderNotifier
import com.example.nightwatchprotocol.di.AppContainer
import com.example.nightwatchprotocol.di.DefaultAppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class NightWatchApplication : Application() {
    lateinit var container: AppContainer
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)

        val shouldRemainSignedIn = runBlocking { container.sessionPreferences.rememberSessionFlow.first() }
        if (!shouldRemainSignedIn) {
            container.authRepository.signOut()
        }

        ReminderNotifier.createChannels(this)
        // Puts the planned reminders back in line with the switches (e.g. after the permission was revoked).
        appScope.launch { container.reminderCoordinator.sync() }
    }
}
