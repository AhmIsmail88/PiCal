package com.ahmedismail.flowtrack.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ahmedismail.flowtrack.R
import com.ahmedismail.flowtrack.ui.theme.Ink
import com.ahmedismail.flowtrack.ui.theme.Ink2
import com.ahmedismail.flowtrack.ui.theme.glassPanel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Calendar
import java.util.TimeZone

/** An optional [start, end] window in epoch millis. Both null means "no filter — all time". */
data class DateRange(val startMillis: Long? = null, val endMillis: Long? = null) {
    /** True if [dateMillis] falls within this range (inclusive), or the range has no bound on that side. */
    fun contains(dateMillis: Long): Boolean {
        val afterStart = startMillis == null || dateMillis >= startOfDay(startMillis)
        val beforeEnd = endMillis == null || dateMillis <= endOfDay(endMillis)
        return afterStart && beforeEnd
    }

    companion object {
        // Material DatePicker stores calendar dates at UTC midnight. Interpret
        // those Y/M/D fields as a local day when filtering real timestamps.
        private fun localDay(millis: Long): Calendar {
            val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = millis }
            return Calendar.getInstance().apply {
                clear()
                set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH))
            }
        }
        private fun startOfDay(millis: Long) = localDay(millis).timeInMillis
        private fun endOfDay(millis: Long) = localDay(millis).apply { add(Calendar.DAY_OF_MONTH, 1) }.timeInMillis - 1
    }
}

/**
 * "From — To" period selector used by report exports (project-wide and
 * per-employee). Either side left blank means unbounded on that side; both
 * blank means "all time".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangeField(range: DateRange, onChange: (DateRange) -> Unit, modifier: Modifier = Modifier) {
    var pickingStart by remember { mutableStateOf(false) }
    var pickingEnd by remember { mutableStateOf(false) }
    val format = remember { SimpleDateFormat("dd/MM/yyyy", Locale.US) }

    Column(modifier) {
        Text(stringResource(R.string.report_period_label), style = MaterialTheme.typography.bodySmall, color = Ink2, modifier = Modifier.padding(bottom = 5.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            DateChip(
                label = stringResource(R.string.date_from),
                value = range.startMillis?.let { format.format(Date(it)) } ?: stringResource(R.string.all_time),
                onClick = { pickingStart = true },
                modifier = Modifier.weight(1f)
            )
            DateChip(
                label = stringResource(R.string.date_to),
                value = range.endMillis?.let { format.format(Date(it)) } ?: stringResource(R.string.all_time),
                onClick = { pickingEnd = true },
                modifier = Modifier.weight(1f)
            )
            if (range.startMillis != null || range.endMillis != null) {
                TextButton(onClick = { onChange(DateRange()) }) { Text(stringResource(R.string.date_range_clear)) }
            }
        }
    }

    if (pickingStart) {
        val state = rememberDatePickerState(initialSelectedDateMillis = range.startMillis)
        DatePickerDialog(
            onDismissRequest = { pickingStart = false },
            confirmButton = {
                TextButton(onClick = {
                    onChange(normalizeRange(range.copy(startMillis = state.selectedDateMillis)))
                    pickingStart = false
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { pickingStart = false }) { Text(stringResource(android.R.string.cancel)) } }
        ) { DatePicker(state = state) }
    }

    if (pickingEnd) {
        val state = rememberDatePickerState(initialSelectedDateMillis = range.endMillis)
        DatePickerDialog(
            onDismissRequest = { pickingEnd = false },
            confirmButton = {
                TextButton(onClick = {
                    onChange(normalizeRange(range.copy(endMillis = state.selectedDateMillis)))
                    pickingEnd = false
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { pickingEnd = false }) { Text(stringResource(android.R.string.cancel)) } }
        ) { DatePicker(state = state) }
    }
}

/**
 * If both ends are set and "From" ends up after "To" (e.g. the user picks
 * From after already having a To in the past), silently swaps them instead
 * of leaving an impossible range that would filter out every entry with no
 * explanation of why the report came back empty.
 */
private fun normalizeRange(range: DateRange): DateRange {
    val start = range.startMillis
    val end = range.endMillis
    return if (start != null && end != null && start > end) {
        DateRange(startMillis = end, endMillis = start)
    } else {
        range
    }
}

@Composable
private fun DateChip(label: String, value: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .glassPanel(cornerRadius = 10.dp)
            .clickable(onClick = onClick)
            .heightIn(min = 44.dp)
            .padding(10.dp, 8.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Ink2)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = Ink)
    }
}
