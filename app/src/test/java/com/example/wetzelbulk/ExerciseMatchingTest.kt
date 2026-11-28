package com.example.wetzelbulk

import org.junit.Assert.*
import org.junit.Test

class ExerciseMatchingTest {

    @Test
    fun testLatPullNeverMatchesStraightArmPulldown() {
        // Core bug reproduction test:
        // "Lat Pull" (Lat Pulldown) must NEVER match "Pulldown" (Straight-Arm Pulldown)
        assertFalse(isLabelMatch("Lat Pull", "Pulldown"))
        assertFalse(isLabelMatch("Pulldown", "Lat Pull"))
        assertFalse(isLabelMatch("Lat Pulldown", "Pulldown"))
        assertFalse(isLabelMatch("Pulldown", "Lat Pulldown"))
        assertFalse(isLabelMatch("Lat Pull", "Straight-Arm Pulldown"))
        assertFalse(isLabelMatch("Straight-Arm Pulldown", "Lat Pull"))
    }

    @Test
    fun testLatPullMatchesLegitimateLatPulldownAliases() {
        assertTrue(isLabelMatch("Lat Pull", "Lat Pull"))
        assertTrue(isLabelMatch("Lat Pull", "lat pull"))
        assertTrue(isLabelMatch("Lat Pull", "Lat Pulldown"))
        assertTrue(isLabelMatch("Lat Pull", "lat pulldown"))
        assertTrue(isLabelMatch("Lat Pull", "Lat Pulldown (4 Sets)"))
        assertTrue(isLabelMatch("Lat Pull", "lat pull down"))
        assertTrue(isLabelMatch("Lat Pull", "lat pull-down"))
    }

    @Test
    fun testStraightArmPulldownMatchesLegitimateAliases() {
        assertTrue(isLabelMatch("Pulldown", "Pulldown"))
        assertTrue(isLabelMatch("Pulldown", "pulldown"))
        assertTrue(isLabelMatch("Pulldown", "Straight-Arm Pulldown"))
        assertTrue(isLabelMatch("Pulldown", "straight-arm cable pulldown"))
        assertTrue(isLabelMatch("Pulldown", "straight arm pulldown"))
        assertTrue(isLabelMatch("Pulldown", "Superset: Straight-Arm Pulldown / Face Pull"))
    }

    @Test
    fun testLegPressDoesNotMatchCalfRaise() {
        assertFalse(isLabelMatch("Leg Press", "Leg Press Calf Raise"))
        assertFalse(isLabelMatch("Leg Press", "Calf Raise"))
        assertTrue(isLabelMatch("Calf Raise", "Leg Press Calf Raise"))
        assertTrue(isLabelMatch("Calf Raise", "Calf Raise"))
        assertTrue(isLabelMatch("Leg Press", "Leg Press"))
        assertTrue(isLabelMatch("Leg Press", "Leg Press (4 Sets)"))
    }

    @Test
    fun testOtherExerciseDistinctions() {
        assertFalse(isLabelMatch("Lat. Raise", "Lat Pull"))
        assertFalse(isLabelMatch("DB Fly", "DB Lateral Raise"))
        assertFalse(isLabelMatch("Inc. Press", "Incline DB Curl"))
        assertFalse(isLabelMatch("Rope Push", "Pulldown"))
        assertTrue(isLabelMatch("Inc. Press", "Incline DB Press (4 Sets)"))
        assertTrue(isLabelMatch("RDL", "Romanian Dead Lift (3 Sets)"))
        assertTrue(isLabelMatch("Crunch", "Machine Crunch"))
        assertTrue(isLabelMatch("Cable Row", "Seated Cable Row - V-Grip (4 Sets)"))
    }

    @Test
    fun testRepRanges() {
        assertEquals(Pair(8, 12), getExerciseRepRange("Lat Pull"))
        assertEquals(Pair(8, 12), getExerciseRepRange("Lat Pulldown"))
        assertEquals(Pair(10, 15), getExerciseRepRange("Pulldown"))
        assertEquals(Pair(10, 15), getExerciseRepRange("Straight-Arm Pulldown"))
        assertEquals(Pair(8, 12), getExerciseRepRange("Leg Press"))
        assertEquals(Pair(12, 20), getExerciseRepRange("Calf Raise"))
    }

    @Test
    fun testPullDayBaselineLogMatchingSimulation() {
        // Simulate logs from past Pull Day session ordered by logId DESC (reverse chronological):
        // 1. Hammer / Inc Curl (Block 4)
        // 2. Face Pull / Pulldown (Block 3) -> weight 30.0, reps 12
        // 3. Cable Row (Block 2)
        // 4. Lat Pull (Block 1) -> weight 95.0, reps 10
        val simulatedRecentLogs = listOf(
            SetLog(logId = 12, sessionId = 1, exerciseBlockTitle = "Superset: Incline DB Curl / Hammer Curl", exerciseLabel = "Hammer", setNum = 1, weightAchieved = 25.0, repsAchieved = 10),
            SetLog(logId = 11, sessionId = 1, exerciseBlockTitle = "Superset: Incline DB Curl / Hammer Curl", exerciseLabel = "Inc. Curl", setNum = 1, weightAchieved = 25.0, repsAchieved = 10),
            SetLog(logId = 10, sessionId = 1, exerciseBlockTitle = "Superset: Straight-Arm Pulldown / Face Pull", exerciseLabel = "Face Pull", setNum = 1, weightAchieved = 20.0, repsAchieved = 15),
            SetLog(logId = 9, sessionId = 1, exerciseBlockTitle = "Superset: Straight-Arm Pulldown / Face Pull", exerciseLabel = "Pulldown", setNum = 1, weightAchieved = 30.0, repsAchieved = 12),
            SetLog(logId = 8, sessionId = 1, exerciseBlockTitle = "Seated Cable Row - V-Grip (4 Sets)", exerciseLabel = "Cable Row", setNum = 1, weightAchieved = 90.0, repsAchieved = 10),
            SetLog(logId = 7, sessionId = 1, exerciseBlockTitle = "Lat Pulldown (4 Sets)", exerciseLabel = "Lat Pull", setNum = 1, weightAchieved = 95.0, repsAchieved = 10)
        )

        // Find match for live session Lat Pull set 1
        val latPullMatchingLog = simulatedRecentLogs.firstOrNull { log ->
            log.setNum == 1 && isLabelMatch("Lat Pull", log.exerciseLabel)
        } ?: simulatedRecentLogs.firstOrNull { log ->
            isLabelMatch("Lat Pull", log.exerciseLabel)
        }

        assertNotNull(latPullMatchingLog)
        assertEquals(7L, latPullMatchingLog!!.logId)
        assertEquals("Lat Pull", latPullMatchingLog.exerciseLabel)
        assertEquals(95.0, latPullMatchingLog.weightAchieved, 0.001)
        assertEquals(10, latPullMatchingLog.repsAchieved)

        // Find match for live session Pulldown set 1
        val pulldownMatchingLog = simulatedRecentLogs.firstOrNull { log ->
            log.setNum == 1 && isLabelMatch("Pulldown", log.exerciseLabel)
        } ?: simulatedRecentLogs.firstOrNull { log ->
            isLabelMatch("Pulldown", log.exerciseLabel)
        }

        assertNotNull(pulldownMatchingLog)
        assertEquals(9L, pulldownMatchingLog!!.logId)
        assertEquals("Pulldown", pulldownMatchingLog.exerciseLabel)
        assertEquals(30.0, pulldownMatchingLog.weightAchieved, 0.001)
        assertEquals(12, pulldownMatchingLog.repsAchieved)
    }
}
