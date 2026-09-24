package com.example.nightwatchprotocol

import android.app.Application
import com.example.nightwatchprotocol.di.AppContainer
import com.example.nightwatchprotocol.di.DefaultAppContainer
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class NightWatchApplication : Application() {
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)

        val shouldRemainSignedIn = runBlocking { container.sessionPreferences.rememberSessionFlow.first() }
        if (!shouldRemainSignedIn) {
            FirebaseAuth.getInstance().signOut()
        }
    }
}
