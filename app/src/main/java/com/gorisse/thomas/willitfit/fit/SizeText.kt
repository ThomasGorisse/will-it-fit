package com.gorisse.thomas.willitfit.fit

import java.text.Normalizer

/**
 * The sides a piece of text gives, in centimetres; null where it gives none. Read from what a
 * person shares or selects in a listing ("Dimensions: 223 x 98 x 83 cm", "Largeur : 223 cm",
 * "87\"W x 37\"D x 33\"H"), in the app's six languages. Nothing is fetched: the text is all.
 */
data class TextSizes(val widthCm: Float?, val depthCm: Float?, val heightCm: Float?) {
    val count: Int get() = listOfNotNull(widthCm, depthCm, heightCm).size

    /** [dims] with the sides found here, typed in: the rest stay as they were. */
    fun applyTo(dims: Dimensions): Dimensions = Dimensions(
        widthCm = widthCm ?: dims.widthCm,
        depthCm = depthCm ?: dims.depthCm,
        heightCm = heightCm ?: dims.heightCm,
        source = DimensionSource.TYPED,
        presetId = null,
    )
}

/**
 * Finds the object's sizes in [text]. A number without a unit is in [defaultUnit].
 *
 * - A run of three numbers joined by "x", "×" or "by" is width × depth × height, unless letters
 *   say otherwise, on the numbers ("98P x 223L x 83H") or as an order ("(W x D x H)").
 * - Otherwise named sides: "Hauteur : 83 cm", "H 83", "83 cm high", "83 cm de haut".
 * - A run of two numbers ("160 x 200", a bed) is width × depth, unless named.
 *
 * Null when no side between [Dimensions.MIN_CM] and [Dimensions.MAX_CM] is found.
 */
fun sizesFromText(text: String, defaultUnit: LengthUnit): TextSizes? {
    val tokens = tokenize(text)
    val chains = chains(tokens)
    val order = orderHint(tokens)
    val sides = arrayOfNulls<Float>(3)

    fun put(role: Role, cm: Float) {
        if (cm !in Dimensions.MIN_CM..Dimensions.MAX_CM) return
        val i = role.index
        if (sides[i] == null) sides[i] = cm
    }

    val three = chains.firstOrNull { it.size == 3 }
    if (three != null) {
        assign(three, order).forEach { (role, item) -> put(role, item.cm(three, defaultUnit)) }
    } else {
        named(tokens).forEach { (role, m) -> put(role, m.cm(defaultUnit)) }
        chains.firstOrNull { it.size == 2 }?.let { two ->
            assign(two, order).forEach { (role, item) -> put(role, item.cm(two, defaultUnit)) }
        }
    }
    val found = TextSizes(sides[0], sides[1], sides[2])
    return found.takeIf { it.count > 0 }
}

// ---------------------------------------------------------------------------------------------

/** Which side a word names. LENGTH is the long side of a listing: width, or depth beside WIDTH. */
private enum class Role(val index: Int) { WIDTH(0), DEPTH(1), HEIGHT(2), LENGTH(0) }

private sealed interface Token {
    data class Num(val value: Float) : Token
    data class Per(val cmPer: Float) : Token
    data class Label(val role: Role, val letter: Boolean) : Token
    data object Times : Token
    data object Other : Token
}

private val UNITS: Map<String, Float> = buildMap {
    listOf("cm", "cms", "centimetre", "centimetres", "centimeter", "centimeters", "zentimeter", "centimetri", "centimetro", "centimetros")
        .forEach { put(it, 1f) }
    listOf("mm", "millimetre", "millimetres", "millimeter", "millimeters", "millimetri", "milimetros").forEach { put(it, 0.1f) }
    listOf("m", "metre", "metres", "meter", "meters", "metri", "metro", "metros").forEach { put(it, 100f) }
    listOf("in", "inch", "inches", "pouce", "pouces", "zoll", "pulgada", "pulgadas", "pollici", "pollice", "polegada", "polegadas")
        .forEach { put(it, LengthUnit.CM_PER_INCH) }
    listOf("ft", "feet", "foot").forEach { put(it, 30.48f) }
}

/** Whole words naming a side, accents removed, in the six languages. */
private val WORDS: Map<String, Role> = buildMap {
    listOf("width", "wide", "largeur", "large", "larg", "breite", "breit", "ancho", "anchura", "larghezza", "largh", "largura")
        .forEach { put(it, Role.WIDTH) }
    listOf("depth", "deep", "profondeur", "profond", "prof", "tiefe", "tief", "fondo", "profundidad", "profondita", "profundidade")
        .forEach { put(it, Role.DEPTH) }
    listOf("height", "high", "tall", "hauteur", "haut", "hohe", "hoehe", "hoch", "alto", "altura", "altezza")
        .forEach { put(it, Role.HEIGHT) }
    listOf("length", "long", "longueur", "lange", "laenge", "largo", "longitud", "lunghezza", "comprimento", "comp")
        .forEach { put(it, Role.LENGTH) }
}

/** Single letters naming a side: W/B width, D/P/T depth, H height, L length. */
private val LETTERS: Map<String, Role> = mapOf(
    "w" to Role.WIDTH, "b" to Role.WIDTH,
    "d" to Role.DEPTH, "p" to Role.DEPTH, "t" to Role.DEPTH,
    "h" to Role.HEIGHT,
    "l" to Role.LENGTH,
)

private val TIMES_WORDS = setOf("x", "by")

/** Small words between a size and its name, skipped: "83 cm de haut", "Largeur de 223 cm". */
private val FILLERS = setOf("de", "di", "of", "em")

private fun tokenize(text: String): List<Token> {
    val s = Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .lowercase()
        .replace('×', 'x').replace('✕', 'x').replace('✖', 'x').replace('*', 'x')
        .replace('″', '"').replace('”', '"').replace('“', '"').replace("''", "\"")
        .replace(' ', ' ').replace(' ', ' ')
    val out = mutableListOf<Token>()
    var i = 0
    while (i < s.length) {
        val c = s[i]
        when {
            c.isDigit() -> {
                var j = i
                while (j < s.length && s[j].isDigit()) j++
                if (j + 1 < s.length && (s[j] == ',' || s[j] == '.') && s[j + 1].isDigit()) {
                    j++
                    while (j < s.length && s[j].isDigit()) j++
                }
                out += Token.Num(s.substring(i, j).replace(',', '.').toFloat())
                i = j
            }
            c.isLetter() -> {
                var j = i
                while (j < s.length && s[j].isLetter()) j++
                word(s.substring(i, j), followedByDigit = j < s.length && s[j].isDigit(), out)
                i = j
            }
            c == '"' -> { out += Token.Per(LengthUnit.CM_PER_INCH); i++ }
            c.isWhitespace() || c == ':' || c == '=' || c == '.' || c == '(' || c == ')' -> i++
            else -> { out += Token.Other; i++ }
        }
    }
    return out
}

/** One run of letters. "px" or "cmx" before a digit is a label or unit glued to "x". */
private fun word(w: String, followedByDigit: Boolean, out: MutableList<Token>) {
    if (followedByDigit && w.length > 1 && w.endsWith('x')) {
        val head = w.dropLast(1)
        if (head in UNITS || head in LETTERS || head in WORDS) {
            word(head, followedByDigit = false, out); out += Token.Times; return
        }
    }
    if (w in FILLERS) return
    out += when {
        w in TIMES_WORDS -> Token.Times
        w in UNITS -> Token.Per(UNITS.getValue(w))
        w in LETTERS -> Token.Label(LETTERS.getValue(w), letter = true)
        w in WORDS -> Token.Label(WORDS.getValue(w), letter = false)
        else -> Token.Other
    }
}

/** One number of a chain, with the unit and the side letters written on it. */
private class Item(val value: Float, val cmPer: Float?, val role: Role?) {
    fun cm(chain: List<Item>, default: LengthUnit): Float =
        value * (cmPer ?: chain.lastOrNull { it.cmPer != null }?.cmPer ?: default.toCm(1f))
}

/** Numbers joined by "x": "223 x 98 x 83 cm", "W 87 x D 37 x H 33 in", "98P x 223L x 83H". */
private fun chains(tokens: List<Token>): List<List<Item>> {
    val found = mutableListOf<List<Item>>()
    var i = 0
    while (i < tokens.size) {
        val chain = mutableListOf<Item>()
        var j = i
        while (true) {
            val (item, next) = item(tokens, j) ?: break
            chain += item
            if (next < tokens.size && tokens[next] == Token.Times) j = next + 1 else { j = next; break }
        }
        // Bare numbers with nothing to say they are sizes ("2 x 3-seat") are not taken.
        val bare = chain.all { it.cmPer == null && it.role == null } && chain.any { it.value < 10f }
        if (chain.size in 2..3 && !bare) { found += chain; i = j } else i++
    }
    return found
}

/** [label] number [unit] [label] from [start], or null; and where it ends. */
private fun item(tokens: List<Token>, start: Int): Pair<Item, Int>? {
    var k = start
    var role = (tokens.getOrNull(k) as? Token.Label)?.role
    if (role != null) k++
    val num = tokens.getOrNull(k) as? Token.Num ?: return null
    k++
    val unit = (tokens.getOrNull(k) as? Token.Per)?.cmPer
    if (unit != null) k++
    val after = tokens.getOrNull(k) as? Token.Label
    var cmPer = unit
    if (after != null && after.letter && role == null) {
        role = after.role; k++
        // "98P x 223L x 83H centimetres": the unit after the last letter.
        if (cmPer == null) (tokens.getOrNull(k) as? Token.Per)?.let { cmPer = it.cmPer; k++ }
    }
    return Item(num.value, cmPer, role) to k
}

/** A written order such as "(W x D x H)" or "L x P x H", or null. */
private fun orderHint(tokens: List<Token>): List<Role>? {
    for (i in tokens.indices) {
        val roles = mutableListOf<Role>()
        var k = i
        while (true) {
            val label = tokens.getOrNull(k) as? Token.Label ?: break
            roles += label.role
            if (tokens.getOrNull(k + 1) == Token.Times) k += 2 else break
        }
        if (roles.size >= 2) return roles
    }
    return null
}

/** The sides of a chain: from its own letters, else from a written order, else W, D, H. */
private fun assign(chain: List<Item>, order: List<Role>?): List<Pair<Role, Item>> {
    val roles: List<Role?> = when {
        chain.all { it.role != null } -> chain.map { it.role }
        order != null && order.size == chain.size -> order
        else -> listOf(Role.WIDTH, Role.DEPTH, Role.HEIGHT).take(chain.size)
    }
    return resolve(roles).zip(chain)
}

/**
 * LENGTH becomes width, and a WIDTH beside it becomes depth ("L x l x H", "Longueur … Largeur").
 * A side named twice goes to the first side still free.
 */
private fun resolve(roles: List<Role?>): List<Role> {
    val hasLength = Role.LENGTH in roles
    val taken = mutableSetOf<Role>()
    return roles.map { r ->
        var role = when {
            r == Role.LENGTH -> Role.WIDTH
            r == Role.WIDTH && hasLength -> Role.DEPTH
            else -> r
        }
        if (role == null || role in taken) role = listOf(Role.WIDTH, Role.DEPTH, Role.HEIGHT).first { it !in taken }
        taken += role
        role
    }
}

/**
 * Sides named one by one: "Hauteur : 83 cm", "H 83", "83 cm high", "83 cm de haut". A single
 * letter counts only before its number: "300 l" is a fridge's litres, not a length. A word needs
 * a unit with its number.
 */
private fun named(tokens: List<Token>): List<Pair<Role, Measure>> {
    val raw = mutableListOf<Pair<Role, Measure>>()
    for (k in tokens.indices) {
        val label = tokens[k] as? Token.Label
        if (label != null) {
            val num = tokens.getOrNull(k + 1) as? Token.Num ?: continue
            if (tokens.getOrNull(k + 2) == Token.Times) continue
            val unit = (tokens.getOrNull(k + 2) as? Token.Per)?.cmPer
            // A word with a bare number is not a size ("Large 3 seater"); "H 85" is.
            if (unit == null && !label.letter) continue
            raw += label.role to Measure(num.value, unit)
            continue
        }
        val num = tokens[k] as? Token.Num ?: continue
        var j = k + 1
        val unit = (tokens.getOrNull(j) as? Token.Per)?.cmPer ?: continue
        j++
        val after = tokens.getOrNull(j) as? Token.Label ?: continue
        if (after.letter) continue
        if (tokens.getOrNull(j + 1) is Token.Num) continue // "… cm hauteur 83": the next one's label
        raw += after.role to Measure(num.value, unit)
    }
    val roles = resolve(raw.map { it.first })
    return roles.zip(raw.map { it.second })
}

private class Measure(val value: Float, val cmPer: Float?) {
    fun cm(default: LengthUnit): Float = value * (cmPer ?: default.toCm(1f))
}
