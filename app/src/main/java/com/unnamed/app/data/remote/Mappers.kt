package com.unnamed.app.data.remote

import com.unnamed.app.data.local.entity.Note
import com.unnamed.app.data.local.entity.SetEntry
import com.unnamed.app.data.local.entity.WorkoutSession
import com.unnamed.app.data.remote.dto.NoteDto
import com.unnamed.app.data.remote.dto.SetEntryDto
import com.unnamed.app.data.remote.dto.WorkoutSessionDto
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

/** Conversions between Room entities (epoch millis) and Supabase DTOs (ISO). */

private fun Long.toIso(): String = Instant.ofEpochMilli(this).toString()

private fun String.isoToMillis(): Long =
    // Accepts both "…Z" and "…+00:00" forms returned by PostgREST.
    runCatching { OffsetDateTime.parse(this).toInstant().toEpochMilli() }
        .getOrElse { Instant.parse(this).toEpochMilli() }

private fun Long.toDateString(): String =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate().toString()

private fun String.dateToMillis(): Long =
    LocalDate.parse(this).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

// --- WorkoutSession ---------------------------------------------------------
fun WorkoutSession.toDto(userId: String): WorkoutSessionDto = WorkoutSessionDto(
    id = id,
    userId = userId,
    title = title,
    sessionDate = sessionDate.toDateString(),
    startedAt = startedAt?.toIso(),
    endedAt = endedAt?.toIso(),
    overallFatigue = overallFatigue,
    notes = notes,
    updatedAt = updatedAt.toIso(),
)

fun WorkoutSessionDto.toEntity(): WorkoutSession = WorkoutSession(
    id = id,
    title = title,
    sessionDate = sessionDate.dateToMillis(),
    startedAt = startedAt?.isoToMillis(),
    endedAt = endedAt?.isoToMillis(),
    overallFatigue = overallFatigue,
    notes = notes,
    updatedAt = updatedAt.isoToMillis(),
    synced = true,
)

// --- SetEntry ---------------------------------------------------------------
fun SetEntry.toDto(userId: String): SetEntryDto = SetEntryDto(
    id = id,
    userId = userId,
    sessionId = sessionId,
    exerciseId = exerciseId,
    setIndex = setIndex,
    setType = setType,
    reps = reps,
    weightKg = weightKg,
    rpe = rpe,
    fatigue = fatigue,
    isBodyweight = isBodyweight,
    addedLoadKg = addedLoadKg,
    side = side,
    updatedAt = updatedAt.toIso(),
)

fun SetEntryDto.toEntity(): SetEntry = SetEntry(
    id = id,
    sessionId = sessionId,
    exerciseId = exerciseId,
    setIndex = setIndex,
    setType = setType,
    reps = reps,
    weightKg = weightKg,
    rpe = rpe,
    fatigue = fatigue,
    isBodyweight = isBodyweight,
    addedLoadKg = addedLoadKg,
    side = side,
    updatedAt = updatedAt.isoToMillis(),
    synced = true,
)

// --- Note -------------------------------------------------------------------
fun Note.toDto(userId: String): NoteDto = NoteDto(
    id = id,
    userId = userId,
    sessionId = sessionId,
    setEntryId = setEntryId,
    exerciseId = exerciseId,
    body = body,
    tags = tags,
    severity = severity,
    createdAt = createdAt.toIso(),
)

fun NoteDto.toEntity(): Note = Note(
    id = id,
    sessionId = sessionId,
    setEntryId = setEntryId,
    exerciseId = exerciseId,
    body = body,
    tags = tags,
    severity = severity,
    createdAt = createdAt.isoToMillis(),
    synced = true,
)
