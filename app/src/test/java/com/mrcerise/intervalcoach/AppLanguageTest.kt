package com.mrcerise.intervalcoach

import com.mrcerise.intervalcoach.data.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Locale

/**
 * The language selector's contract with the resource folders and with stored preferences.
 *
 * Settings → Appearance lets a user pick a language instead of following the device, and that choice
 * survives a restart by being stored as the enum's *name* in DataStore, while it reaches the resource
 * system as a BCP-47 tag. Both halves are easy to break quietly: a tag that does not match a packaged
 * `values-…` folder shows English with no error anywhere, and a renamed enum entry silently resets
 * the user's choice. These are the two things asserted here, plus the one rule the translation
 * pipeline must keep — a table may only define keys the English source declares, otherwise a string
 * exists in French with nothing to fall back to.
 */
class AppLanguageTest {

    /** Gradle runs the test task from the module directory, like the schema check in DatabaseMigrationTest. */
    private val resRoot = File("src/main/res")

    @Test
    fun `following the system carries no tag at all`() {
        assertNull("SYSTEM must mean no override at all", AppLanguage.SYSTEM.tag)
    }

    @Test
    fun `every offered language is a packaged values folder`() {
        val folders = resRoot.listFiles { file -> file.isDirectory }.orEmpty().map { it.name }.toSet()
        assertTrue("no resource directories found from ${File(".").absolutePath}", folders.isNotEmpty())
        // English is the default table, so it has no folder of its own.
        assertEquals("en", AppLanguage.ENGLISH.tag)
        assertTrue("the default table must be values/", "values" in folders)
        val missing = AppLanguage.entries
            .filter { it != AppLanguage.SYSTEM && it != AppLanguage.ENGLISH }
            .filterNot { it.tag!!.toValuesFolder() in folders }
            .map { "${it.name} → ${it.tag!!.toValuesFolder()}" }
        assertTrue("offered with no matching resource folder: $missing", missing.isEmpty())
    }

    @Test
    fun `each tag is well formed and keeps the region the folder encodes`() {
        AppLanguage.entries.forEach { language ->
            val tag = language.tag ?: return@forEach
            val locale = Locale.forLanguageTag(tag)
            // A malformed tag decodes to the empty "und" locale rather than throwing.
            assertTrue("$tag is not a usable language tag", locale.language.isNotEmpty())
            val parts = tag.split("-")
            assertEquals(parts[0].lowercase(), locale.language)
            assertEquals(parts.getOrNull(1)?.uppercase().orEmpty(), locale.country)
        }
    }

    @Test
    fun `stored names are stable because they are what DataStore holds`() {
        assertEquals(
            listOf("SYSTEM", "ENGLISH", "FRENCH", "GERMAN", "SPANISH", "ITALIAN"),
            AppLanguage.entries.map { it.name },
        )
    }

    @Test
    fun `no two languages offer the same tag`() {
        val tags = AppLanguage.entries.mapNotNull { it.tag }
        assertEquals(tags.size, tags.toSet().size)
    }

    @Test
    fun `a translation table never declares a key the English source does not have`() {
        val english = stringNames(File(resRoot, "values/strings.xml"))
        assertTrue("the English source table is empty", english.isNotEmpty())
        val translations = resRoot.listFiles { file -> file.isDirectory && file.name.startsWith("values-") }
            .orEmpty()
            .map { File(it, "strings.xml") }
            .filter { it.isFile }
        assertTrue("no translation table was found", translations.isNotEmpty())
        translations.forEach { table ->
            val extra = stringNames(table) - english
            assertTrue("${table.parentFile.name} declares keys with no English source: $extra", extra.isEmpty())
        }
    }

    private fun stringNames(table: File): Set<String> =
        Regex("name=\"([A-Za-z0-9_]+)\"").findAll(table.readText()).map { it.groupValues[1] }.toSet()

    /** `de-DE` → `values-de-rDE`, `fr` → `values-fr`: the qualifier Android matches a locale against. */
    private fun String.toValuesFolder(): String {
        val parts = split("-")
        val region = parts.getOrNull(1)?.uppercase()
        return if (region == null) "values-${parts[0].lowercase()}" else "values-${parts[0].lowercase()}-r$region"
    }
}
