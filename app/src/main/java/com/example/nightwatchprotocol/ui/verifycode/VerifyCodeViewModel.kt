package com.example.nightwatchprotocol.ui.verifycode

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import com.example.nightwatchprotocol.NightWatchApplication
import com.example.nightwatchprotocol.data.auth.AuthError
import com.example.nightwatchprotocol.ui.navigation.VerifyCode
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The codes Supabase sends have 6 digits ("Email OTP length" in the panel). */
const val CODE_LENGTH = 6

const val MESSAGE_INVALID_CODE = "El código ingresado no es correcto."
const val MESSAGE_CODE_RESENT = "Te enviamos un código nuevo."
const val MESSAGE_OFFLINE = "Sin conexión a internet"
const val MESSAGE_CODE_GENERIC_ERROR = "Se produjo un error inesperado. Intenta de nuevo."

const val MESSAGE_CODE_EXPIRED = "El código expiró. Solicita uno nuevo."
const val MESSAGE_TOO_MANY_ATTEMPTS = "Demasiados intentos. Espera unos minutos y vuelve a intentarlo."

/** "Email OTP expiration" in the panel: 600 seconds. */
const val CODE_VALIDITY_MILLIS = 600_000L

/** The least time between two emails to the same user ("minimum interval" in the panel): 60 seconds. */
const val RESEND_COOLDOWN_SECONDS = 60

/** What the code field accepts: only ASCII digits, at most [CODE_LENGTH]. (`isDigit()` would let other scripts in.) */
fun sanitizeCode(raw: String): String = raw.filter { it in '0'..'9' }.take(CODE_LENGTH)

/** Why the last confirmation failed. [clearsCode] says whether the typed code is useless and should be retyped. */
enum class VerifyCodeError(val message: String, val clearsCode: Boolean) {
    InvalidCode(MESSAGE_INVALID_CODE, clearsCode = true),
    Expired(MESSAGE_CODE_EXPIRED, clearsCode = true),
    TooManyAttempts(MESSAGE_TOO_MANY_ATTEMPTS, clearsCode = false),
    Offline(MESSAGE_OFFLINE, clearsCode = false),
    Generic(MESSAGE_CODE_GENERIC_ERROR, clearsCode = false),
}

data class VerifyCodeUiState(
    val email: String,
    val code: String = "",
    val isConfirming: Boolean = false,
    val isResending: Boolean = false,
    val error: VerifyCodeError? = null,
    /** "Te enviamos un código nuevo." after a successful resend. */
    val notice: String? = null,
    /** Seconds until "Reenviar" works again; 0 when it already does. */
    val resendSecondsLeft: Int = 0,
) {
    /** The confirm button is enabled once the six digits are in. */
    val canConfirm: Boolean get() = code.length == CODE_LENGTH && !isConfirming

    val canResend: Boolean get() = resendSecondsLeft == 0 && !isResending

    /** The text of the resend link: a countdown while it is disabled. */
    val resendLabel: String
        get() = if (resendSecondsLeft > 0) {
            "Reenviar en $resendSecondsLeft s"
        } else {
            "Reenviar el código de $CODE_LENGTH digitos"
        }
}

sealed interface VerifyCodeEvent {
    /** The code was accepted; the caller decides where the flow goes next. */
    data object Verified : VerifyCodeEvent

    /** Accepted, but a secondary step failed (see [CodeFlow.completionNotice]); the next screen shows [notice]. */
    data class VerifiedWithNotice(val notice: String) : VerifyCodeEvent
}

/**
 * One view model for every 6-digit code screen. What "confirm" and "resend" do is the [CodeFlow] it gets: sign-up,
 * email change or password recovery.
 */
class VerifyCodeViewModel(
    private val flow: CodeFlow,
    email: String,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val _uiState = MutableStateFlow(VerifyCodeUiState(email = email))
    val uiState: StateFlow<VerifyCodeUiState> = _uiState.asStateFlow()

    private val _events = Channel<VerifyCodeEvent>(Channel.BUFFERED)
    val events: Flow<VerifyCodeEvent> = _events.receiveAsFlow()

    // The server answers the same for a wrong and for an expired code, so expiry is decided here: a code is expired
    // once CODE_VALIDITY_MILLIS passed since it was sent. A code was sent just before this screen opened.
    private var codeSentAtMillis = clock()
    private var countdownJob: Job? = null

    init {
        startCountdown(RESEND_COOLDOWN_SECONDS)
    }

    /** Disables "Reenviar" and counts [seconds] down to zero, one per second. */
    private fun startCountdown(seconds: Int) {
        countdownJob?.cancel()
        _uiState.update { it.copy(resendSecondsLeft = seconds) }
        countdownJob = viewModelScope.launch {
            var left = seconds
            while (left > 0) {
                delay(1_000)
                left--
                _uiState.update { it.copy(resendSecondsLeft = left) }
            }
        }
    }

    fun onCodeChanged(raw: String) {
        _uiState.update { it.copy(code = sanitizeCode(raw), error = null, notice = null) }
    }

    /**
     * "CONFIRMAR CÓDIGO". After a failure the same button reads "REINTENTAR": a refused code is cleared so a new one
     * can be typed; any other failure (no connection...) sends the same code again.
     */
    fun onConfirmClicked() {
        val state = _uiState.value
        val error = state.error
        if (error != null && error.clearsCode) {
            _uiState.update { it.copy(code = "", error = null) }
            return
        }
        if (!state.canConfirm) return
        viewModelScope.launch {
            _uiState.update { it.copy(isConfirming = true, error = null, notice = null) }
            try {
                flow.confirm(state.code)
                _uiState.update { it.copy(isConfirming = false) }
                val notice = flow.completionNotice
                _events.send(if (notice == null) VerifyCodeEvent.Verified else VerifyCodeEvent.VerifiedWithNotice(notice))
            } catch (e: CancellationException) {
                throw e
            } catch (e: AuthError) {
                _uiState.update { it.copy(isConfirming = false, error = errorFor(e)) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isConfirming = false, error = VerifyCodeError.Generic) }
            }
        }
    }

    /** "Reenviar el código": only when the countdown is over. A new code makes the old one useless, so the field is cleared. */
    fun onResendClicked() {
        if (!_uiState.value.canResend) return
        viewModelScope.launch {
            _uiState.update { it.copy(isResending = true, error = null, notice = null) }
            try {
                flow.resend()
                codeSentAtMillis = clock()
                _uiState.update { it.copy(isResending = false, code = "", notice = MESSAGE_CODE_RESENT) }
                startCountdown(RESEND_COOLDOWN_SECONDS)
            } catch (e: CancellationException) {
                throw e
            } catch (e: AuthError.RateLimited) {
                // Too soon for the server: no error text, the link just counts the seconds it asked for.
                _uiState.update { it.copy(isResending = false) }
                startCountdown(e.retryAfterSeconds ?: RESEND_COOLDOWN_SECONDS)
            } catch (e: AuthError) {
                _uiState.update { it.copy(isResending = false, error = errorFor(e)) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isResending = false, error = VerifyCodeError.Generic) }
            }
        }
    }

    private fun errorFor(error: AuthError): VerifyCodeError = when (error) {
        AuthError.InvalidCode ->
            if (clock() - codeSentAtMillis >= CODE_VALIDITY_MILLIS) VerifyCodeError.Expired else VerifyCodeError.InvalidCode
        AuthError.CodeExpired -> VerifyCodeError.Expired
        AuthError.TooManyAttempts -> VerifyCodeError.TooManyAttempts
        AuthError.NetworkError -> VerifyCodeError.Offline
        else -> VerifyCodeError.Generic
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NightWatchApplication
                val route = createSavedStateHandle().toRoute<VerifyCode>()
                val container = application.container
                val flow = codeFlowFor(route.purpose, route.email, container.authRepository, container.accountRepository)
                VerifyCodeViewModel(flow, route.email)
            }
        }
    }
}
