package com.gorisse.thomas.willitfit.fit

/** One answer of moving day, whatever the space: through, tight, or blocked, and by how much. */
data class Pass(val fits: Boolean, val isTight: Boolean, val marginCm: Float)

/** The spaces of the person's home, as last typed in the checks. */
data class Home(val door: Door, val hall: Hall, val boot: Boot)

/** One object against every space of the home. */
data class MovingDayRow(val door: Pass, val hall: Pass, val boot: Pass) {
    /** Through the door and round the turn: it gets into the home. The car is a separate trip. */
    val getsIn: Boolean get() = door.fits && hall.fits
}

fun movingDay(obj: Dimensions, home: Home): MovingDayRow = MovingDayRow(
    door = checkDoor(obj, home.door).let { Pass(it.fits, it.isTight, it.marginCm) },
    hall = checkHall(obj, home.hall).let { Pass(it.fits, it.isTight, it.marginCm) },
    boot = checkBoot(obj, home.boot).let { Pass(it.fits, it.isTight, it.marginCm) },
)
