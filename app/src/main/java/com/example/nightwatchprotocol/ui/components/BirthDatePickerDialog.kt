package com.example.nightwatchprotocol.ui.components

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.example.nightwatchprotocol.data.profile.BirthDate
import com.example.nightwatchprotocol.data.profile.birthDateIssue
import com.example.nightwatchprotocol.data.profile.earliestBirthDate

/**
 * Material 3 date picker that only offers dates [birthDateIssue] accepts: nothing after [today] and nothing more
 * than 120 years back. The rules live in `BirthDate.kt` (unit tested); this only wires them to the picker.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BirthDatePickerDialog(
    initial: BirthDate?,
    today: BirthDate,
    onDismiss: () -> Unit,
    onConfirm: (BirthDate) -> Unit,
) {
    val selectableDates = remember(today) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                birthDateIssue(BirthDate.fromUtcMillis(utcTimeMillis), today) == null

            override fun isSelectableYear(year: Int): Boolean =
                year in earliestBirthDate(today).year..today.year
        }
    }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial?.toUtcMillis(),
        yearRange = earliestBirthDate(today).year..today.year,
        selectableDates = selectableDates,
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedDateMillis != null,
                onClick = { state.selectedDateMillis?.let { onConfirm(BirthDate.fromUtcMillis(it)) } },
            ) { Text("Aceptar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    ) {
        DatePicker(state = state)
    }
}
