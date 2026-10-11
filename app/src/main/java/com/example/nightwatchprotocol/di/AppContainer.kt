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
import com.example.nightwatchprotocol.data.biometric.AndroidBiometricCapabilities
import com.example.nightwatchprotocol.data.biometric.BiometricCapabilities
import com.example.nightwatchprotocol.data.device.AndroidDeviceInfoProvider
import com.example.nightwatchprotocol.data.device.DeviceIdStore
import com.example.nightwatchprotocol.data.device.DeviceInfoProvider
import com.example.nightwatchprotocol.data.device.DeviceRepository
import com.example.nightwatchprotocol.data.device.DeviceSessionManager
import com.example.nightwatchprotocol.data.device.FirestoreDeviceRepository
import com.example.nightwatchprotocol.data.notifications.AndroidNotificationPermission
import com.example.nightwatchprotocol.data.notifications.NotificationPermission
import com.example.nightwatchprotocol.data.notifications.ReminderCoordinator
import com.example.nightwatchprotocol.data.notifications.ReminderScheduler
import com.example.nightwatchprotocol.data.notifications.WorkManagerReminderScheduler
import com.example.nightwatchprotocol.data.preferences.UserPreferences
import com.example.nightwatchprotocol.data.preferences.userDataStore
import com.example.nightwatchprotocol.data.profile.ContentResolverDataExportWriter
import com.example.nightwatchprotocol.data.profile.DataExportWriter
import com.example.nightwatchprotocol.data.profile.FirestoreProfileRepository
import com.example.nightwatchprotocol.data.profile.ProfileDataExporter
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
    val userPreferences: UserPreferences
    val biometricCapabilities: BiometricCapabilities
    val profileDataExporter: ProfileDataExporter
    val dataExportWriter: DataExportWriter
    val notificationPermission: NotificationPermission
    val reminderScheduler: ReminderScheduler
    val reminderCoordinator: ReminderCoordinator
    val deviceRepository: DeviceRepository
    val deviceIdStore: DeviceIdStore
    val deviceInfoProvider: DeviceInfoProvider
    val deviceSessionManager: DeviceSessionManager
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
    override val userPreferences: UserPreferences = UserPreferences(context.userDataStore)
    override val biometricCapabilities: BiometricCapabilities = AndroidBiometricCapabilities(context)
    override val profileDataExporter: ProfileDataExporter =
        ProfileDataExporter(accountRepository, profileRepository, quizResultRepository)
    override val dataExportWriter: DataExportWriter = ContentResolverDataExportWriter(context.contentResolver)
    override val notificationPermission: NotificationPermission = AndroidNotificationPermission(context)
    override val reminderScheduler: ReminderScheduler = WorkManagerReminderScheduler(context)
    override val reminderCoordinator: ReminderCoordinator =
        ReminderCoordinator(reminderScheduler, notificationPermission, userPreferences)
    override val deviceRepository: DeviceRepository = FirestoreDeviceRepository(FirebaseFirestore.getInstance())
    override val deviceIdStore: DeviceIdStore = DeviceIdStore(context.userDataStore)
    override val deviceInfoProvider: DeviceInfoProvider = AndroidDeviceInfoProvider()
    override val deviceSessionManager: DeviceSessionManager =
        DeviceSessionManager(authRepository, deviceRepository, deviceIdStore, deviceInfoProvider, avatarRepository)
}
