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
}
