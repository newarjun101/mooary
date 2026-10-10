package com.arjun.mooary.ui.labels

import androidx.compose.runtime.Composable
import com.arjun.mooary.resources.Res
import com.arjun.mooary.resources.calendar_month_label
import com.arjun.mooary.resources.entry_date_label
import com.arjun.mooary.resources.month_1
import com.arjun.mooary.resources.month_10
import com.arjun.mooary.resources.month_11
import com.arjun.mooary.resources.month_12
import com.arjun.mooary.resources.month_2
import com.arjun.mooary.resources.month_3
import com.arjun.mooary.resources.month_4
import com.arjun.mooary.resources.month_5
import com.arjun.mooary.resources.month_6
import com.arjun.mooary.resources.month_7
import com.arjun.mooary.resources.month_8
import com.arjun.mooary.resources.month_9
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number

/**
 * Month names as localised text.
 *
 * `Month.name` and `LocalDate.toString()` both emit English regardless of the device
 * language, so anything a user reads has to come from resources instead. [monthNumber] is
 * 1-based to match [com.arjun.mooary.resources.month_1] rather than `kotlinx.datetime`'s
 * 0-based [kotlinx.datetime.Month.number]; the bounds are clamped so a caller can pass a
 * number straight from arithmetic without risking an index crash on a bad value.
 */
private val MONTH_NAMES: List<StringResource> = listOf(
    Res.string.month_1, Res.string.month_2, Res.string.month_3,
    Res.string.month_4, Res.string.month_5, Res.string.month_6,
    Res.string.month_7, Res.string.month_8, Res.string.month_9,
    Res.string.month_10, Res.string.month_11, Res.string.month_12,
)

/** The resource for a 1-based [monthNumber], clamped into 1..12. */
fun monthNameRes(monthNumber: Int): StringResource =
    MONTH_NAMES[monthNumber.coerceIn(1, 12) - 1]

/**
 * `September 2026` in the user's language.
 *
 * The year lives in its own string rather than in the month names, because the yearly chart
 * axis needs the month on its own and would otherwise render a literal `%2$d`.
 */
@Composable
fun monthLabel(month: Int, year: Int): String =
    stringResource(Res.string.calendar_month_label, stringResource(monthNameRes(month)), year)

/**
 * `5 October 2026` in the user's language.
 *
 * Not [LocalDate.toString], which is English and ISO-ordered whatever the device language is.
 * The argument order is deliberately different per locale -- Burmese reads year, month, day --
 * so the two `strings.xml` files carry the same specifiers in a different arrangement rather
 * than one of them being a word-for-word copy of the other.
 */
@Composable
fun entryDateLabel(date: LocalDate): String =
    stringResource(Res.string.entry_date_label, date.day, stringResource(monthNameRes(date.month.number)), date.year)
