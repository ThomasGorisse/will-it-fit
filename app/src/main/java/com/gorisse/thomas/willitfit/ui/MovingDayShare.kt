package com.gorisse.thomas.willitfit.ui

import com.gorisse.thomas.willitfit.data.Thing
import com.gorisse.thomas.willitfit.fit.LengthUnit
import com.gorisse.thomas.willitfit.fit.MovingDayRow
import com.gorisse.thomas.willitfit.fit.Pass
import com.gorisse.thomas.willitfit.fit.formatDimensions
import com.gorisse.thomas.willitfit.fit.formatMargin

/** Labels come from the current locale; the checklist follows the order shown on screen. */
fun movingDayChecklist(
    rows: List<Pair<Thing, MovingDayRow>>,
    unit: LengthUnit,
    door: String,
    turn: String,
    car: String,
    bootTyped: Boolean,
    lift: String = "Lift",
): String {
    fun answer(name: String, pass: Pass): String =
        "$name ${if (pass.fits) "✓" else "✗"} ${if (pass.marginCm < 0f) "−" else "+"}${formatMargin(pass.marginCm, unit)}"

    return rows.joinToString("\n") { (thing, row) ->
        "${thing.name} — ${formatDimensions(thing.dims, unit)} · " +
            listOfNotNull(
                answer(door, row.door), answer(turn, row.hall),
                row.lift?.let { answer(lift, it) },
                if (bootTyped) answer(car, row.boot) else null,
            ).joinToString(" · ")
    } + "\n\nhttps://play.google.com/store/apps/details?id=com.gorisse.thomas.willitfit"
}
