package ru.ytkab0bp.beamklipper.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChangelogTextTest {
    private val json = """{"en": "English text", "pt": "Texto em português"}"""

    @Test
    fun `the device language is used when the file has it`() {
        assertEquals("Texto em português", ChangelogText.pick(json, "pt"))
    }

    @Test
    fun `other languages fall back to English`() {
        assertEquals("English text", ChangelogText.pick(json, "ru"))
        assertEquals("English text", ChangelogText.pick(json, ""))
    }

    @Test
    fun `a file without English and without the language has no text`() {
        assertNull(ChangelogText.pick("""{"pt": "x"}""", "ru"))
    }

    @Test
    fun `a broken file has no text`() {
        assertNull(ChangelogText.pick("not json", "en"))
        assertNull(ChangelogText.pick("", "en"))
    }
}
