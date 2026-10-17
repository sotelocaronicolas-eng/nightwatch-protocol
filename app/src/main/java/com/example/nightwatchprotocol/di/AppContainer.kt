package com.example.nightwatchprotocol.di

import android.content.Context
import com.example.nightwatchprotocol.BuildConfig
import com.example.nightwatchprotocol.R
import com.example.nightwatchprotocol.data.account.AccountRepository
import com.example.nightwatchprotocol.data.account.FirebaseAccountRepository
import com.example.nightwatchprotocol.data.account.SupabaseAccountRepository
import com.example.nightwatchprotocol.data.auth.AuthRepository
import com.example.nightwatchprotocol.data.auth.FirebaseAuthRepository
import com.example.nightwatchprotocol.data.auth.SupabaseAccountStatusLookup
import com.example.nightwatchprotocol.data.auth.SupabaseAuthRepository
import com.example.nightwatchprotocol.data.avatar.AVATAR_BUCKET
import com.example.nightwatchprotocol.data.avatar.AvatarImageProcessor
import com.example.nightwatchprotocol.data.avatar.AvatarRepository
import com.example.nightwatchprotocol.data.avatar.BitmapAvatarImageProcessor
import com.example.nightwatchprotocol.data.avatar.FileAvatarCache
import com.example.nightwatchprotocol.data.avatar.SupabaseAvatarStorage
import com.example.nightwatchprotocol.data.biometric.AndroidBiometricCapabilities
import com.example.nightwatchprotocol.data.biometric.BiometricCapabilities
import com.example.nightwatchprotocol.data.device.AndroidDeviceInfoProvider
import com.example.nightwatchprotocol.data.device.DeviceIdStore
import com.example.nightwatchprotocol.data.device.DeviceInfoProvider
import com.example.nightwatchprotocol.data.device.DeviceRepository
import com.example.nightwatchprotocol.data.device.DeviceSessionManager
import com.example.nightwatchprotocol.data.device.FirestoreDeviceRepository
import com.example.nightwatchprotocol.data.device.SupabaseDeviceRepository
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
import com.example.nightwatchprotocol.data.profile.DataStoreProfileExtrasCache
import com.example.nightwatchprotocol.data.profile.ProfileDataExporter
import com.example.nightwatchprotocol.data.profile.ProfileExtrasRepository
import com.example.nightwatchprotocol.data.profile.ProfileRepository
import com.example.nightwatchprotocol.data.profile.SupabaseProfileRepository
import com.example.nightwatchprotocol.data.quiz.FirestoreQuizResultRepository
import com.example.nightwatchprotocol.data.quiz.LocalQuizContentRepository
import com.example.nightwatchprotocol.data.quiz.QuizContentRepository
import com.example.nightwatchprotocol.data.quiz.QuizResultRepository
import com.example.nightwatchprotocol.data.quiz.SupabasePlacementRepository
import com.example.nightwatchprotocol.data.session.SessionPreferences
import com.example.nightwatchprotocol.data.session.sessionDataStore
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage

interface AppContainer {
    val googleWebClientId: String
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
    val profileExtrasRepository: ProfileExtrasRepository
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
    private val backend: Backend = parseBackend(BuildConfig.BACKEND)

    init {
        if (backend == Backend.SUPABASE) {
            supabaseConfigError(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_PUBLISHABLE_KEY)?.let {
                throw IllegalStateException(it)
            }
        }
    }

    // Only built when BACKEND=supabase and something asks for it; in firebase mode it is never created.
    val supabaseClient: SupabaseClient by lazy {
        check(backend == Backend.SUPABASE) { "El cliente de Supabase solo existe con BACKEND=supabase." }
        createSupabaseClient(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_PUBLISHABLE_KEY) {
            install(Auth)
            install(Postgrest)
            install(Storage)
        }
    }

    /*
     * Block A of the Supabase migration. With BACKEND=supabase these are the Supabase repositories: email and
     * password sign-in, account (name, identity check, password), quiz result, profile (+ photo in Storage) and
     * devices. With BACKEND=firebase nothing below changes, and nothing from Supabase or the Firebase-free paths
     * is created.
     *
     * Block B adds Google sign-in (ID token + nonce), the 6-digit codes (sign-up, email change, password recovery)
     * and "sign out all other sessions" (signOut(SignOutScope.OTHERS)), all inside those repositories.
     * Still pending: an observer of sessionStatus that sends the user to Login when the session dies.
     */
    private val supabaseAuthRepository: SupabaseAuthRepository? =
        if (backend == Backend.SUPABASE) {
            SupabaseAuthRepository(supabaseClient.auth, SupabaseAccountStatusLookup(supabaseClient.postgrest))
        } else {
            null
        }

    // The row owner always comes from the session, never from a screen. Read lazily: only built in supabase mode.
    private val sessionUserId: () -> String? = { supabaseAuthRepository?.currentUserUid }

    // Generated by the google-services plugin from google-services.json.
    override val googleWebClientId: String = context.getString(R.string.default_web_client_id)
    override val authRepository: AuthRepository =
        supabaseAuthRepository ?: FirebaseAuthRepository(FirebaseAuth.getInstance())
    override val sessionPreferences: SessionPreferences = SessionPreferences(context.sessionDataStore)
    override val quizContentRepository: QuizContentRepository = LocalQuizContentRepository(context)
    override val quizResultRepository: QuizResultRepository =
        if (supabaseAuthRepository != null) {
            SupabasePlacementRepository(supabaseClient.postgrest, sessionUserId)
        } else {
            FirestoreQuizResultRepository(FirebaseFirestore.getInstance())
        }
    override val accountRepository: AccountRepository =
        if (supabaseAuthRepository != null) {
            SupabaseAccountRepository(supabaseClient.auth, supabaseAuthRepository)
        } else {
            FirebaseAccountRepository(FirebaseAuth.getInstance())
        }
    override val profileRepository: ProfileRepository =
        if (supabaseAuthRepository != null) {
            SupabaseProfileRepository(
                supabaseClient.postgrest,
                SupabaseAvatarStorage(supabaseClient.storage[AVATAR_BUCKET]),
                sessionUserId,
            )
        } else {
            FirestoreProfileRepository(FirebaseFirestore.getInstance())
        }
    override val avatarRepository: AvatarRepository =
        AvatarRepository(profileRepository, FileAvatarCache(context.filesDir))
    override val avatarImageProcessor: AvatarImageProcessor = BitmapAvatarImageProcessor(context.contentResolver)
    override val userPreferences: UserPreferences = UserPreferences(context.userDataStore)
    override val biometricCapabilities: BiometricCapabilities = AndroidBiometricCapabilities(context)
    override val profileExtrasRepository: ProfileExtrasRepository =
        ProfileExtrasRepository(profileRepository, DataStoreProfileExtrasCache(context.userDataStore))
    override val profileDataExporter: ProfileDataExporter =
        ProfileDataExporter(accountRepository, profileRepository, quizResultRepository, profileExtrasRepository)
    override val dataExportWriter: DataExportWriter = ContentResolverDataExportWriter(context.contentResolver)
    override val notificationPermission: NotificationPermission = AndroidNotificationPermission(context)
    override val reminderScheduler: ReminderScheduler = WorkManagerReminderScheduler(context)
    override val reminderCoordinator: ReminderCoordinator =
        ReminderCoordinator(reminderScheduler, notificationPermission, userPreferences)
    override val deviceRepository: DeviceRepository =
        if (supabaseAuthRepository != null) {
            SupabaseDeviceRepository(supabaseClient.postgrest, sessionUserId)
        } else {
            FirestoreDeviceRepository(FirebaseFirestore.getInstance())
        }
    override val deviceIdStore: DeviceIdStore = DeviceIdStore(context.userDataStore)
    override val deviceInfoProvider: DeviceInfoProvider = AndroidDeviceInfoProvider()
    override val deviceSessionManager: DeviceSessionManager =
        DeviceSessionManager(authRepository, deviceRepository, deviceIdStore, deviceInfoProvider, avatarRepository)
}
