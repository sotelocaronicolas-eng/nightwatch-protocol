package com.example.nightwatchprotocol.di

import android.content.Context
import com.example.nightwatchprotocol.data.account.AccountRepository
import com.example.nightwatchprotocol.data.account.FirebaseAccountRepository
import com.example.nightwatchprotocol.data.auth.AuthRepository
import com.example.nightwatchprotocol.data.auth.FirebaseAuthRepository
import com.example.nightwatchprotocol.data.avatar.AvatarImageProcessor
import com.example.nightwatchprotocol.data.avatar.AvatarRepository
import com.example.nightwatchprotocol.data.avatar.BitmapAvatarImageProcessor
import com.example.nightwatchprotocol.data.avatar.FileAvatarCache
import com.example.nightwatchprotocol.data.profile.FirestoreProfileRepository
import com.example.nightwatchprotocol.data.profile.ProfileRepository
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
    val accountRepository: AccountRepository
    val profileRepository: ProfileRepository
    val avatarRepository: AvatarRepository
    val avatarImageProcessor: AvatarImageProcessor
}

class DefaultAppContainer(context: Context) : AppContainer {
    override val authRepository: AuthRepository = FirebaseAuthRepository(FirebaseAuth.getInstance())
    override val sessionPreferences: SessionPreferences = SessionPreferences(context.sessionDataStore)
    override val quizContentRepository: QuizContentRepository = LocalQuizContentRepository(context)
    override val quizResultRepository: QuizResultRepository = FirestoreQuizResultRepository(FirebaseFirestore.getInstance())
    override val accountRepository: AccountRepository = FirebaseAccountRepository(FirebaseAuth.getInstance())
    override val profileRepository: ProfileRepository = FirestoreProfileRepository(FirebaseFirestore.getInstance())
    override val avatarRepository: AvatarRepository =
        AvatarRepository(profileRepository, FileAvatarCache(context.filesDir))
    override val avatarImageProcessor: AvatarImageProcessor = BitmapAvatarImageProcessor(context.contentResolver)
}
