package com.gorisse.thomas.willitfit.fit

/** The silhouette drawn in 3D and AR. Built in code from boxes, cylinders and rings. */
enum class Shape { SOFA, BED, WARDROBE, FRIDGE, WASHER, TV, BIKE, STROLLER, BOX }

/**
 * Typical objects, at typical sizes — the user is told to check their own. Width × depth ×
 * height in centimetres, as the object stands. Sources noted per line (market sizes checked
 * 2026-11): they are starting points, not product sheets.
 */
enum class Preset(val id: String, val shape: Shape, val dimensions: Dimensions) {
    // Two-seat sofas sell between 150 and 190 cm wide, 85-95 deep, 80-90 high.
    SOFA_2("sofa2", Shape.SOFA, preset("sofa2", 170f, 90f, 85f)),
    // Three-seat: 200-240 wide.
    SOFA_3("sofa3", Shape.SOFA, preset("sofa3", 220f, 95f, 85f)),
    // Frame around a 140 × 190 mattress: about +10 cm each way, headboard included.
    BED_140("bed140", Shape.BED, preset("bed140", 150f, 205f, 95f)),
    // Frame around a 160 × 200 mattress.
    BED_160("bed160", Shape.BED, preset("bed160", 170f, 215f, 95f)),
    // Two-door wardrobe: the most sold modular frame is 100 × 58 × 201 cm.
    WARDROBE("wardrobe", Shape.WARDROBE, preset("wardrobe", 100f, 60f, 201f)),
    // Fridge-freezer: the European 60 cm module, 185-200 high.
    FRIDGE("fridge", Shape.FRIDGE, preset("fridge", 60f, 66f, 186f)),
    // Front-loading washing machine: 60 × 60 × 85 cm, the standard under-counter module.
    WASHER("washer", Shape.WASHER, preset("washer", 60f, 60f, 85f)),
    // 55" 16:9 panel is 121.8 × 68.5 cm; with bezel and stand, about 123 × 25 × 77.
    TV_55("tv55", Shape.TV, preset("tv55", 123f, 25f, 77f)),
    // 65" panel is 143.9 × 80.9 cm; with bezel and stand, about 145 × 29 × 90.
    TV_65("tv65", Shape.TV, preset("tv65", 145f, 29f, 90f)),
    // Adult city bike: 175-185 long, 60 at the handlebar, 100-110 high.
    BIKE("bike", Shape.BIKE, preset("bike", 178f, 60f, 105f)),
    // Open pram/stroller: about 60 wide, 90 long, 105 high at the handle.
    STROLLER("stroller", Shape.STROLLER, preset("stroller", 60f, 90f, 105f));

    companion object {
        fun byId(id: String?): Preset? = entries.firstOrNull { it.id == id }

        /** What the app opens on: the object people ask about most. */
        val DEFAULT = SOFA_3
    }
}

private fun preset(id: String, w: Float, d: Float, h: Float) =
    Dimensions(w, d, h, DimensionSource.PRESET, id)

/** The silhouette for any dimensions: the preset's own, or a plain box for typed sizes. */
val Dimensions.shape: Shape
    get() = Preset.byId(presetId)?.shape ?: Shape.BOX
