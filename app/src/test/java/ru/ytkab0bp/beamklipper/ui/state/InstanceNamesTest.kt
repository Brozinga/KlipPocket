package ru.ytkab0bp.beamklipper.ui.state

import org.junit.Assert.assertEquals
import org.junit.Test

class InstanceNamesTest {
    private val template: (Int) -> String = { "Printer $it" }

    @Test
    fun `a trailing number is stripped`() {
        assertEquals("Printer", InstanceNames.stripTrailingNumber("Printer 3"))
        assertEquals("Neptune 3 Pro", InstanceNames.stripTrailingNumber("Neptune 3 Pro 2"))
    }

    @Test
    fun `a trailing number in parentheses is stripped`() {
        assertEquals("Printer", InstanceNames.stripTrailingNumber("Printer (3)"))
        assertEquals("Printer", InstanceNames.stripTrailingNumber("Printer ( 12 )"))
    }

    @Test
    fun `a name without a trailing number is kept`() {
        assertEquals("Ender", InstanceNames.stripTrailingNumber("  Ender  "))
        assertEquals("Neptune 3 Pro", InstanceNames.stripTrailingNumber("Neptune 3 Pro"))
    }

    @Test
    fun `the first default name is Printer 1`() {
        assertEquals("Printer 1", InstanceNames.defaultName(template, emptyList()))
    }

    @Test
    fun `the default name skips the ones in use`() {
        assertEquals("Printer 3", InstanceNames.defaultName(template, listOf("Printer 1", "Printer 2")))
        assertEquals("Printer 2", InstanceNames.defaultName(template, listOf("Printer 1", "Printer 3")))
    }

    @Test
    fun `default names come from the localized template`() {
        assertEquals("Impressora 1", InstanceNames.defaultName({ "Impressora $it" }, emptyList()))
    }

    @Test
    fun `a template that never varies falls back to a numbered base name`() {
        assertEquals("Printer 1", InstanceNames.defaultName({ "Printer" }, listOf("Printer")))
        assertEquals("Printer 2", InstanceNames.defaultName({ "Printer" }, listOf("Printer", "Printer 1")))
    }

    @Test
    fun `when a thousand names are taken the first one is returned`() {
        val taken = (1..1000).map { "Printer $it" }
        assertEquals("Printer 1", InstanceNames.defaultName(template, taken))
    }

    @Test
    fun `a free name is kept as typed`() {
        assertEquals("Voron", InstanceNames.unique("Voron", listOf("Ender"), { "Printer 1" }))
    }

    @Test
    fun `a name in use gets the next number`() {
        assertEquals("Voron 2", InstanceNames.unique("Voron", listOf("Voron"), { "Printer 1" }))
        assertEquals("Voron 3", InstanceNames.unique("Voron", listOf("Voron", "Voron 2"), { "Printer 1" }))
    }

    @Test
    fun `numbering restarts from the name without its number`() {
        assertEquals("Voron 3", InstanceNames.unique("Voron 2", listOf("Voron 2", "Voron"), { "Printer 1" }))
    }

    @Test
    fun `an empty name becomes the default one`() {
        assertEquals("Printer 4", InstanceNames.unique("", listOf("x"), { "Printer 4" }))
    }

    @Test
    fun `the profile being edited keeps its own name when the others are not in conflict`() {
        // the caller leaves the edited profile's own name out of the taken list
        assertEquals("Voron", InstanceNames.unique("Voron", listOf("Ender"), { "Printer 1" }))
    }

    @Test
    fun `the example config is preselected`() {
        assertEquals("example-cartesian.cfg", InstanceNames.defaultConfig(listOf("generic-a.cfg", "example-cartesian.cfg")))
        assertEquals("EXAMPLE-corexy.cfg", InstanceNames.defaultConfig(listOf("printer-x.cfg", "EXAMPLE-corexy.cfg")))
    }

    @Test
    fun `without an example the first config is preselected`() {
        assertEquals("a.cfg", InstanceNames.defaultConfig(listOf("a.cfg", "b.cfg")))
        assertEquals(null, InstanceNames.defaultConfig(emptyList()))
    }
}
