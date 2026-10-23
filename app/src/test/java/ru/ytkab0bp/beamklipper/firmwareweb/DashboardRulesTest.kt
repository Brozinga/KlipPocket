package ru.ytkab0bp.beamklipper.firmwareweb

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DashboardRulesTest {
    @Test fun cpuStatGivesBusyAndTotalTicks() {
        val r = DashboardRules.parseCpuStat("cpu  100 0 50 800 50 0 0 0 0 0\ncpu0 1 2 3 4\n")!!
        assertEquals(150L, r[0])      // 100 + 50, idle 850 is left out
        assertEquals(1000L, r[1])
        assertNull(DashboardRules.parseCpuStat("nothing here"))
        assertNull(DashboardRules.parseCpuStat("cpu  1 2"))
    }

    @Test fun meminfoIsReadInKilobytes() {
        val m = DashboardRules.parseMeminfo("MemTotal:        3800000 kB\nMemAvailable:    1200000 kB\nSwapTotal:             0 kB\n")
        assertEquals(3800000L, m["MemTotal"])
        assertEquals(1200000L, m["MemAvailable"])
        assertEquals(0L, m["SwapTotal"])
    }

    @Test fun processTicksSurviveACommandNameWithSpacesAndParentheses() {
        val line = "1234 (klippy (main) x) S 1 1 1 0 -1 4194560 100 0 0 0 70 30 0 0 20 0 5 0 100 1000 200 18446744073709551615"
        assertEquals(100L, DashboardRules.processTicks(line))
        assertNull(DashboardRules.processTicks("garbage"))
    }

    @Test fun residentPagesAreTheSecondFieldOfStatm() {
        assertEquals(250L, DashboardRules.residentPages("1000 250 100 5 0 300 0\n"))
        assertNull(DashboardRules.residentPages(""))
    }

    @Test fun percentIsKeptBetweenZeroAndAHundred() {
        assertEquals(25.0, DashboardRules.percent(1.0, 4.0), 0.001)
        assertEquals(100.0, DashboardRules.percent(9.0, 4.0), 0.001)
        assertEquals(0.0, DashboardRules.percent(1.0, 0.0), 0.001)
    }

    @Test fun mcuObjectsAreTheOnesCalledMcu() {
        val list = JSONObject("{\"result\":{\"objects\":[\"extruder\",\"mcu\",\"mcu toolhead\",\"mcu_foo\"]}}")
        assertEquals(listOf("mcu", "mcu toolhead"), DashboardRules.mcuObjectNames(list))
        assertEquals(emptyList<String>(), DashboardRules.mcuObjectNames(JSONObject("{}")))
    }

    @Test fun mcuAnswersGiveChipVersionAndClock() {
        val answer = JSONObject("{\"result\":{\"status\":{\"mcu\":{\"mcu_version\":\"v0.12.0\",\"mcu_constants\":{\"MCU\":\"stm32f401xc\",\"CLOCK_FREQ\":84000000}},\"mcu toolhead\":{\"mcu_version\":\"v0.12.0\",\"mcu_constants\":{\"MCU\":\"rp2040\",\"CLOCK_FREQ\":12000000}}}}}")
        val mcus = DashboardRules.parseMcus(answer)
        assertEquals(2, mcus.length())
        assertEquals("mcu", mcus.getJSONObject(0).getString("name"))
        assertEquals("stm32f401xc", mcus.getJSONObject(0).getString("chip"))
        assertEquals("toolhead", mcus.getJSONObject(1).getString("name"))
        assertEquals(12000000L, mcus.getJSONObject(1).getLong("clock"))
    }

    @Test fun cpuMeterNeedsTwoSamplesAndIgnoresCountersThatGoBack() {
        val meter = CpuMeter(4)
        assertNull(meter.sample(100, 0, 100))
        // 1 s later the app used 1 s of CPU on a 4-core device -> 25 %
        assertEquals(25.0, meter.sample(200, 1_000_000_000L, 100)!!, 0.001)
        assertNull("a process ended: the counter went back", meter.sample(150, 2_000_000_000L, 100))
        assertNotNull(meter.sample(250, 3_000_000_000L, 100))
    }

    @Test fun thermalZonesAreSortedIntoCpuGpuAndSkin() {
        assertEquals("cpu", DashboardRules.thermalKind("cpu-1-0-usr"))
        assertEquals("cpu", DashboardRules.thermalKind("tsens_tz_sensor3"))
        assertEquals("gpu", DashboardRules.thermalKind("gpuss-0-usr"))
        assertEquals("skin", DashboardRules.thermalKind("quiet_therm"))
        assertNull(DashboardRules.thermalKind("battery"))
        assertNull(DashboardRules.thermalKind("pa_therm0"))
    }
}
