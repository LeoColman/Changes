// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.core.data

import br.com.colman.changes.core.model.RecordedTime
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.uuid.Uuid

// Conversões de coluna. Os mappers de linha (arquivos *Mappers.kt deste pacote) convertem entre as
// linhas geradas pelo SQLDelight e o modelo de domínio. Valor de enum desconhecido no banco é
// corrupção (o import valida antes de escrever) e derruba com exceção.

internal fun Long.asBoolean(): Boolean = this != 0L

internal fun Boolean.asLong(): Long = if (this) 1L else 0L

internal fun String.asUuid(): Uuid = Uuid.parse(this)

internal fun Long.asLocalDate(): LocalDate = LocalDate.fromEpochDays(this)

internal fun LocalDate.asEpochDay(): Long = toEpochDays()

internal fun Long.asLocalTime(): LocalTime = LocalTime(
    (this / MINUTES_PER_HOUR).toInt(),
    (this % MINUTES_PER_HOUR).toInt()
)

internal fun LocalTime.asMinutes(): Long = hour * MINUTES_PER_HOUR + minute

private const val MINUTES_PER_HOUR = 60L

internal fun recordedOrNull(millis: Long?, offsetSeconds: Long?): RecordedTime? =
    if (millis != null && offsetSeconds != null) RecordedTime.fromDb(millis, offsetSeconds) else null
