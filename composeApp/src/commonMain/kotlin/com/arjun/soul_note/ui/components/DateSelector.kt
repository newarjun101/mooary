package com.arjun.soul_note.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import com.arjun.soul_note.ui.components.AnimatedIcon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.arjun.soul_note.resources.Res
import com.arjun.soul_note.resources.date_picker_cancel
import com.arjun.soul_note.resources.date_picker_confirm
import com.arjun.soul_note.resources.date_selector_label
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant
import org.jetbrains.compose.resources.stringResource

/** A shared local-date picker; UTC keeps calendar dates stable across device time zones. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateSelector(
    label: String,
    date: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showingPicker by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { showingPicker = true }, modifier = modifier) {
        AnimatedIcon(Icons.Default.CalendarMonth, contentDescription = null)
        Text(stringResource(Res.string.date_selector_label, label, date.toShortLabel()))
    }

    if (showingPicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds(),
        )
        DatePickerDialog(
            onDismissRequest = { showingPicker = false },
            confirmButton = {
                Button(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            onDateSelected(
                                Instant.fromEpochMilliseconds(millis)
                                    .toLocalDateTime(TimeZone.UTC)
                                    .date,
                            )
                        }
                        showingPicker = false
                    },
                ) { Text(stringResource(Res.string.date_picker_confirm)) }
            },
            dismissButton = {
                OutlinedButton(onClick = { showingPicker = false }) {
                    Text(stringResource(Res.string.date_picker_cancel))
                }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}
