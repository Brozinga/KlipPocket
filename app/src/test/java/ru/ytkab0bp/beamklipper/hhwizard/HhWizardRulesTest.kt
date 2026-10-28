package ru.ytkab0bp.beamklipper.hhwizard

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HhWizardRulesTest {
    private fun req(body: JSONObject) = HhWizardRules.request("/files/hh", "/files/cfg", body)

    @Test fun theFoldersAlwaysComeFromTheApp() {
        val out = JSONObject(req(JSONObject().put("op", "open").put("hh_root", "/etc").put("config_dir", "/data/x"))!!)
        assertEquals("/files/hh", out.getString("hh_root"))
        assertEquals("/files/cfg", out.getString("config_dir"))
        assertEquals("open", out.getString("op"))
    }

    @Test fun onlyTheWizardsCallsAreAccepted() {
        for (op in listOf("open", "status", "page", "set", "unset", "reset", "review", "generate", "add_to_printer")) {
            val body = JSONObject().put("op", op).put("symbol", "A").put("value", true)
            assertNotNull(op, req(body))
        }
        assertNull(req(JSONObject().put("op", "eval")))
        assertNull(req(JSONObject().put("op", "")))
        assertNull(req(JSONObject()))
    }

    @Test fun symbolsAndPathsAreCheckedBeforeTheyReachPython() {
        assertNull(req(JSONObject().put("op", "set").put("symbol", "A B").put("value", 1)))
        assertNull(req(JSONObject().put("op", "set").put("symbol", "A;rm").put("value", 1)))
        assertNull(req(JSONObject().put("op", "set").put("symbol", "x".repeat(129)).put("value", 1)))
        assertNotNull(req(JSONObject().put("op", "set").put("symbol", "PARAM_NUM_GATES").put("value", 4)))
        assertNotNull(req(JSONObject().put("op", "page").put("path", "")))
        assertNotNull(req(JSONObject().put("op", "page").put("path", "3.12.0")))
        assertNull(req(JSONObject().put("op", "page").put("path", "../3")))
        assertNull(req(JSONObject().put("op", "page").put("path", "3..1")))
    }

    @Test fun valuesAreScalarsOfASaneSize() {
        assertNotNull(req(JSONObject().put("op", "set").put("symbol", "A").put("value", "text")))
        assertNotNull(req(JSONObject().put("op", "set").put("symbol", "A").put("value", 1.5)))
        assertNull(req(JSONObject().put("op", "set").put("symbol", "A").put("value", "x".repeat(501))))
        assertNull(req(JSONObject().put("op", "set").put("symbol", "A").put("value", org.json.JSONArray().put(1))))
        assertNull(req(JSONObject().put("op", "set").put("symbol", "A").put("value", JSONObject())))
    }

    @Test fun setAndUnsetNeedTheirArguments() {
        assertNull(req(JSONObject().put("op", "set").put("symbol", "A")))
        assertNull(req(JSONObject().put("op", "set").put("value", 1)))
        assertNull(req(JSONObject().put("op", "unset")))
        assertNotNull(req(JSONObject().put("op", "unset").put("symbol", "A")))
    }

    @Test fun onlyKnownFlagsAreCopiedAndAsBooleans() {
        val out = JSONObject(req(JSONObject().put("op", "generate").put("include", "yes").put("keep_state", true).put("evil", "x"))!!)
        assertFalse(out.getBoolean("include"))
        assertTrue(out.getBoolean("keep_state"))
        assertFalse(out.has("evil"))
    }

    @Test fun aFailureCarriesItsKey() {
        val f = HhWizardRules.failure("timeout")
        assertFalse(f.getBoolean("ok"))
        assertEquals("timeout", f.getString("key"))
    }
}
