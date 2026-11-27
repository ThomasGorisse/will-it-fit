package com.gorisse.thomas.willitfit.data

import com.gorisse.thomas.willitfit.fit.Boot
import com.gorisse.thomas.willitfit.fit.Dimensions
import com.gorisse.thomas.willitfit.fit.Door
import com.gorisse.thomas.willitfit.fit.Hall
import com.gorisse.thomas.willitfit.fit.Lift
import com.gorisse.thomas.willitfit.fit.SharedCheck
import com.gorisse.thomas.willitfit.fit.Space

/** What opening a check sees: the object, the saved spaces, and which space is open. */
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

/**
 * Opens a check: its object becomes the one on screen, and the space it was checked against is
 * the one open. The sender's door, boot, hallway or lift is only shown, from the check itself:
 * the home the person saved stays theirs until they edit it.
 */
fun CheckStore.takeCheck(check: SharedCheck) {
    dimensions = check.dims
    when (check.space) {
        Space.DOOR -> { liftSelected = false; carBoot = false; hallway = false }
        Space.BOOT -> { liftSelected = false; carBoot = true; hallway = false }
        Space.LIFT -> liftSelected = true
        Space.HALL -> { liftSelected = false; hallway = true }
        null -> Unit
    }
}
