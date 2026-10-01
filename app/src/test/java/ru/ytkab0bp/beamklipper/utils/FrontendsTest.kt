package ru.ytkab0bp.beamklipper.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FrontendsTest {
    @Test
    fun `each front end has its documented port`() {
        assertEquals(4408, Frontends.portFor(Prefs.FRONTEND_FLUIDD))
        assertEquals(4409, Frontends.portFor(Prefs.FRONTEND_MAINSAIL))
        assertEquals(4410, Frontends.portFor(Prefs.FRONTEND_VOYAGER))
    }

    @Test
    fun `ports never collide`() {
        val ports = Frontends.ALL.map { Frontends.portFor(it) }
        assertEquals(ports.size, ports.toSet().size)
    }

    @Test
    fun `unknown front end falls back to Mainsail`() {
        assertEquals(Frontends.PORT_MAINSAIL, Frontends.portFor("something-else"))
        assertEquals(Frontends.nameRes(Prefs.FRONTEND_MAINSAIL), Frontends.nameRes("something-else"))
    }

    @Test
    fun `ports stay unprivileged and clear of the camera port`() {
        Frontends.ALL.forEach {
            val port = Frontends.portFor(it)
            assertTrue("$it uses a privileged port", port > 1024)
            assertNotEquals("$it collides with the camera server", 8889, port)
        }
    }

    @Test
    fun `cycle visits every front end and comes back`() {
        assertEquals(Prefs.FRONTEND_MAINSAIL, Frontends.next(Prefs.FRONTEND_FLUIDD))
        assertEquals(Prefs.FRONTEND_VOYAGER, Frontends.next(Prefs.FRONTEND_MAINSAIL))
        assertEquals(Prefs.FRONTEND_FLUIDD, Frontends.next(Prefs.FRONTEND_VOYAGER))
    }

    @Test
    fun `cycle restarts from an unknown value`() {
        assertEquals(Prefs.FRONTEND_FLUIDD, Frontends.next("kalico_frontend"))
    }

    @Test
    fun `stored ids match the asset folders the build bundles`() {
        assertEquals(listOf("fluidd", "mainsail", "voyager"), Frontends.ALL)
    }

    @Test
    fun `name and icon differ for every front end`() {
        assertEquals(Frontends.ALL.size, Frontends.ALL.map { Frontends.nameRes(it) }.toSet().size)
        assertEquals(Frontends.ALL.size, Frontends.ALL.map { Frontends.iconRes(it) }.toSet().size)
    }
}
