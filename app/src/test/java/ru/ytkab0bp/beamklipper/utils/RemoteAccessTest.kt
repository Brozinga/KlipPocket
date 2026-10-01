package ru.ytkab0bp.beamklipper.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OctoEverywhereLinkTest {
    @Test
    fun `the printer id is read from the secrets file`() {
        val secrets = "[secrets]\nprivate_key = abc\nprinter_id = PRINTER123\n"
        assertEquals("PRINTER123", OctoEverywhereLink.printerId(secrets))
    }

    @Test
    fun `spaces around the id are ignored`() {
        assertEquals("XYZ", OctoEverywhereLink.printerId("  printer_id   =   XYZ   \n"))
    }

    @Test
    fun `a secrets file without an id has none`() {
        assertNull(OctoEverywhereLink.printerId("[secrets]\nprivate_key = abc\n"))
        assertNull(OctoEverywhereLink.printerId("printer_id =   \n"))
        assertNull(OctoEverywhereLink.printerId(""))
    }

    @Test
    fun `the link opens octoeverywhere's setup for that printer`() {
        assertEquals("https://octoeverywhere.com/getstarted?printerid=ABC", OctoEverywhereLink.linkUrl("ABC"))
    }
}

class ObicoLinkTest {
    @Test
    fun `the discovery status is parsed`() {
        val s = ObicoLink.parseDiscoveryStatus(
            """{"is_linked": false, "one_time_passcode": "123456", "one_time_passlink": "https://app.obico.io/l/123456"}"""
        )!!
        assertFalse(s.isLinked)
        assertEquals("123456", s.passcode)
        assertEquals("https://app.obico.io/l/123456", s.passlink)
    }

    @Test
    fun `a linked status is parsed`() {
        assertTrue(ObicoLink.parseDiscoveryStatus("""{"is_linked": true}""")!!.isLinked)
    }

    @Test
    fun `missing fields read as empty`() {
        val s = ObicoLink.parseDiscoveryStatus("{}")!!
        assertFalse(s.isLinked)
        assertEquals("", s.passcode)
        assertEquals("", s.passlink)
    }

    @Test
    fun `a broken status file is ignored`() {
        assertNull(ObicoLink.parseDiscoveryStatus("not json"))
        assertNull(ObicoLink.parseDiscoveryStatus(""))
    }

    @Test
    fun `the auth token is read from the companion's config`() {
        val cfg = "[server]\nurl = https://app.obico.io\nauth_token = tok_123\n\n[moonraker]\nport = 7125\n"
        assertEquals("tok_123", ObicoLink.authTokenFromConfig(cfg))
    }

    @Test
    fun `a config without a token has none`() {
        assertNull(ObicoLink.authTokenFromConfig("[server]\nurl = https://app.obico.io\n"))
        assertNull(ObicoLink.authTokenFromConfig("# auth_token = commented\n".replace("# ", "x")))
    }

    @Test
    fun `the verify url carries the encoded code`() {
        assertEquals(
            "https://app.obico.io/api/v1/octo/verify/?code=123456",
            ObicoLink.verifyUrl("https://app.obico.io", "123456")
        )
    }

    @Test
    fun `the verify url tolerates a trailing slash and whitespace`() {
        assertEquals(
            "https://obico.local/api/v1/octo/verify/?code=AB12",
            ObicoLink.verifyUrl("https://obico.local/", "  AB12 ")
        )
    }

    @Test
    fun `special characters in a code are escaped`() {
        assertEquals("https://s/api/v1/octo/verify/?code=a%26b%3Dc", ObicoLink.verifyUrl("https://s", "a&b=c"))
    }

    @Test
    fun `a 2xx response links the printer`() {
        assertNull(ObicoLink.classifyResponse(200))
        assertNull(ObicoLink.classifyResponse(204))
    }

    @Test
    fun `a 4xx response means the code was rejected`() {
        assertEquals(ObicoLink.Result.InvalidCode, ObicoLink.classifyResponse(400))
        assertEquals(ObicoLink.Result.InvalidCode, ObicoLink.classifyResponse(404))
    }

    @Test
    fun `other responses are server problems`() {
        assertEquals(ObicoLink.Result.NetworkError("HTTP 500"), ObicoLink.classifyResponse(500))
        assertEquals(ObicoLink.Result.NetworkError("HTTP 302"), ObicoLink.classifyResponse(302))
    }

    @Test
    fun `the token is read from the verify response`() {
        assertEquals("secret", ObicoLink.authTokenFromResponse("""{"printer": {"auth_token": "secret", "id": 5}}"""))
    }

    @Test(expected = Exception::class)
    fun `a response without a token is an error`() {
        ObicoLink.authTokenFromResponse("""{"printer": {}}""")
    }

    @Test
    fun `only the default url is obico cloud`() {
        assertTrue(ObicoLink.isCloud(Prefs.OBICO_CLOUD_URL))
        assertFalse(ObicoLink.isCloud("https://obico.example"))
    }
}
