package io.github.henriksten65.alarmclock

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The translations (sv, de, fr, es, pt): every string exists in every language with the same
 * placeholders, plurals have the quantities the language needs, and everything formats at runtime.
 */
@RunWith(AndroidJUnit4::class)
class TranslationsTest {

    private val languages = listOf("sv", "de", "fr", "es", "pt")

    /** Plural quantities Android uses for each language (CLDR); French, Spanish and Portuguese also have "many". */
    private val pluralQuantities = mapOf(
        "en" to setOf("one", "other"), "sv" to setOf("one", "other"), "de" to setOf("one", "other"),
        "fr" to setOf("one", "many", "other"), "es" to setOf("one", "many", "other"), "pt" to setOf("one", "many", "other"),
    )

    @Before
    fun setUp() = TestSupport.resetAppState()

    /** name (or "name/quantity" for plural items) → text, from res/values[-lang]/strings.xml. */
    private fun strings(lang: String?): Map<String, String> {
        val dir = if (lang == null) "values" else "values-$lang"
        val file = listOf("src/main/res/$dir/strings.xml", "app/src/main/res/$dir/strings.xml").map(::File).first { it.exists() }
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val result = linkedMapOf<String, String>()
        val strings = doc.getElementsByTagName("string")
        for (i in 0 until strings.length) (strings.item(i) as Element).let { result[it.getAttribute("name")] = it.textContent }
        val plurals = doc.getElementsByTagName("plurals")
        for (i in 0 until plurals.length) {
            val p = plurals.item(i) as Element
            val items = p.getElementsByTagName("item")
            for (j in 0 until items.length) (items.item(j) as Element).let {
                result["${p.getAttribute("name")}/${it.getAttribute("quantity")}"] = it.textContent
            }
        }
        return result
    }

    private fun placeholders(text: String) = Regex("""%(\d+)\$[sd]""").findAll(text).map { it.value }.sorted().toList()

    @Test
    fun everyLanguageHasEveryStringWithTheSamePlaceholders() {
        val english = strings(null)
        val englishNames = english.keys.map { it.substringBefore('/') }.toSet()
        for (lang in languages) {
            val translated = strings(lang)
            assertEquals("$lang: same strings as English", englishNames, translated.keys.map { it.substringBefore('/') }.toSet())
            for ((key, text) in translated) {
                val englishText = english[key] ?: english["${key.substringBefore('/')}/other"]!!
                assertEquals("$lang $key placeholders", placeholders(englishText), placeholders(text))
                assertTrue("$lang $key is empty", text.isNotBlank())
            }
        }
    }

    @Test
    fun pluralsHaveTheQuantitiesEachLanguageNeeds() {
        for (lang in listOf(null) + languages) {
            val plurals = strings(lang).keys.filter { '/' in it }.groupBy({ it.substringBefore('/') }, { it.substringAfter('/') })
            assertTrue(plurals.isNotEmpty())
            plurals.forEach { (name, quantities) ->
                assertEquals("${lang ?: "en"} $name", pluralQuantities.getValue(lang ?: "en"), quantities.toSet())
            }
        }
    }

    @Test
    fun everyStringFormatsInEveryLanguage() {
        val res = TestSupport.context.resources
        val english = strings(null)
        for (lang in listOf("en") + languages) {
            RuntimeEnvironment.setQualifiers("$lang-w411dp-h891dp")
            val r = TestSupport.context.resources
            for (field in R.string::class.java.fields) {
                val name = field.name
                val text = english[name] ?: continue // library strings
                val args: Array<Any> = placeholders(text).map<String, Any> { if (it.endsWith("d")) 3 else "x" }.toTypedArray()
                r.getString(field.getInt(null), *args)
            }
            for (field in R.plurals::class.java.fields) {
                for (n in listOf(0, 1, 2, 5, 1_000_000)) r.getQuantityString(field.getInt(null), n, n, 2, 3)
            }
        }
        assertTrue(res !== null)
    }

    @Test
    @Config(qualifiers = "de")
    fun appRunsInGerman() {
        assertEquals("Wecker hinzufügen", TestSupport.context.getString(R.string.add_alarm))
        assertEquals("Wecker klingelt in 1 Tag 2 Std. 3 Min.",
            TestSupport.context.resources.getQuantityString(R.plurals.alarm_set_in_days, 1, 1, 2, 3))
        assertEquals("Wecker klingelt in 2 Tagen 2 Std. 3 Min.",
            TestSupport.context.resources.getQuantityString(R.plurals.alarm_set_in_days, 2, 2, 2, 3))
    }

    @Test
    @Config(qualifiers = "fr")
    fun frenchPluralsTreatZeroAndOneAsSingular() {
        val r = TestSupport.context.resources
        assertEquals("A sonné pendant 1 minute sans être arrêtée", r.getQuantityString(R.plurals.missed_alarm_text, 1, 1))
        assertEquals("A sonné pendant 10 minutes sans être arrêtée", r.getQuantityString(R.plurals.missed_alarm_text, 10, 10))
    }

    @Test
    @Config(qualifiers = "sv")
    fun swedishAppName() {
        assertNotEquals("Alarm Clock", TestSupport.context.getString(R.string.app_name))
    }
}
