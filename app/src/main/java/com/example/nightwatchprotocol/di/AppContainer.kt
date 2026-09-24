package com.example.nightwatchprotocol.di

import android.content.Context
import com.example.nightwatchprotocol.data.auth.AuthRepository
import com.example.nightwatchprotocol.data.auth.FirebaseAuthRepository
import com.example.nightwatchprotocol.data.quiz.FirestoreQuizResultRepository
import com.example.nightwatchprotocol.data.quiz.LocalQuizContentRepository
import com.example.nightwatchprotocol.data.quiz.QuizContentRepository
import com.example.nightwatchprotocol.data.quiz.QuizResultRepository
import com.example.nightwatchprotocol.data.session.SessionPreferences
import com.example.nightwatchprotocol.data.session.sessionDataStore
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

interface AppContainer {
    val authRepository: AuthRepository
    val sessionPreferences: SessionPreferences
    val quizContentRepository: QuizContentRepository
    val quizResultRepository: QuizResultRepository
}

class DefaultAppContainer(context: Context) : AppContainer {
    override val authRepository: AuthRepository = FirebaseAuthRepository(FirebaseAuth.getInstance())
    override val sessionPreferences: SessionPreferences = SessionPreferences(context.sessionDataStore)
    override val quizContentRepository: QuizContentRepository = LocalQuizContentRepository(context)
    override val quizResultRepository: QuizResultRepository = FirestoreQuizResultRepository(FirebaseFirestore.getInstance())
}
