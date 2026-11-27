package com.gorisse.thomas.willitfit.data

import com.gorisse.thomas.willitfit.fit.Boot
import com.gorisse.thomas.willitfit.fit.Dimensions
import com.gorisse.thomas.willitfit.fit.Door
import com.gorisse.thomas.willitfit.fit.Hall
import com.gorisse.thomas.willitfit.fit.Lift
import com.gorisse.thomas.willitfit.fit.SharedCheck
import com.gorisse.thomas.willitfit.fit.Space

/** What opening a check can touch: the object, the saved spaces, and which space is open. */
interface CheckStore {
    var dimensions: Dimensions
    var door: Door
    var boot: Boot
    var hall: Hall
    var lift: Lift
    var liftSelected: Boolean
    var carBoot: Boolean
    var hallway: Boolean
}

/** Takes a check as the person's own: the object, and the space it was checked against. */
fun CheckStore.takeCheck(check: SharedCheck) {
    dimensions = check.dims
    if (check.space != null) liftSelected = check.space == Space.LIFT
    when (check.space) {
        Space.DOOR -> { check.door?.let { door = it }; carBoot = false; hallway = false }
        Space.BOOT -> { check.boot?.let { boot = it }; carBoot = true; hallway = false }
        Space.LIFT -> { check.lift?.let { lift = it }; liftSelected = true }
        Space.HALL -> { check.hall?.let { hall = it }; hallway = true }
        null -> Unit
    }
}
