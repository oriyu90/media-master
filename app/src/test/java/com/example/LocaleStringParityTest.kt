package com.example

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * Guards the "every locale has the same string keys" invariant the project
 * has maintained by hand since v0.2.0 (README/IMPLEMENTATION_AND_MAINTENANCE
 * call this out explicitly) but never enforced with a test. Only checks
 * `<string>` keys in `strings*.xml`; `plurals.xml` is intentionally excluded
 * since Arabic legitimately declares more quantity categories than the rest.
 */
class LocaleStringParityTest {

    private val resDir = File("src/main/res")
    private val locales = listOf("values", "values-ja", "values-zh", "values-ar", "values-nl")

    private fun stringKeys(localeDir: String): Set<String> {
        val dir = File(resDir, localeDir)
        val keys = mutableSetOf<String>()
        dir.listFiles { f -> f.name.startsWith("strings") && f.name.endsWith(".xml") }
            ?.forEach { file ->
                val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
                val nodes = doc.getElementsByTagName("string")
                for (i in 0 until nodes.length) {
                    val el = nodes.item(i) as Element
                    keys.add(el.getAttribute("name"))
                }
            }
        return keys
    }

    @Test
    fun `every locale declares the same set of string keys as the default`() {
        val defaultKeys = stringKeys("values")
        assertTrue("Default locale unexpectedly has no string keys — check the test's working directory", defaultKeys.isNotEmpty())
        for (locale in locales) {
            val keys = stringKeys(locale)
            val missing = defaultKeys - keys
            val extra = keys - defaultKeys
            assertTrue("$locale is missing keys: $missing", missing.isEmpty())
            assertTrue("$locale has extra keys not in default: $extra", extra.isEmpty())
        }
    }

    @Test
    fun `every locale has the same string key count`() {
        val counts = locales.associateWith { stringKeys(it).size }
        val distinctCounts = counts.values.toSet()
        assertEquals("Locale key counts differ: $counts", 1, distinctCounts.size)
    }
}
