// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.core.testing

import br.com.colman.changes.core.model.TimeZoneProvider
import kotlinx.datetime.TimeZone
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/** Relógio controlável. Default: 2026-09-12T12:00:00Z. */
public class FixedClock(public var now: Instant = DEFAULT_NOW) : Clock {
    override fun now(): Instant = now

    public fun advance(duration: Duration) {
        now += duration
    }

    public companion object {
        public val DEFAULT_NOW: Instant = Instant.parse("2026-09-12T12:00:00Z")
    }
}

/** Fuso controlável para testes de mudança de fuso. */
public class FixedTimeZoneProvider(public var zone: TimeZone = TestZones.SAO_PAULO) : TimeZoneProvider {
    override fun current(): TimeZone = zone
}

/** Fusos com e sem horário de verão, e com offsets não inteiros. */
public object TestZones {
    public val UTC: TimeZone = TimeZone.UTC
    public val SAO_PAULO: TimeZone = TimeZone.of("America/Sao_Paulo")
    public val NEW_YORK: TimeZone = TimeZone.of("America/New_York")
    public val BERLIN: TimeZone = TimeZone.of("Europe/Berlin")
    public val TOKYO: TimeZone = TimeZone.of("Asia/Tokyo")
    public val KOLKATA: TimeZone = TimeZone.of("Asia/Kolkata")
    public val LORD_HOWE: TimeZone = TimeZone.of("Australia/Lord_Howe")
    public val CHATHAM: TimeZone = TimeZone.of("Pacific/Chatham")

    public val ALL: List<TimeZone> = listOf(UTC, SAO_PAULO, NEW_YORK, BERLIN, TOKYO, KOLKATA, LORD_HOWE, CHATHAM)
}
