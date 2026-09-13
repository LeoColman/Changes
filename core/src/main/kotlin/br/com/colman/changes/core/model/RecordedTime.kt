// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.core.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.asTimeZone
import kotlinx.datetime.offsetAt
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Instante + offset local no momento do registro (Seção 6). "Tomei a dose às 8h" continua
 * verdadeiro depois que a pessoa muda de fuso: a hora local é sempre calculada com o offset gravado.
 * Precisão de milissegundos, igual à do banco.
 */
public data class RecordedTime(val instant: Instant, val offset: UtcOffset) {
    public val epochMillis: Long get() = instant.toEpochMilliseconds()

    public val offsetSeconds: Long get() = offset.totalSeconds.toLong()

    public val localDateTime: LocalDateTime get() = instant.toLocalDateTime(offset.asTimeZone())

    public val localDate: LocalDate get() = localDateTime.date

    public companion object {
        public fun of(instant: Instant, zone: TimeZone): RecordedTime {
            val truncated = Instant.fromEpochMilliseconds(instant.toEpochMilliseconds())
            return RecordedTime(truncated, zone.offsetAt(truncated))
        }

        public fun fromDb(epochMillis: Long, offsetSeconds: Long): RecordedTime =
            RecordedTime(Instant.fromEpochMilliseconds(epochMillis), UtcOffset(seconds = offsetSeconds.toInt()))
    }
}

/** Fuso atual do aparelho. Injetado para que testes controlem fuso e horário de verão. */
public fun interface TimeZoneProvider {
    public fun current(): TimeZone
}

/** Maior offset aceito pelo ISO 8601 / java.time (18 horas). Usado para validar dados importados. */
public const val MAX_OFFSET_SECONDS: Long = 18L * 60 * 60
