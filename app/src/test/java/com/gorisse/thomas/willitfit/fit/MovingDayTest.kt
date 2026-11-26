package com.gorisse.thomas.willitfit.fit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MovingDayTest {
    private val home = Home(Door(80f, 204f), Hall(90f, 90f, 240f), Boot.ESTATE)

    @Test fun eachSpaceGetsTheAnswerOfItsOwnCheck() {
        val sofa = Dimensions(220f, 95f, 85f)
        val row = movingDay(sofa, home)
        assertEquals(checkDoor(sofa, home.door).fits, row.door.fits)
        assertEquals(checkHall(sofa, home.hall).marginCm, row.hall.marginCm, 0.001f)
        assertEquals(checkBoot(sofa, home.boot).fits, row.boot.fits)
    }

    @Test fun aWasherGetsInAndIntoTheCar() {
        val row = movingDay(Dimensions(60f, 60f, 85f), home)
        assertTrue(row.getsIn)
        assertTrue(row.boot.fits)
    }

    @Test fun aWardrobeTallerThanTheCeilingDoesNotGetIn() {
        val row = movingDay(Dimensions(100f, 250f, 260f), home)
        assertFalse(row.getsIn)
    }

    @Test fun blockedThenTightThenTheRestKeepInsertionOrderWithinEachGroup() {
        val clear = Pass(true, false, 4f)
        val tight = Pass(true, true, 1f)
        val blocked = Pass(false, false, -12f)
        val rows = listOf(
            "Clear first" to MovingDayRow(clear, clear, clear),
            "Tight car" to MovingDayRow(clear, clear, tight),
            "Blocked turn" to MovingDayRow(clear, blocked, tight),
            "Car only blocked" to MovingDayRow(clear, clear, blocked),
            "Blocked door" to MovingDayRow(blocked, clear, clear),
            "Tight door" to MovingDayRow(tight, clear, clear),
            "Tight turn" to MovingDayRow(clear, tight, clear),
        )
        assertEquals(
            listOf("Blocked turn", "Blocked door", "Tight car", "Tight door", "Tight turn", "Clear first", "Car only blocked"),
            orderMovingDay(rows).map { it.first },
        )
        assertEquals(emptyList<Pair<String, MovingDayRow>>(), orderMovingDay<String>(emptyList()))
    }
}
