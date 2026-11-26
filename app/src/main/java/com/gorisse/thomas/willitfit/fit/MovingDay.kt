package com.gorisse.thomas.willitfit.fit

/** One answer of moving day, whatever the space: through, tight, or blocked, and by how much. */
data class Pass(val fits: Boolean, val isTight: Boolean, val marginCm: Float)

/** The home's spaces; a lift is included only once measured. */
data class Home(val door: Door, val hall: Hall, val boot: Boot, val lift: Lift? = null)

/** One object against every space of the home. */
data class MovingDayRow(val door: Pass, val hall: Pass, val boot: Pass, val lift: Pass? = null) {
    /** Into the home, including the lift when measured. The car is a separate trip. */
    val getsIn: Boolean get() = door.fits && hall.fits && (lift?.fits != false)
}

fun movingDay(obj: Dimensions, home: Home): MovingDayRow = MovingDayRow(
    door = checkDoor(obj, home.door).let { Pass(it.fits, it.isTight, it.marginCm) },
    hall = checkHall(obj, home.hall).let { Pass(it.fits, it.isTight, it.marginCm) },
    lift = home.lift?.let { checkLift(obj, it) }?.let { Pass(it.fits, it.isTight, it.marginCm) },
    boot = checkBoot(obj, home.boot).let { Pass(it.fits, it.isTight, it.marginCm) },
)

/** Stable groups keep the person's insertion order while bringing problems to the top. */
fun <T> orderMovingDay(rows: List<Pair<T, MovingDayRow>>, bootTyped: Boolean): List<Pair<T, MovingDayRow>> =
    rows.sortedBy { (_, row) ->
        when {
            !row.getsIn -> 0
            row.door.isTight || row.hall.isTight || row.lift?.isTight == true || (bootTyped && row.boot.isTight) -> 1
            else -> 2
        }
    }
