package net.agolyakov.firelamp.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/**
 * Expected strings are the ones of the tzdata `zone1970.tab` POSIX footers; zone abbreviations
 * come from the platform, so zones without a letter abbreviation are checked by their rules only.
 */
class TimeZoneMapperTest {

    private fun posix(zone: String) = TimeZoneMapper.toPosix(ZoneId.of(zone))

    @Test
    fun fixedOffset() {
        assertEquals("MSK-3", posix("Europe/Moscow"))
        assertEquals("IST-5:30", posix("Asia/Kolkata"))
        assertEquals("UTC0", posix("UTC"))
    }

    @Test
    fun europe() {
        assertEquals("CET-1CEST,M3.5.0,M10.5.0/3", posix("Europe/Berlin"))
        assertEquals("EET-2EEST,M3.5.0/3,M10.5.0/4", posix("Europe/Kiev"))
        assertEquals("GMT0BST,M3.5.0/1,M10.5.0", posix("Europe/London"))
    }

    @Test
    fun northAmerica() {
        assertEquals("EST5EDT,M3.2.0,M11.1.0", posix("America/New_York"))
        assertEquals("PST8PDT,M3.2.0,M11.1.0", posix("America/Los_Angeles"))
    }

    @Test
    fun southernHemisphere() {
        assertEquals("AEST-10AEDT,M10.1.0,M4.1.0/3", posix("Australia/Sydney"))
        assertEquals("NZST-12NZDT,M9.5.0,M4.1.0/3", posix("Pacific/Auckland"))
    }

    @Test
    fun ruleNotOnAWeekBoundary() {
        // Chile: "Sun>=2" at 4:00 UTC, i.e. Saturday 24:00 local time
        val tz = posix("America/Santiago")
        assertTrue(tz, tz.endsWith("4,M9.1.6/24,M4.1.6/24") || tz.endsWith(",M9.1.6/24,M4.1.6/24"))
    }

    @Test
    fun fitsTheLamp() {
        for (id in ZoneId.getAvailableZoneIds()) {
            val tz = TimeZoneMapper.toPosix(ZoneId.of(id))
            assertTrue("$id: $tz", tz.length <= FireLampCodec.TIME_ZONE_MAX_BYTES)
            assertTrue("$id: $tz", tz.all { it in ' '..'~' })
        }
    }

    @Test
    fun parseOffsets() {
        assertEquals(TimeZoneMapper.Offsets(180, null), TimeZoneMapper.parseOffsets("MSK-3"))
        assertEquals(TimeZoneMapper.Offsets(0, null), TimeZoneMapper.parseOffsets("UTC0"))
        assertEquals(TimeZoneMapper.Offsets(330, null), TimeZoneMapper.parseOffsets("<+0530>-5:30"))
        assertEquals(TimeZoneMapper.Offsets(60, 120), TimeZoneMapper.parseOffsets("CET-1CEST,M3.5.0,M10.5.0/3"))
        assertEquals(TimeZoneMapper.Offsets(-300, -240), TimeZoneMapper.parseOffsets("EST5EDT,M3.2.0,M11.1.0"))
        assertEquals(TimeZoneMapper.Offsets(630, 660), TimeZoneMapper.parseOffsets("<+1030>-10:30<+11>-11,M10.1.0,M4.1.0"))
        assertEquals(null, TimeZoneMapper.parseOffsets("garbage"))
        assertEquals(null, TimeZoneMapper.parseOffsets(""))
    }

    @Test
    fun parsesWhatItBuilds() {
        listOf("Europe/Berlin", "America/New_York", "Asia/Kolkata", "Australia/Sydney", "Europe/Moscow").forEach { zone ->
            val rules = ZoneId.of(zone).rules
            val offsets = TimeZoneMapper.parseOffsets(posix(zone))
            assertEquals(zone, rules.getStandardOffset(java.time.Instant.now()).totalSeconds / 60, offsets?.standardMinutes)
        }
    }

    @Test
    fun formatUtc() {
        assertEquals("UTC+3", TimeZoneMapper.formatUtc(180))
        assertEquals("UTC+5:30", TimeZoneMapper.formatUtc(330))
        assertEquals("UTC-8", TimeZoneMapper.formatUtc(-480))
        assertEquals("UTC", TimeZoneMapper.formatUtc(0))
    }
}
