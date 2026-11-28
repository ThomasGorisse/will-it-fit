package com.gorisse.thomas.willitfit.fit

/**
 * Centimetres. Generic shapes are typical planning values, not measurements of a particular car.
 * The estate uses RiDC's 2014 Peugeot 308 SW measurements except for its estimated usable height.
 * Data provenance and assumptions: CAR_TRUNK_MODE.md. Always measure the actual car.
 */
enum class BootPreset(val boot: Boot) {
    CITY(Boot(90f, 70f, 55f, 95f, 65f, 120f)),
    COMPACT(Boot(100f, 75f, 75f, 100f, 70f, 150f)),
    ESTATE(Boot(107.4f, 72.9f, 105.5f, 104.3f, 68f, 183.6f)),
    SUV(Boot(110f, 85f, 95f, 105f, 80f, 175f)),
    VAN(Boot(140f, 125f, 240f, 120f, 130f)),
}
