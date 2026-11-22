package com.gorisse.thomas.willitfit.fit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SizeTextTest {

    private fun cm(text: String, unit: LengthUnit = LengthUnit.CM): Triple<Float?, Float?, Float?>? =
        sizesFromText(text, unit)?.let { Triple(it.widthCm, it.depthCm, it.heightCm) }

    private fun assertSides(w: Float?, d: Float?, h: Float?, text: String, unit: LengthUnit = LengthUnit.CM) {
        val got = cm(text, unit) ?: throw AssertionError("nothing found in: $text")
        listOf(w to got.first, d to got.second, h to got.third).forEachIndexed { i, (want, have) ->
            if (want == null) assertNull("side $i of: $text", have)
            else assertEquals("side $i of: $text", want, have!!, 0.05f)
        }
    }

    @Test
    fun `a plain run of three is width, depth, height`() {
        assertSides(223f, 98f, 83f, "Dimensions : 223 x 98 x 83 cm")
        assertSides(223f, 98f, 83f, "223×98×83cm")
        assertSides(223f, 98f, 83f, "Canapé 3 places, 223 X 98 X 83 cm, très bon état")
        assertSides(223f, 98f, 83f, "2,23 x 0,98 x 0,83 m")
        assertSides(60f, 60f, 85f, "600 x 600 x 850 mm")
    }

    @Test
    fun `letters on the numbers say which side is which`() {
        assertSides(223f, 98f, 83f, "Dimensions du produit : 98P x 223L x 83H centimètres")
        assertSides(87f * 2.54f, 37f * 2.54f, 33f * 2.54f, "Product Dimensions: 37\"D x 87\"W x 33\"H")
        assertSides(87f * 2.54f, 37f * 2.54f, 33f * 2.54f, "W 87 x D 37 x H 33 in")
        assertSides(223f, 98f, 83f, "L 223 x l 98 x H 83 cm")
        assertSides(223f, 98f, 83f, "B 223 x T 98 x H 83 cm")
    }

    @Test
    fun `a written order rearranges a plain run`() {
        assertSides(223f, 98f, 83f, "Maße (H x B x T): 83 x 223 x 98 cm")
        assertSides(223f, 98f, 83f, "L x P x H : 223 x 98 x 83 cm")
    }

    @Test
    fun `sides named one by one, in every language`() {
        assertSides(223f, 98f, 83f, "Largeur : 223 cm\nProfondeur : 98 cm\nHauteur : 83 cm")
        assertSides(223f, 98f, 83f, "Width: 223 cm Depth: 98 cm Height: 83 cm")
        assertSides(223f, 98f, 83f, "Breite: 223 cm, Tiefe: 98 cm, Höhe: 83 cm")
        assertSides(223f, 98f, 83f, "Ancho: 223 cm · Fondo: 98 cm · Alto: 83 cm")
        assertSides(223f, 98f, 83f, "Larghezza 223 cm, Profondità 98 cm, Altezza 83 cm")
        assertSides(223f, 98f, 83f, "Largura: 223 cm; Profundidade: 98 cm; Altura: 83 cm")
        assertSides(223f, 98f, 83f, "Longueur 223 cm, largeur 98 cm, hauteur 83 cm")
        assertSides(null, null, 83f, "Il fait 83 cm de haut")
        assertSides(223f, null, 83f, "223 cm wide and 83 cm high")
    }

    @Test
    fun `a bed's two sizes and its height`() {
        assertSides(160f, 200f, 95f, "Lit 160 x 200 cm, hauteur 95 cm")
        assertSides(160f, 200f, null, "Matelas 160x200")
    }

    @Test
    fun `numbers without a unit take the app's unit`() {
        assertSides(87f * 2.54f, 37f * 2.54f, 33f * 2.54f, "87 x 37 x 33", LengthUnit.INCH)
        assertSides(223f, 98f, 83f, "223 x 98 x 83")
    }

    @Test
    fun `text without a size gives nothing`() {
        assertNull(cm("Superbe canapé, à venir chercher sur place"))
        assertNull(cm("Large 3 seater sofa, 2 cushions"))
        assertNull(cm("Réfrigérateur 300 L, classe A"))
        assertNull(cm("2 x 3 places"))
        assertNull(cm("https://www.leboncoin.fr/ad/ameublement/2871234567"))
    }

    @Test
    fun `found sides replace only those sides, typed`() {
        val start = Preset.SOFA_3.dimensions
        val got = sizesFromText("hauteur 95 cm", LengthUnit.CM)!!.applyTo(start)
        assertEquals(Dimensions(start.widthCm, start.depthCm, 95f, DimensionSource.TYPED, null), got)
    }
}
