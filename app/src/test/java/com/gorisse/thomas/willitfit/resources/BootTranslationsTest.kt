package com.gorisse.thomas.willitfit.resources

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Read real resources so a new key or a newly shipped locale cannot silently fall back. */
class BootTranslationsTest {
    @Test fun everyFeatureStringExistsInEveryShippedLocaleWithMatchingFormatArguments() {
        val resources = resourceRoot()
        val defaults = readStrings(File(resources, "values"))
        val feature = defaults.filterKeys { key ->
            key.startsWith("boot_") || key.startsWith("carry_") || key.startsWith("space_boot") ||
                key.startsWith("tape_side_boot") || key.startsWith("photo_boot") ||
                key.startsWith("photo_failed") || key in setOf("door_share", "preset_custom", "action_back")
        }
        assertTrue("Feature resource selection must not be empty", feature.size > 80)
        val locales = resources.listFiles()!!.filter { it.isDirectory && it.name.startsWith("values-") && File(it, "strings.xml").isFile }
        assertTrue("No shipped locales found", locales.isNotEmpty())
        for (locale in locales) {
            val translated = readStrings(locale)
            assertEquals("${locale.name}: missing translations", emptySet<String>(), feature.keys - translated.keys)
            for ((key, english) in feature) {
                val value = translated.getValue(key)
                assertTrue("${locale.name}: empty $key", value.isNotBlank())
                assertEquals("${locale.name}: format arguments in $key", arguments(english), arguments(value))
            }
        }
    }

    private fun arguments(text: String) = Regex("%[0-9]+\\\$[a-zA-Z]").findAll(text).map { it.value }.sorted().toList()
}

internal fun resourceRoot(): File = listOf(File("src/main/res"), File("app/src/main/res"))
    .firstOrNull { File(it, "values/strings.xml").isFile } ?: error("Cannot locate app resource sources")

internal fun readStrings(folder: File): Map<String, String> {
    val factory = DocumentBuilderFactory.newInstance().apply {
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
    }
    return folder.listFiles()!!.filter { it.extension == "xml" }.flatMap { file ->
        val strings = factory.newDocumentBuilder().parse(file).getElementsByTagName("string")
        (0 until strings.length).map { i ->
            val node = strings.item(i)
            node.attributes.getNamedItem("name").nodeValue to node.textContent.replace("\\'", "'")
        }
    }.toMap()
}
