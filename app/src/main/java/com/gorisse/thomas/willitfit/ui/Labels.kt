package com.gorisse.thomas.willitfit.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.gorisse.thomas.willitfit.R
import com.gorisse.thomas.willitfit.fit.Carry
import com.gorisse.thomas.willitfit.fit.BootPreset
import com.gorisse.thomas.willitfit.fit.Dimensions
import com.gorisse.thomas.willitfit.fit.Preset

@StringRes
fun BootPreset.label(): Int = when (this) {
    BootPreset.CITY -> R.string.boot_preset_city
    BootPreset.COMPACT -> R.string.boot_preset_compact
    BootPreset.GENERIC_ESTATE -> R.string.boot_preset_estate
    BootPreset.SUV -> R.string.boot_preset_suv
    BootPreset.VAN -> R.string.boot_preset_van
    BootPreset.ESTATE -> R.string.boot_car_308_sw
    BootPreset.PEUGEOT_2008 -> R.string.boot_car_2008
    BootPreset.PEUGEOT_3008 -> R.string.boot_car_3008
    BootPreset.CLIO -> R.string.boot_car_clio
    BootPreset.CAPTUR -> R.string.boot_car_captur
    BootPreset.KANGOO -> R.string.boot_car_kangoo
    BootPreset.GOLF -> R.string.boot_car_golf
    BootPreset.TIGUAN -> R.string.boot_car_tiguan
    BootPreset.MODEL_Y -> R.string.boot_car_model_y
    BootPreset.MODEL_3 -> R.string.boot_car_model_3
    BootPreset.DUSTER -> R.string.boot_car_duster
    BootPreset.YARIS -> R.string.boot_car_yaris
    BootPreset.RAV4 -> R.string.boot_car_rav4
    BootPreset.BERLINGO -> R.string.boot_car_berlingo
    BootPreset.X1 -> R.string.boot_car_x1
    BootPreset.OCTAVIA -> R.string.boot_car_octavia
    BootPreset.CRV -> R.string.boot_car_crv
    BootPreset.TUCSON -> R.string.boot_car_tucson
    BootPreset.F150 -> R.string.boot_car_f150
    BootPreset.VAN_6M3 -> R.string.boot_car_van_6m3
}

@StringRes
fun Preset.label(): Int = when (this) {
    Preset.SOFA_2 -> R.string.preset_sofa2
    Preset.SOFA_3 -> R.string.preset_sofa3
    Preset.BED_140 -> R.string.preset_bed140
    Preset.BED_160 -> R.string.preset_bed160
    Preset.WARDROBE -> R.string.preset_wardrobe
    Preset.FRIDGE -> R.string.preset_fridge
    Preset.WASHER -> R.string.preset_washer
    Preset.TV_55 -> R.string.preset_tv55
    Preset.TV_65 -> R.string.preset_tv65
    Preset.BIKE -> R.string.preset_bike
    Preset.STROLLER -> R.string.preset_stroller
}

@DrawableRes
fun Preset.thumbnail(): Int = when (this) {
    Preset.SOFA_2 -> R.drawable.preset_sofa2
    Preset.SOFA_3 -> R.drawable.preset_sofa3
    Preset.BED_140 -> R.drawable.preset_bed140
    Preset.BED_160 -> R.drawable.preset_bed160
    Preset.WARDROBE -> R.drawable.preset_wardrobe
    Preset.FRIDGE -> R.drawable.preset_fridge
    Preset.WASHER -> R.drawable.preset_washer
    Preset.TV_55 -> R.drawable.preset_tv55
    Preset.TV_65 -> R.drawable.preset_tv65
    Preset.BIKE -> R.drawable.preset_bike
    Preset.STROLLER -> R.drawable.preset_stroller
}

/** "Sofa, 3 seats", or "Your object" for a typed box. */
@Composable
fun objectName(d: Dimensions): String =
    Preset.byId(d.presetId)?.let { stringResource(it.label()) } ?: stringResource(R.string.preset_custom)

@StringRes
fun Carry.label(): Int = when (this) {
    Carry.UPRIGHT -> R.string.carry_upright
    Carry.UPRIGHT_SIDEWAYS -> R.string.carry_upright_sideways
    Carry.ON_ITS_SIDE -> R.string.carry_on_side
    Carry.ON_ITS_SIDE_SIDEWAYS -> R.string.carry_on_side_sideways
    Carry.ON_ITS_BACK -> R.string.carry_on_back
    Carry.ON_ITS_BACK_SIDEWAYS -> R.string.carry_on_back_sideways
}
