package net.agolyakov.firelamp.protocol

import java.time.DayOfWeek
import java.time.Instant
import java.time.Month
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.zone.ZoneOffsetTransitionRule
import java.time.zone.ZoneOffsetTransitionRule.TimeDefinition
import java.util.Locale

/**
 * Builds the POSIX TZ string the lamp wants (Time zone, 0x0006) from the phone's IANA zone,
 * e.g. `Europe/Berlin` -> `CET-1CEST,M3.5.0,M10.5.0/3`.
 *
 * Built from the zone's current rules, so every zone works without a table: a fixed offset
 * gives `MSK-3`, a zone with daylight saving time gives the two yearly transition rules.
 * Note the POSIX sign is inverted: UTC+3 is `-3`.
 */
object TimeZoneMapper {

    fun toPosix(zone: ZoneId, now: Instant = Instant.now()): String {
        val rules = zone.rules
        val transitionRules = rules.transitionRules
        // Zones without yearly rules (or with only one, which POSIX cannot express)
        // use the offset in effect now
        if (transitionRules.size != 2) {
            val offset = rules.getOffset(now)
            return name(zone, offset, daylight = false) + offset(offset)
        }

        val start = transitionRules.first { it.offsetAfter.totalSeconds > it.offsetBefore.totalSeconds }
        val end = transitionRules.first { it !== start }
        val standard = start.offsetBefore
        val daylight = start.offsetAfter

        return buildString {
            append(name(zone, standard, daylight = false))
            append(offset(standard))
            append(name(zone, daylight, daylight = true))
            // The daylight offset may be left out when it is the standard one + 1 hour
            if (daylight.totalSeconds - standard.totalSeconds != 3600) append(offset(daylight))
            append(',').append(rule(start))
            append(',').append(rule(end))
        }
    }

    /** POSIX offset: hours west of UTC, so the sign is inverted. */
    private fun offset(offset: ZoneOffset): String = signedTime(-offset.totalSeconds)

    /**
     * A zone abbreviation of letters only (`CET`, `MSK`) if the platform has one, otherwise the
     * POSIX quoted numeric form (`<+0530>`), which libc time zone parsing accepts as well.
     */
    private fun name(zone: ZoneId, offset: ZoneOffset, daylight: Boolean): String {
        val short = java.util.TimeZone.getTimeZone(zone).getDisplayName(daylight, java.util.TimeZone.SHORT, Locale.US)
        if (short.length in 3..6 && short.all { it in 'A'..'Z' || it in 'a'..'z' }) return short
        val seconds = offset.totalSeconds
        val abs = kotlin.math.abs(seconds)
        val sign = if (seconds < 0) '-' else '+'
        val minutes = abs % 3600 / 60
        return if (minutes == 0) "<%c%02d>".format(Locale.ROOT, sign, abs / 3600)
        else "<%c%02d%02d>".format(Locale.ROOT, sign, abs / 3600, minutes)
    }

    /**
     * One transition, `Mm.w.d[/time]`: month, week (1..4, 5 - the last one), day (0 - Sunday),
     * and the local time in effect before the transition. A rule on a fixed date becomes `Jn`.
     */
    private fun rule(rule: ZoneOffsetTransitionRule): String {
        var seconds = if (rule.isMidnightEndOfDay) 24 * 3600 else rule.localTime.toSecondOfDay()
        // To the wall time in effect before the transition
        seconds += rule.offsetBefore.totalSeconds - when (rule.timeDefinition) {
            TimeDefinition.UTC -> 0
            TimeDefinition.STANDARD -> rule.standardOffset.totalSeconds
            else -> rule.offsetBefore.totalSeconds
        }

        val dayOfWeek = rule.dayOfWeek
        val dom = rule.dayOfMonthIndicator
        val date: String = when {
            dayOfWeek == null -> "J" + julianDay(rule.month, dom)
            dom < 0 -> {
                // The last <weekday> on or before <dom> days from the end; -1 is the last one.
                // Other negatives do not exist in tzdb today.
                "M${rule.month.value}.5.${posixDay(dayOfWeek)}"
            }
            dom + 6 == rule.month.maxLength() && rule.month != Month.FEBRUARY ->
                // "<weekday> on or after <dom>" covering the last 7 days: the last one
                "M${rule.month.value}.5.${posixDay(dayOfWeek)}"
            else -> {
                // "<weekday> on or after <dom>". POSIX counts weeks from the 1st, so only
                // dom 1, 8, 15, 22 map directly; "Sun>=2" is the same as "Sat>=1" 24 hours later
                val shift = (dom - 1) % 7
                seconds += shift * 24 * 3600
                val week = (dom - shift - 1) / 7 + 1
                "M${rule.month.value}.$week.${posixDay(dayOfWeek.minus(shift.toLong()))}"
            }
        }
        return if (seconds == 2 * 3600) date else "$date/${signedTime(seconds)}"
    }

    private fun posixDay(day: DayOfWeek): Int = day.value % 7 // Sunday is 0

    /** `Jn`: 1..365, February 29 is never counted. */
    private fun julianDay(month: Month, day: Int): Int =
        Month.entries.take(month.ordinal).sumOf { it.minLength() } + day

    /** `[-]h[:mm[:ss]]` */
    private fun signedTime(totalSeconds: Int): String {
        val sign = if (totalSeconds < 0) "-" else ""
        val abs = kotlin.math.abs(totalSeconds)
        val h = abs / 3600
        val m = abs % 3600 / 60
        val s = abs % 60
        return sign + when {
            s != 0 -> "%d:%02d:%02d".format(Locale.ROOT, h, m, s)
            m != 0 -> "%d:%02d".format(Locale.ROOT, h, m)
            else -> "$h"
        }
    }
}
