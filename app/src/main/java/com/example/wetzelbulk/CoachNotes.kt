package com.example.wetzelbulk

import android.content.Context
import org.json.JSONObject
import java.time.Instant
import java.time.OffsetDateTime

// ══════════════════════════════════════════════════════════════════════════════
// P3: PERSONAL TRAINER NOTES (source of truth: coach/notes.json on GitHub)
// ══════════════════════════════════════════════════════════════════════════════

data class CoachNote(
    val id: String,
    val workout: String,      // PUSH | LEGS | PULL | CARDIO
    val createdAtMillis: Long,
    val createdAtText: String,
    val text: String
)

/** "PULL DAY" -> "PULL". Notes target workout types, never dates. */
fun dayNameToWorkoutType(dayName: String): String =
    dayName.trim().uppercase().removeSuffix(" DAY").trim()

fun parseIsoMillis(text: String): Long? {
    val t = text.trim()
    return runCatching { OffsetDateTime.parse(t).toInstant().toEpochMilli() }.getOrNull()
        ?: runCatching { Instant.parse(t).toEpochMilli() }.getOrNull()
}

/** Parses notes.json. Invalid entries are skipped rather than failing the whole file. */
fun parseCoachNotesJson(json: String): List<CoachNote> {
    val root = JSONObject(json)
    val arr = root.optJSONArray("notes") ?: return emptyList()
    val out = mutableListOf<CoachNote>()
    for (i in 0 until arr.length()) {
        val o = arr.optJSONObject(i) ?: continue
        val id = o.optString("id", "").trim()
        val workout = o.optString("workout", "").trim().uppercase()
        val createdText = o.optString("createdAt", "").trim()
        val text = o.optString("text", "")
        val millis = parseIsoMillis(createdText) ?: continue
        if (id.isEmpty() || workout !in setOf("PUSH", "LEGS", "PULL", "CARDIO") || text.isBlank()) continue
        out.add(CoachNote(id, workout, millis, createdText, text))
    }
    return out
}

/** The current note for a workout type: the latest createdAt (optionally only notes created at/before a time). */
fun latestCoachNote(workoutType: String, notes: List<CoachNote>, atOrBeforeMillis: Long? = null): CoachNote? =
    notes.filter { it.workout == workoutType && (atOrBeforeMillis == null || it.createdAtMillis <= atOrBeforeMillis) }
        .maxByOrNull { it.createdAtMillis }

/**
 * Note of record for a finished workout: the note saved with the session if there is one,
 * otherwise the latest note for that workout type that existed when the workout ended.
 */
fun coachNoteOfRecord(dayName: String, endTimeMillis: Long, savedNoteId: String?, notes: List<CoachNote>): CoachNote? {
    if (savedNoteId != null) notes.firstOrNull { it.id == savedNoteId }?.let { return it }
    return latestCoachNote(dayNameToWorkoutType(dayName), notes, endTimeMillis)
}

/** Phone-side cache of the downloaded file and which notes have been opened. */
object CoachNotesStore {
    private const val PREFS = "WetzelCoachPrefs"
    private const val KEY_CACHE = "notes_json_cache"
    private const val KEY_SEEN = "seen_note_ids"
    const val PATH = "coach/notes.json"

    fun loadCached(context: Context): List<CoachNote> {
        val json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_CACHE, null) ?: return emptyList()
        return runCatching { parseCoachNotesJson(json) }.getOrDefault(emptyList())
    }

    fun saveCache(context: Context, json: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_CACHE, json).apply()
    }

    fun loadSeen(context: Context): Set<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet(KEY_SEEN, emptySet())?.toSet() ?: emptySet()

    fun markSeen(context: Context, id: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val updated = (prefs.getStringSet(KEY_SEEN, emptySet()) ?: emptySet()).toMutableSet().apply { add(id) }
        prefs.edit().putStringSet(KEY_SEEN, updated).apply()
    }
}
