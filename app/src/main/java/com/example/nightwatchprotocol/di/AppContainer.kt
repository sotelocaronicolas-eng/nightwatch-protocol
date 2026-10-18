package com.example.nightwatchprotocol.di

import android.content.Context
import com.example.nightwatchprotocol.BuildConfig
import com.example.nightwatchprotocol.data.account.AccountRepository
import com.example.nightwatchprotocol.data.account.SupabaseAccountRepository
import com.example.nightwatchprotocol.data.auth.AuthRepository
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
import com.example.nightwatchprotocol.data.profile.DataStoreProfileExtrasCache
import com.example.nightwatchprotocol.data.profile.ProfileDataExporter
import com.example.nightwatchprotocol.data.profile.ProfileExtrasRepository
import com.example.nightwatchprotocol.data.profile.ProfileRepository
import com.example.nightwatchprotocol.data.profile.SupabaseProfileRepository
import com.example.nightwatchprotocol.data.quiz.LocalQuizContentRepository
import com.example.nightwatchprotocol.data.quiz.QuizContentRepository
import com.example.nightwatchprotocol.data.quiz.QuizResultRepository
import com.example.nightwatchprotocol.data.quiz.SupabasePlacementRepository
import com.example.nightwatchprotocol.data.session.SessionPreferences
import com.example.nightwatchprotocol.data.session.sessionDataStore
import com.example.nightwatchprotocol.data.supabase.SupabaseRecoveryEmailCodeApi
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.functions.functions
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

/**
 * The one place that knows Supabase: it builds the client (Auth, Postgrest, Storage and Functions) and the repositories
 * on top of it. Everything above (view models, screens) only sees the repository interfaces.
 *
 * The Supabase settings and the Google web client ID come from `local.properties` (see `local.properties.example`);
 * if one is missing the app stops at startup with a message that says which.
 */
class DefaultAppContainer(context: Context) : AppContainer {

    init {
        // Fail fast, saying what to add to local.properties, instead of crashing later with an obscure error.
        appConfigError(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_PUBLISHABLE_KEY, BuildConfig.GOOGLE_WEB_CLIENT_ID)
            ?.let { throw IllegalStateException(it) }
    }

    private val supabaseClient: SupabaseClient =
        createSupabaseClient(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_PUBLISHABLE_KEY) {
            install(Auth)
            install(Postgrest)
            install(Storage)
            install(Functions)
        }

    private val supabaseAuthRepository =
        SupabaseAuthRepository(supabaseClient.auth, SupabaseAccountStatusLookup(supabaseClient.postgrest))

    // The row owner always comes from the session, never from a screen.
    private val sessionUserId: () -> String? = { supabaseAuthRepository.currentUserUid }

    override val googleWebClientId: String = BuildConfig.GOOGLE_WEB_CLIENT_ID
    override val authRepository: AuthRepository = supabaseAuthRepository
    override val sessionPreferences: SessionPreferences = SessionPreferences(context.sessionDataStore)
    override val quizContentRepository: QuizContentRepository = LocalQuizContentRepository(context)
    override val quizResultRepository: QuizResultRepository =
        SupabasePlacementRepository(supabaseClient.postgrest, sessionUserId)
    override val accountRepository: AccountRepository =
        SupabaseAccountRepository(supabaseClient.auth, supabaseAuthRepository)
    override val profileRepository: ProfileRepository =
        SupabaseProfileRepository(
            supabaseClient.postgrest,
            SupabaseAvatarStorage(supabaseClient.storage[AVATAR_BUCKET]),
            sessionUserId,
            SupabaseRecoveryEmailCodeApi(supabaseClient.functions, supabaseClient.postgrest),
        )
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
        SupabaseDeviceRepository(supabaseClient.postgrest, sessionUserId)
    override val deviceIdStore: DeviceIdStore = DeviceIdStore(context.userDataStore)
    override val deviceInfoProvider: DeviceInfoProvider = AndroidDeviceInfoProvider()
    override val deviceSessionManager: DeviceSessionManager =
        DeviceSessionManager(authRepository, deviceRepository, deviceIdStore, deviceInfoProvider, avatarRepository)
}
