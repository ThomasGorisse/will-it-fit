package com.gorisse.thomas.willitfit.ui

import com.gorisse.thomas.willitfit.data.Thing
import com.gorisse.thomas.willitfit.fit.Dimensions
import com.gorisse.thomas.willitfit.fit.LengthUnit
import com.gorisse.thomas.willitfit.fit.MovingDayRow
import com.gorisse.thomas.willitfit.fit.Pass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MovingDayShareTest {
    @Test fun measuredLiftAppearsInLocalizedChecklist() {
        val measured = row.copy(lift = Pass(false, false, -5f))
        val text = movingDayChecklist(listOf(Thing("Box", Dimensions(40f, 30f, 20f)) to measured), LengthUnit.CM,
            "porte", "virage", "coffre", false, lift = "ascenseur")
        assertTrue(text.contains("ascenseur ✗ −5 cm"))
        val unmeasured = movingDayChecklist(listOf(Thing("Box", Dimensions(40f, 30f, 20f)) to row), LengthUnit.CM,
            "porte", "virage", "coffre", false, lift = "ascenseur")
        assertTrue(!unmeasured.contains("ascenseur"))
    }

    private val row = MovingDayRow(Pass(true, false, 4f), Pass(false, false, -12f), Pass(true, false, 3f))

    @Test fun checklistIncludesNamesSizesSignedAnswersAndPlayLink() {
        val rows = listOf(
            Thing("Grandma's sofa", Dimensions(220f, 95f, 85f)) to row,
            Thing("Box", Dimensions(40f, 30f, 20f)) to row,
        )
        val lines = movingDayChecklist(rows, LengthUnit.CM, "door", "turn", "car", bootTyped = true).lines()
        assertEquals("Grandma's sofa — 220 × 95 × 85 cm · door ✓ +4 cm · turn ✗ −12 cm · car ✓ +3 cm", lines[0])
        assertTrue(lines[1].startsWith("Box — 40 × 30 × 20 cm"))
        assertEquals("", lines[2])
        assertEquals("https://play.google.com/store/apps/details?id=com.gorisse.thomas.willitfit", lines[3])
    }

    @Test fun checklistOmitsTheCarUntilItsBootIsMeasured() {
        val text = movingDayChecklist(
            listOf(Thing("Box", Dimensions(40f, 30f, 20f)) to row),
            LengthUnit.CM, "door", "turn", "car", bootTyped = false,
        )
        assertEquals(
            "Box — 40 × 30 × 20 cm · door ✓ +4 cm · turn ✗ −12 cm\n\n" +
                "https://play.google.com/store/apps/details?id=com.gorisse.thomas.willitfit",
            text,
        )
    }

    @Test fun checklistUsesThePersonsUnitAndLocalizedWords() {
        val inchRow = MovingDayRow(Pass(true, false, 5.08f), Pass(false, false, -2.54f), Pass(true, true, 0f))
        val text = movingDayChecklist(
            listOf(Thing("Carton", Dimensions(25.4f, 50.8f, 76.2f)) to inchRow),
            LengthUnit.INCH, "porte", "virage", "coffre", bootTyped = true,
        )
        assertTrue(text.startsWith("Carton — 10 × 20 × 30 in · porte ✓ +2 in · virage ✗ −1 in · coffre ✓ +0 in"))
    }
}
