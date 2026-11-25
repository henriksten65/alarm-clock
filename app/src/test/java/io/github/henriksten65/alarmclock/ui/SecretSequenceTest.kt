package io.github.henriksten65.alarmclock.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The keypad easter egg: only the exact sequence (as the last ten digits) triggers it. */
class SecretSequenceTest {

    private fun SecretSequence.pressAll(keys: List<String>) = keys.map { press(it) }

    private val secretKeys = "8908131553".map { it.toString() }

    @Test
    fun triggersOnTheLastKeyOfTheSequence() {
        val results = SecretSequence().pressAll(secretKeys)
        assertTrue(results.last())
        assertFalse(results.dropLast(1).any { it })
    }

    @Test
    fun earlierDigitsDontMatter() {
        assertTrue(SecretSequence().pressAll(listOf("1", "2") + secretKeys).last())
    }

    @Test
    fun doubleZeroKeyCountsAsTwoZeros() {
        // 8 9 0 8 1 3 1 5 5 3 can't use "00", but the key still feeds two digits:
        val s = SecretSequence()
        s.pressAll(listOf("00"))
        assertTrue(s.pressAll(secretKeys).last())
    }

    @Test
    fun backspaceOrAWrongDigitBreaksIt() {
        val s = SecretSequence()
        assertFalse(s.pressAll(secretKeys.take(5) + BACKSPACE_KEY + secretKeys.drop(5)).last())
        assertFalse(SecretSequence().pressAll(secretKeys.dropLast(1) + "4").last())
    }

    @Test
    fun theOtherTwoSequencesWorkToo() {
        for (code in listOf("9012272978", "9312300917")) {
            val results = SecretSequence().pressAll(code.map { it.toString() })
            assertTrue(code, results.last())
            assertFalse(code, results.dropLast(1).any { it })
        }
    }

    @Test
    fun sequencesDontMixAndMatch() {
        assertFalse(SecretSequence().pressAll("9012300917".map { it.toString() }).last())
    }

    @Test
    fun theOldNumberNoLongerWorks() {
        assertFalse(SecretSequence().pressAll("3551318098".map { it.toString() }).last())
    }
}
