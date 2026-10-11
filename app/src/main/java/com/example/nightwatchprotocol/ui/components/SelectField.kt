package com.example.nightwatchprotocol.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nightwatchprotocol.ui.theme.NightWatchBorderAccent
import com.example.nightwatchprotocol.ui.theme.NightWatchError
import com.example.nightwatchprotocol.ui.theme.NightWatchFieldShape
import com.example.nightwatchprotocol.ui.theme.NightWatchOnBackground
import com.example.nightwatchprotocol.ui.theme.NightWatchSurfaceButtonMuted

/**
 * A field that looks like `NightWatchTextField` but is not typed into: tapping it calls [onClick] (open a menu
 * or a date picker). [valueText] is what is shown; an empty value shows "—" to say "not defined".
 */
@Composable
fun NightWatchSelectField(
    label: String,
    valueText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    supportingText: String? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            color = NightWatchOnBackground,
            style = MaterialTheme.typography.labelLarge,
            fontSize = 14.sp,
        )
        Box(modifier = Modifier.padding(top = 4.dp)) {
            OutlinedTextField(
                value = valueText.ifEmpty { "—" },
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                shape = NightWatchFieldShape,
                isError = isError,
                modifier = Modifier.fillMaxWidth().testTag(label),
                trailingIcon = {
                    if (isError) {
                        Box(
                            modifier = Modifier.size(20.dp).border(1.5.dp, NightWatchError, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Error", tint = NightWatchError, modifier = Modifier.size(12.dp))
                        }
                    } else {
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = NightWatchOnBackground)
                    }
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
            // The read-only text field would swallow the touch; this layer turns the whole field into a button.
            Box(modifier = Modifier.matchParentSize().clickable(role = Role.Button, onClick = onClick))
        }
    }
}
