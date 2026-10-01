package ru.ytkab0bp.beamklipper.service.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyRulesTest {
    @Test
    fun `an api request is forwarded to moonraker's port with its query`() {
        assertEquals(
            "http://127.0.0.1:7126/printer/objects/query?heater_bed",
            ProxyRules.upstreamUrl(7126, "/printer/objects/query", "heater_bed")
        )
    }

    @Test
    fun `a request without a query gets no question mark`() {
        assertEquals("http://127.0.0.1:7125/server/info", ProxyRules.upstreamUrl(7125, "/server/info", null))
        assertEquals("http://127.0.0.1:7125/server/info", ProxyRules.upstreamUrl(7125, "/server/info", ""))
    }

    @Test
    fun `the websocket goes to moonraker's websocket endpoint`() {
        assertEquals("ws://127.0.0.1:7125/websocket", ProxyRules.websocketUrl(7125, null))
        assertEquals("ws://127.0.0.1:7125/websocket?token=abc", ProxyRules.websocketUrl(7125, "token=abc"))
    }

    @Test
    fun `hop by hop and injected headers are not forwarded`() {
        listOf("Host", "Connection", "Content-Length", "Transfer-Encoding", "Keep-Alive",
            "Proxy-Connection", "remote-addr", "http-client-ip")
            .forEach { assertFalse(it, ProxyRules.forwardRequestHeader(it)) }
    }

    @Test
    fun `ordinary headers are forwarded`() {
        listOf("Authorization", "Content-Type", "Accept", "X-Api-Key", "Cookie")
            .forEach { assertTrue(it, ProxyRules.forwardRequestHeader(it)) }
    }

    @Test
    fun `length and connection headers of the response are rebuilt by the server`() {
        listOf("Content-Length", "transfer-encoding", "Connection", "Keep-Alive")
            .forEach { assertFalse(it, ProxyRules.forwardResponseHeader(it)) }
    }

    @Test
    fun `other response headers are forwarded but not the status line`() {
        assertTrue(ProxyRules.forwardResponseHeader("Content-Type"))
        assertTrue(ProxyRules.forwardResponseHeader("Access-Control-Allow-Origin"))
        assertFalse(ProxyRules.forwardResponseHeader(null))
        assertFalse(ProxyRules.forwardResponseHeader(""))
    }

    @Test
    fun `only writes carry a body`() {
        listOf("POST", "PUT", "PATCH", "post").forEach { assertTrue(it, ProxyRules.hasBody(it)) }
        listOf("GET", "DELETE", "HEAD", "OPTIONS").forEach { assertFalse(it, ProxyRules.hasBody(it)) }
    }

    @Test
    fun `only the device itself may call the beam endpoints`() {
        assertTrue(ProxyRules.isLocalRequest("127.0.0.1"))
        assertFalse(ProxyRules.isLocalRequest("192.168.1.20"))
        assertFalse(ProxyRules.isLocalRequest(null))
    }
}
