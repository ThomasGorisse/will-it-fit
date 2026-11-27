package com.gorisse.thomas.willitfit.data

import com.gorisse.thomas.willitfit.fit.Boot
import com.gorisse.thomas.willitfit.fit.Dimensions
import com.gorisse.thomas.willitfit.fit.Door
import com.gorisse.thomas.willitfit.fit.Hall
import com.gorisse.thomas.willitfit.fit.Lift
import com.gorisse.thomas.willitfit.fit.Preset
import com.gorisse.thomas.willitfit.fit.SharedCheck
import com.gorisse.thomas.willitfit.fit.Space
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TakeCheckTest {
    private class Store : CheckStore {
        override var dimensions = Preset.SOFA_3.dimensions
        override var door = Door(83f, 201f)
        override var boot = Boot.ESTATE
        override var hall = Hall(95f, 92f, 250f)
        override var lift = Lift.TYPICAL
        override var liftSelected = false
        override var carBoot = false
        override var hallway = false
    }

    private val fridge = Dimensions(60f, 65f, 185f)

    @Test fun aSharedCheckNeverOverwritesTheSavedSpaces() {
        val sender = mapOf(
            Space.DOOR to SharedCheck(fridge, Space.DOOR, door = Door(70f, 200f)),
            Space.BOOT to SharedCheck(fridge, Space.BOOT, boot = Boot.ESTATE.copy(lengthCm = 99f)),
            Space.HALL to SharedCheck(fridge, Space.HALL, hall = Hall(80f, 81f, 240f)),
            Space.LIFT to SharedCheck(fridge, Space.LIFT, lift = Lift.TYPICAL.copy(doorWidthCm = 70f)),
        )
        for ((space, check) in sender) {
            val store = Store()
            val own = listOf(store.door, store.boot, store.hall, store.lift)
            store.takeCheck(check)
            assertEquals("$space link", own, listOf(store.door, store.boot, store.hall, store.lift))
        }
    }

    @Test fun aSharedCheckOpensItsObjectAndSpace() {
        val store = Store()
        store.takeCheck(SharedCheck(fridge, Space.LIFT, lift = Lift.TYPICAL.copy(doorWidthCm = 70f)))
        assertEquals(fridge, store.dimensions)
        assertTrue(store.liftSelected)
        store.takeCheck(SharedCheck(fridge, Space.BOOT, boot = Boot.ESTATE))
        assertTrue(store.carBoot && !store.liftSelected && !store.hallway)
    }
}
