package com.example.nightwatchprotocol.di

import android.content.Context
import com.example.nightwatchprotocol.data.auth.AuthRepository
import com.example.nightwatchprotocol.data.auth.FirebaseAuthRepository
import com.example.nightwatchprotocol.data.session.SessionPreferences
import com.example.nightwatchprotocol.data.session.sessionDataStore
import com.google.firebase.auth.FirebaseAuth

interface AppContainer {
    val authRepository: AuthRepository
    val sessionPreferences: SessionPreferences
}

class DefaultAppContainer(context: Context) : AppContainer {
    override val authRepository: AuthRepository = FirebaseAuthRepository(FirebaseAuth.getInstance())
    override val sessionPreferences: SessionPreferences = SessionPreferences(context.sessionDataStore)
}
