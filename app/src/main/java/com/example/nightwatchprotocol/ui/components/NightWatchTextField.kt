package com.example.nightwatchprotocol.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nightwatchprotocol.ui.theme.NightWatchBorderAccent
import com.example.nightwatchprotocol.ui.theme.NightWatchError
import com.example.nightwatchprotocol.ui.theme.NightWatchFieldShape
import com.example.nightwatchprotocol.ui.theme.NightWatchOnBackground
import com.example.nightwatchprotocol.ui.theme.NightWatchSurfaceButtonMuted

@Composable
fun NightWatchTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
    isError: Boolean = false,
    supportingText: String? = null,
    keyboardOptions: KeyboardOptions? = null,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val resolvedKeyboardOptions = keyboardOptions
        ?: if (isPassword) {
            KeyboardOptions(keyboardType = KeyboardType.Password)
        } else {
            KeyboardOptions.Default
        }

    Column(modifier = modifier.fillMaxWidth().keepVisibleWithKeyboard()) {
        Text(
            text = label,
            color = NightWatchOnBackground,
            style = MaterialTheme.typography.labelLarge,
            fontSize = 14.sp,
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp).testTag(label),
            shape = NightWatchFieldShape,
            isError = isError,
            singleLine = true,
            visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = resolvedKeyboardOptions,
            keyboardActions = keyboardActions,
            trailingIcon = if (isError) {
                { NightWatchErrorIcon() }
            } else {
                null
            },
            supportingText = supportingText?.let { { Text(it) } },
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = NightWatchSurfaceButtonMuted,
                focusedContainerColor = NightWatchSurfaceButtonMuted,
                errorContainerColor = NightWatchSurfaceButtonMuted,
                unfocusedBorderColor = NightWatchBorderAccent,
                focusedBorderColor = NightWatchBorderAccent,
                errorBorderColor = NightWatchError,
                unfocusedTextColor = NightWatchOnBackground,
                focusedTextColor = NightWatchOnBackground,
                errorTextColor = NightWatchOnBackground,
                errorSupportingTextColor = NightWatchError,
            ),
        )
    }
}

@Composable
private fun NightWatchErrorIcon() {
    Box(
        modifier = Modifier.size(20.dp).border(1.5.dp, NightWatchError, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Close,
            contentDescription = "Error",
            tint = NightWatchError,
            modifier = Modifier.size(12.dp),
        )
    }
}
