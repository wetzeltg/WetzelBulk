package com.example.wetzelbulk

import org.junit.Assert.*
import org.junit.Test

class CoachNotesTest {

    private val json = """
        {
          "notes": [
            {"id": "n1", "workout": "PULL", "createdAt": "2026-11-20T08:00:00-05:00", "text": "Old pull note"},
            {"id": "n2", "workout": "PUSH", "createdAt": "2026-11-21T08:00:00Z", "text": "Push note"},
            {"id": "n3", "workout": "pull", "createdAt": "2026-11-28T08:00:00-05:00", "text": "New pull note"},
            {"id": "bad-date", "workout": "LEGS", "createdAt": "2026-11-28", "text": "skipped"},
            {"id": "bad-type", "workout": "MONDAY", "createdAt": "2026-11-28T08:00:00Z", "text": "skipped"},
            {"id": "", "workout": "LEGS", "createdAt": "2026-11-28T08:00:00Z", "text": "skipped"}
          ]
        }
    """.trimIndent()

    private val notes = parseCoachNotesJson(json)
    private fun millis(iso: String) = parseIsoMillis(iso)!!

    @Test
    fun parsesValidNotesAndSkipsInvalidOnes() {
        assertEquals(listOf("n1", "n2", "n3"), notes.map { it.id })
        assertEquals("PULL", notes.first { it.id == "n3" }.workout) // case-insensitive workout type
    }

    @Test
    fun emptyFileHasNoNotes() {
        assertTrue(parseCoachNotesJson("""{"notes": []}""").isEmpty())
    }

    @Test
    fun dayNamesMapToWorkoutTypes() {
        assertEquals("PUSH", dayNameToWorkoutType("PUSH DAY"))
        assertEquals("LEGS", dayNameToWorkoutType("LEGS DAY"))
        assertEquals("PULL", dayNameToWorkoutType("PULL DAY"))
        assertEquals("CARDIO", dayNameToWorkoutType("CARDIO DAY"))
    }

    @Test
    fun currentNoteIsNewestForThatWorkoutType() {
        assertEquals("n3", latestCoachNote("PULL", notes)?.id)
        assertEquals("n2", latestCoachNote("PUSH", notes)?.id)
        assertNull(latestCoachNote("LEGS", notes))
    }

    @Test
    fun historyUsesSavedNoteIdWhenPresent() {
        val end = millis("2026-11-29T10:00:00-05:00")
        assertEquals("n1", coachNoteOfRecord("PULL DAY", end, "n1", notes)?.id)
    }

    @Test
    fun historyWithoutSavedIdUsesNoteThatExistedAtTheTime() {
        // Workout finished between n1 and n3 -> n1 was the note of record
        val end = millis("2026-11-25T10:00:00-05:00")
        assertEquals("n1", coachNoteOfRecord("PULL DAY", end, null, notes)?.id)
        // Workout before any pull note -> none
        assertNull(coachNoteOfRecord("PULL DAY", millis("2026-11-01T10:00:00Z"), null, notes))
    }

    @Test
    fun unknownSavedIdFallsBackToTimeRule() {
        val end = millis("2026-11-29T10:00:00-05:00")
        assertEquals("n3", coachNoteOfRecord("PULL DAY", end, "deleted-id", notes)?.id)
    }
}
