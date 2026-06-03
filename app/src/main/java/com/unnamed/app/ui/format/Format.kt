package com.unnamed.app.ui.format

import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Display helpers for stats — kept in one place so units read consistently. */
object Format {

    /** Trim trailing ".0": 80.0 → "80", 82.5 → "82.5". */
    fun number(value: Double): String =
        if (abs(value - value.roundToInt()) < 0.05) value.roundToInt().toString()
        else String.format(Locale.US, "%.1f", value)

    /** Weight with unit: 80.0 → "80 kg". */
    fun weight(kg: Double?): String = if (kg == null) "—" else "${number(kg)} kg"

    /** Compact volume: 1234 → "1,234 kg", 18450 → "18.5k kg". */
    fun volume(kg: Double): String = when {
        kg <= 0 -> "—"
        kg >= 10_000 -> String.format(Locale.US, "%.1fk kg", kg / 1000.0)
        else -> "${grouped(kg.roundToInt())} kg"
    }

    /** Estimated 1RM: 100.4 → "100 kg e1RM". */
    fun e1rm(kg: Double?): String = if (kg == null) "—" else "${number(kg)} kg"

    /** "Set N × reps @ weight" line, gracefully handling missing fields. */
    fun setLine(reps: Int?, loadKg: Double?): String {
        val r = reps?.toString() ?: "—"
        return if (loadKg != null && loadKg > 0) "$r × ${number(loadKg)} kg" else "$r reps"
    }

    fun duration(min: Long?): String? = when {
        min == null -> null
        min < 60 -> "${min}m"
        else -> "${min / 60}h ${min % 60}m"
    }

    fun date(epochMs: Long): String =
        DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(epochMs))

    fun dateTime(epochMs: Long): String =
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(epochMs))

    /** snake_case muscle/tag → "Front Delts". */
    fun label(raw: String): String =
        raw.split('_', ' ').filter { it.isNotBlank() }.joinToString(" ") { word ->
            word.replaceFirstChar { it.titlecase(Locale.US) }
        }

    private fun grouped(n: Int): String = String.format(Locale.US, "%,d", n)
}
