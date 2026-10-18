package com.example.nightwatchprotocol.ui.verifycode

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nightwatchprotocol.R
import com.example.nightwatchprotocol.ui.components.BottomActionScrollLayout
import com.example.nightwatchprotocol.ui.components.NightWatchScreenBackground
import com.example.nightwatchprotocol.ui.components.keepVisibleWithKeyboard
import com.example.nightwatchprotocol.ui.components.ProfileButton
import com.example.nightwatchprotocol.ui.components.ProfileTopBar
import com.example.nightwatchprotocol.ui.profile.changeemail.EmailScreenHeader
import com.example.nightwatchprotocol.ui.profile.changeemail.EnvelopeImage
import com.example.nightwatchprotocol.ui.theme.NightWatchBodyFontFamily
import com.example.nightwatchprotocol.ui.theme.NightWatchError
import com.example.nightwatchprotocol.ui.theme.NightWatchHeaderPanel
import com.example.nightwatchprotocol.ui.theme.NightWatchLinkAccent
import com.example.nightwatchprotocol.ui.theme.NightWatchSurfaceButton
import com.example.nightwatchprotocol.ui.theme.NightWatchSurfaceButtonMuted

const val CODE_FIELD_TAG = "code-field"

@Composable
fun VerifyCodeScreen(
    onVerified: (notice: String?) -> Unit,
    onClose: () -> Unit,
    viewModel: VerifyCodeViewModel = viewModel(factory = VerifyCodeViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                VerifyCodeEvent.Verified -> onVerified(null)
                is VerifyCodeEvent.VerifiedWithNotice -> onVerified(event.notice)
            }
        }
    }

    VerifyCodeContent(
        title = "Verifica tu Correo",
        recipient = uiState.email,
        uiState = uiState,
        onCodeChange = viewModel::onCodeChanged,
        onConfirm = viewModel::onConfirmClicked,
        onResend = viewModel::onResendClicked,
        onClose = onClose,
    )
}

enum class VerifyCodeLayout {
    /** Full screen: "VERIFICAR CÓDIGO" / "CÓDIGO INCORRECTO" (sign-up and email change). */
    Screen,

    /** The dialog over Login with six boxes: "OLVIDASTE LA CONTRASEÑA" (password recovery). */
    Dialog,
}

/**
 * The 6-digit code UI, used by the sign-up, the email change and the password recovery. What differs between them is
 * only the [title], the [recipient], the [layout] (screen or dialog) and the view model behind the callbacks.
 */
@Composable
fun VerifyCodeContent(
    title: String,
    recipient: String,
    uiState: VerifyCodeUiState,
    onCodeChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onResend: () -> Unit,
    onClose: () -> Unit,
    layout: VerifyCodeLayout = VerifyCodeLayout.Screen,
) {
    when (layout) {
        VerifyCodeLayout.Screen ->
            VerifyCodeScreenLayout(title, recipient, uiState, onCodeChange, onConfirm, onResend, onClose)
        VerifyCodeLayout.Dialog ->
            VerifyCodeDialogLayout(title, recipient, uiState, onCodeChange, onConfirm, onResend, onClose)
    }
}

@Composable
private fun VerifyCodeDialogLayout(
    title: String,
    recipient: String,
    uiState: VerifyCodeUiState,
    onCodeChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onResend: () -> Unit,
    onClose: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onClose,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title, modifier = Modifier.weight(1f))
                Image(
                    painter = painterResource(R.drawable.img_logo_nightwatch),
                    contentDescription = null,
                    modifier = Modifier.padding(start = 12.dp).size(44.dp),
                )
            }
        },
        text = {
            // Scrolls if the keyboard leaves the dialog too little room for the code boxes.
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Hemos enviado un código a $recipient. Ingresa el código recibido para verificar tu identidad " +
                        "y continuar con el proceso de restablecimiento de tu contraseña.",
                )
                CodeInput(
                    code = uiState.code,
                    onCodeChange = onCodeChange,
                    style = CodeInputStyle.Boxes,
                    isError = uiState.error != null,
                    modifier = Modifier.padding(top = 20.dp),
                )
                CodeHint(uiState, onSurfaceColor = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                ResendLink(uiState, onResend, modifier = Modifier.padding(top = 4.dp))
            }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Volver al menú") } },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = uiState.error != null || uiState.canConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = NightWatchLinkAccent,
                    contentColor = Color.Black,
                ),
            ) { Text(if (uiState.error?.clearsCode == true) "Reintentar" else "Confirmar") }
        },
    )
}

@Composable
private fun VerifyCodeScreenLayout(
    title: String,
    recipient: String,
    uiState: VerifyCodeUiState,
    onCodeChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onResend: () -> Unit,
    onClose: () -> Unit,
) {
    NightWatchScreenBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            ProfileTopBar(onClose = onClose)
            BottomActionScrollLayout(
                actionBottomPadding = 36.dp,
                modifier = Modifier.weight(1f),
                action = {
                    ProfileButton(
                        text = if (uiState.error != null) "REINTENTAR" else "CONFIRMAR CÓDIGO",
                        onClick = onConfirm,
                        enabled = uiState.error != null || uiState.canConfirm,
                    )
                    ResendLink(uiState, onResend, modifier = Modifier.padding(top = 14.dp))
                },
            ) {
                EmailScreenHeader(
                    title = title,
                    message = "Enviamos un código de $CODE_LENGTH dígitos a $recipient",
                )
                EnvelopeImage()
                CodeInput(
                    code = uiState.code,
                    onCodeChange = onCodeChange,
                    style = CodeInputStyle.Bar,
                    isError = uiState.error != null,
                    modifier = Modifier.padding(top = 28.dp),
                )
                CodeHint(uiState, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

/** The line under the field: the error (red) or the notice, otherwise the instruction. */
@Composable
internal fun CodeHint(
    uiState: VerifyCodeUiState,
    modifier: Modifier = Modifier,
    onSurfaceColor: Color = NightWatchHeaderPanel,
) {
    val error = uiState.error
    Text(
        text = error?.message ?: uiState.notice ?: "Ingrese el codigo de $CODE_LENGTH digitos",
        color = if (error != null) NightWatchError else onSurfaceColor,
        fontFamily = NightWatchBodyFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        textAlign = TextAlign.Center,
        modifier = modifier,
    )
}

@Composable
internal fun ResendLink(uiState: VerifyCodeUiState, onResend: () -> Unit, modifier: Modifier = Modifier) {
    // "Reenviar el código de 6 digitos", or "Reenviar en 45 s" (dimmed, not clickable) while the countdown runs.
    Text(
        text = uiState.resendLabel,
        color = if (uiState.canResend) NightWatchHeaderPanel else NightWatchHeaderPanel.copy(alpha = 0.5f),
        fontFamily = NightWatchBodyFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        modifier = modifier
            .clickable(enabled = uiState.canResend, role = Role.Button, onClick = onResend)
            .padding(8.dp),
    )
}

enum class CodeInputStyle {
    /** One grey bar with the digits spaced out (the screens). */
    Bar,

    /** Six separate boxes (the dialog). */
    Boxes,
}

/**
 * The code field. A single text field underneath in both styles, so the keyboard, paste and the accessibility
 * services see one number. It takes digits only, up to [CODE_LENGTH] (see [sanitizeCode]).
 */
@Composable
fun CodeInput(
    code: String,
    onCodeChange: (String) -> Unit,
    style: CodeInputStyle,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    val border = if (isError) NightWatchError else Color.White

    BasicTextField(
        value = code,
        onValueChange = { onCodeChange(sanitizeCode(it)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        textStyle = TextStyle(
            color = Color.Black,
            fontFamily = NightWatchBodyFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 22.sp,
            letterSpacing = 14.sp,
            textAlign = TextAlign.Center,
        ),
        cursorBrush = SolidColor(Color.Black),
        modifier = modifier.keepVisibleWithKeyboard().focusRequester(focusRequester).testTag(CODE_FIELD_TAG),
        decorationBox = { innerTextField ->
            when (style) {
                CodeInputStyle.Bar -> Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(47.dp)
                        .background(NightWatchSurfaceButtonMuted, RoundedCornerShape(12.dp))
                        .border(BorderStroke(2.dp, border), RoundedCornerShape(12.dp)),
                ) { innerTextField() }

                CodeInputStyle.Boxes -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { runCatching { focusRequester.requestFocus() } },
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        repeat(CODE_LENGTH) { index ->
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(width = 36.dp, height = 44.dp)
                                    .background(NightWatchSurfaceButton, RoundedCornerShape(8.dp))
                                    .border(BorderStroke(1.5.dp, border), RoundedCornerShape(8.dp)),
                            ) {
                                Text(
                                    text = code.getOrNull(index)?.toString().orEmpty(),
                                    color = Color.Black,
                                    fontFamily = NightWatchBodyFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 22.sp,
                                )
                            }
                        }
                    }
                    // The real field stays focusable but takes no room; the boxes above draw its digits.
                    Box(modifier = Modifier.size(0.dp)) { innerTextField() }
                }
            }
        },
    )
}
