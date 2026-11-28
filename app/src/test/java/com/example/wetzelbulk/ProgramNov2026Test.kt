package com.example.wetzelbulk

import org.junit.Assert.*
import org.junit.Test

class ProgramNov2026Test {

    private fun log(id: Long, label: String, setNum: Int, weight: Double, block: String = "B", rest: Int = -1) =
        SetLog(logId = id, sessionId = 1, exerciseBlockTitle = block, exerciseLabel = label,
            setNum = setNum, weightAchieved = weight, repsAchieved = 10, restSeconds = rest)

    // --- P1: pre-fill per day + label ---

    @Test
    fun prefillPrefersSameSetNumberThenAnySet() {
        val dayLogs = listOf(log(3, "Leg Press", 3, 14.0), log(2, "Leg Press", 4, 13.0))
        assertEquals(13.0, findPrefillLog("Leg Press", 4, dayLogs)!!.weightAchieved, 0.0)
        assertEquals(14.0, findPrefillLog("Leg Press", 5, dayLogs)!!.weightAchieved, 0.0)
    }

    @Test
    fun legsLateralRaiseHasNoPrefillWithoutLegsHistory() {
        // Day logs for LEGS contain no Lat. Raise; the Push Day one must not leak in.
        val legsLogs = listOf(log(5, "Calf Raise", 1, 100.0), log(4, "Leg Press", 1, 14.0))
        assertNull(findPrefillLog("Lat. Raise", 1, legsLogs))
    }

    @Test
    fun startingLoadsMatchSpec() {
        assertEquals(mapOf("Flat Press" to 50.0, "DB Fly" to 35.0), getProgramStartingLoads("PUSH DAY"))
        assertEquals(mapOf("Leg Press" to 18.0, "Lat. Raise" to 20.0), getProgramStartingLoads("LEGS DAY"))
        assertEquals(
            mapOf("Lat Pull" to 15.0, "Pulldown" to 6.0, "Face Pull" to 6.0, "Hammer" to 35.0),
            getProgramStartingLoads("PULL DAY")
        )
        assertTrue(getProgramStartingLoads("CARDIO DAY").isEmpty())
    }

    // --- P1: notes copied to re-paired blocks ---

    @Test
    fun everyNewSupersetCopiesNotesFromItsOldBlocks() {
        listOf(
            "Superset: DB Fly / Rope Pushdown",
            "Superset: Lateral Raise / Skull Crusher",
            "Superset: Calf Raise / Lateral Raise",
            "Superset: Straight-Arm Pulldown / Incline DB Curl",
            "Superset: Face Pull / Hammer Curl"
        ).forEach { assertEquals(it, 2, getMergedNotesSources(it).size) }
        assertTrue(getMergedNotesSources("Incline Press (4 Sets)").isEmpty())
    }

    @Test
    fun mergedNotesKeepAllTextAndSkipBlanks() {
        assertEquals("", mergeCopiedNotes(listOf("A" to "", "B" to " ")))
        assertEquals("pin 4", mergeCopiedNotes(listOf("A" to "pin 4", "B" to "")))
        val both = mergeCopiedNotes(listOf("A" to "pin 4", "B" to "seat 3"))
        assertTrue(both.contains("pin 4") && both.contains("seat 3"))
        assertTrue(both.contains("[from A]") && both.contains("[from B]"))
    }

    // --- P4: history rest times ---

    @Test
    fun historyUsesRecordedRestWhenPresent() {
        val logs = listOf(log(1, "DB Fly", 1, 30.0, rest = 0), log(2, "Rope Push", 1, 40.0, rest = 75))
        assertEquals(75, getHistoricalBlockRestSeconds("Superset: DB Fly / Rope Pushdown", logs))
    }

    @Test
    fun historyFallsBackToV1RoutineForOldSets() {
        val old = listOf(log(1, "Face Pull", 1, 4.0))
        assertEquals(60, getHistoricalBlockRestSeconds("Superset: Straight-Arm Pulldown / Face Pull", old))
        assertEquals(75, getHistoricalBlockRestSeconds("Superset: Incline DB Curl / Hammer Curl", old))
        assertEquals(60, getHistoricalBlockRestSeconds("Leg Press Calf Raise (3 Sets)", old))
        assertEquals(120, getHistoricalBlockRestSeconds("Lat Pulldown (4 Sets)", old))
    }

    @Test
    fun historyShowsNoBadgeWhenIntendedRestIsUnknown() {
        assertNull(getLegacyBlockRestSeconds("Incline Chest / Lateral Raise Superset"))
        assertEquals(0, getHistoricalBlockRestSeconds("Incline Chest / Lateral Raise Superset", listOf(log(1, "Inc. Chest", 1, 40.0))))
    }
}
