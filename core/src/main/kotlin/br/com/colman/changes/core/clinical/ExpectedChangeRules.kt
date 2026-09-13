// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.core.clinical

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.math.ceil
import kotlin.math.floor

/** Estado de uma mudança em relação à janela típica de início (Seção 7.2). Descritivo, nunca prescritivo. */
public enum class ExpectedChangeStatus { NOT_YET_EXPECTED, WITHIN_ONSET_WINDOW, PAST_ONSET_WINDOW }

/**
 * Um item da linha do tempo. Sem data de início da TH, [status], [onsetStart], [onsetEnd] e
 * [observedAtMonths] são `null` (critério 7.2.1).
 */
public data class TimelineItem(
    val change: ExpectedChange,
    val status: ExpectedChangeStatus?,
    val onsetStart: LocalDate?,
    val onsetEnd: LocalDate?,
    val firstObserved: LocalDate?,
    val observedAtMonths: Double?,
)

public object ExpectedChangeRules {
    /** Mês médio do calendário gregoriano, em dias. */
    public const val DAYS_PER_MONTH: Double = 365.2425 / 12.0

    public fun monthsBetween(start: LocalDate, date: LocalDate): Double =
        (date.toEpochDays() - start.toEpochDays()) / DAYS_PER_MONTH

    public fun status(change: ExpectedChange, monthsOnTreatment: Double): ExpectedChangeStatus {
        val max = change.onsetMonthsMax
        return when {
            monthsOnTreatment < change.onsetMonthsMin -> ExpectedChangeStatus.NOT_YET_EXPECTED
            max == null || monthsOnTreatment <= max -> ExpectedChangeStatus.WITHIN_ONSET_WINDOW
            else -> ExpectedChangeStatus.PAST_ONSET_WINDOW
        }
    }

    /** Primeiro dia em que [status] é [ExpectedChangeStatus.WITHIN_ONSET_WINDOW]. */
    public fun onsetStart(change: ExpectedChange, hrtStart: LocalDate): LocalDate =
        hrtStart.plus(ceil(change.onsetMonthsMin * DAYS_PER_MONTH).toLong(), DateTimeUnit.DAY)

    /** Último dia dentro da janela, ou `null` se a fonte não dá limite superior. */
    public fun onsetEnd(change: ExpectedChange, hrtStart: LocalDate): LocalDate? =
        change.onsetMonthsMax?.let { hrtStart.plus(floor(it * DAYS_PER_MONTH).toLong(), DateTimeUnit.DAY) }

    public fun timeline(
        changes: List<ExpectedChange>,
        hrtStart: LocalDate?,
        today: LocalDate,
        firstObservations: Map<String, LocalDate>,
    ): List<TimelineItem> = changes.map { change ->
        val observed = firstObservations[change.changeTypeCode]
        if (hrtStart == null) {
            TimelineItem(change, null, null, null, observed, null)
        } else {
            TimelineItem(
                change = change,
                status = status(change, monthsBetween(hrtStart, today)),
                onsetStart = onsetStart(change, hrtStart),
                onsetEnd = onsetEnd(change, hrtStart),
                firstObserved = observed,
                observedAtMonths = observed?.let { monthsBetween(hrtStart, it) },
            )
        }
    }
}
