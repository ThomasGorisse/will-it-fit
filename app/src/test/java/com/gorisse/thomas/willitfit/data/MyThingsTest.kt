package com.gorisse.thomas.willitfit.data

import com.gorisse.thomas.willitfit.fit.Dimensions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MyThingsTest {
    private val sofa = Dimensions(220f, 95f, 85f, presetId = "sofa3")

    @Test fun namesWithSeparatorsSurviveTheRoundTrip() {
        val things = listOf(Thing("Grandma's sofa | big, old\none", sofa), Thing("Frigo", Dimensions(60f, 66f, 186f)))
        val back = MyThings.decode(MyThings.encode(things))
        assertEquals(listOf("Grandma's sofa | big, old one", "Frigo"), back.map { it.name })
        assertEquals(220f, back[0].dims.widthCm)
        assertEquals("sofa3", back[0].dims.presetId)
    }

    @Test fun sameNameReplacesWhateverTheCase() {
        val one = MyThings.add(emptyList(), Thing("Sofa", sofa))
        val two = MyThings.add(one, Thing("sofa", Dimensions(200f, 90f, 80f)))
        assertEquals(1, two.size)
        assertEquals(200f, two[0].dims.widthCm)
    }

    @Test fun blankNamesAndInvalidSizesAreRefused() {
        assertTrue(MyThings.add(emptyList(), Thing("   ", sofa)).isEmpty())
        assertTrue(MyThings.add(emptyList(), Thing("Box", Dimensions(0f, 10f, 10f))).isEmpty())
    }

    @Test fun aDamagedLineKeepsTheOthers() {
        val stored = MyThings.encode(listOf(Thing("A", sofa))) + "\ngarbage\nB|1,2\n" + MyThings.encode(listOf(Thing("C", sofa)))
        assertEquals(listOf("A", "C"), MyThings.decode(stored).map { it.name })
    }

    @Test fun theListStopsAtItsMaximum() {
        var things = emptyList<Thing>()
        repeat(MyThings.MAX + 5) { things = MyThings.add(things, Thing("Thing $it", sofa)) }
        assertEquals(MyThings.MAX, things.size)
    }

    @Test fun removeTakesOnlyThatName() {
        val things = listOf(Thing("A", sofa), Thing("B", sofa))
        assertEquals(listOf("B"), MyThings.remove(things, "A").map { it.name })
    }
}
