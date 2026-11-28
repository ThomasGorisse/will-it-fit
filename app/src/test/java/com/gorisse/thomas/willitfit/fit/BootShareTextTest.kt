package com.gorisse.thomas.willitfit.fit

import com.gorisse.thomas.willitfit.resources.readStrings
import com.gorisse.thomas.willitfit.resources.resourceRoot
import java.io.File
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BootShareTextTest {
    private val straight = BootVerdict(true, Carry.UPRIGHT, 14f, BootLimit.WIDTH)
    private val car = "Peugeot 308 SW"
    private fun labels(folder: String = "values"): BootShareLabels {
        val strings = readStrings(File(resourceRoot(), folder))
        return BootShareLabels(
            strings.getValue("boot_share_fits"), strings.getValue("boot_share_misses"),
            strings.getValue("boot_share_diagonal"), strings.getValue("boot_share_seats_down"),
            strings.getValue("boot_share_seats_down_diagonal"), strings.getValue("boot_share_spare"),
            strings.getValue("boot_share_over"), strings.getValue("boot_share_signature"),
        )
    }
    private fun text(verdict: BootVerdict, unit: LengthUnit = LengthUnit.CM) =
        bootShareText(car, verdict, unit, labels(), Locale.ENGLISH)

    @Test fun straightFitCaptionNamesTheCarAndClearance() {
        assertEquals("Fits in Peugeot 308 SW · 14 cm to spare · Checked with Will It Fit", text(straight).caption)
    }
    @Test fun missUsesAbsoluteExcessAndNeverClaimsSpareRoomOrFolding() {
        val result = text(straight.copy(fits = false, marginCm = -14f, angleDeg = 25f, rearSeatsDown = true))
        assertEquals("Does not fit in Peugeot 308 SW · 14 cm too big · Checked with Will It Fit", result.caption)
    }
    @Test fun diagonalAndFoldedConditionsSurviveIndividuallyAndTogether() {
        assertEquals("Fits diagonally in Peugeot 308 SW", text(straight.copy(angleDeg = 25f)).title)
        assertEquals("Fits in Peugeot 308 SW with rear seats down", text(straight.copy(rearSeatsDown = true)).title)
        assertEquals("Fits diagonally in Peugeot 308 SW with rear seats down", text(straight.copy(angleDeg = 25f, rearSeatsDown = true)).title)
    }
    @Test fun zeroClearanceAndInchesUseTheExistingMarginPrecision() {
        assertEquals("0 cm to spare", text(straight.copy(marginCm = 0f)).clearance)
        assertEquals("2 in to spare", text(straight.copy(marginCm = 5.08f), LengthUnit.INCH).clearance)
        assertEquals("0.4 cm too big", text(straight.copy(fits = false, marginCm = -0.4f)).clearance)
    }
    @Test fun localizedDecimalAndNonEnglishTemplatesAreUsed() {
        val result = bootShareText(car, straight.copy(marginCm = 0.4f), LengthUnit.CM, labels("values-fr"), Locale.FRENCH)
        assertEquals("Rentre dans Peugeot 308 SW · 0,4 cm de marge · Vérifié avec Will It Fit", result.caption)
    }
    @Test fun everyShippedLocaleFormatsEveryOutcomeWithoutUnresolvedArguments() {
        val locales = mapOf("values" to "en", "values-de" to "de", "values-es" to "es", "values-fr" to "fr", "values-it" to "it", "values-pt-rBR" to "pt-BR")
        val outcomes = listOf(straight, straight.copy(fits = false, marginCm = -7f), straight.copy(angleDeg = 25f),
            straight.copy(rearSeatsDown = true), straight.copy(rearSeatsDown = true, angleDeg = 25f))
        locales.forEach { (folder, tag) ->
            outcomes.forEach { verdict ->
                val result = bootShareText(car, verdict, LengthUnit.CM, labels(folder), Locale.forLanguageTag(tag))
                assertTrue(result.title.contains(car))
                assertFalse(result.caption.contains("%1\$s"))
                if (folder != "values") assertFalse(result.caption.contains("Checked with"))
            }
        }
    }
}
