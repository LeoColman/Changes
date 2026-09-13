// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.core.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import br.com.colman.changes.core.clinical.ClinicalLabels
import br.com.colman.changes.core.clinical.ExpectedChange
import br.com.colman.changes.core.clinical.ExpectedChangeRules
import br.com.colman.changes.core.clinical.Permanence
import br.com.colman.changes.core.clinical.SourceKey
import br.com.colman.changes.core.clinical.TimelineItem
import br.com.colman.changes.core.db.sql.Body_change_type
import br.com.colman.changes.core.db.sql.ChangesDatabase
import br.com.colman.changes.core.db.sql.Expected_change
import br.com.colman.changes.core.db.sql.SelectFirstObservations
import br.com.colman.changes.core.model.TimeZoneProvider
import br.com.colman.changes.core.model.TreatmentProtocol
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

/** Item da linha do tempo com a citação da fonte da faixa e, se houver, da fonte da permanência (critério 7.2.3). */
public data class ExpectedChangeItem(
    val timeline: TimelineItem,
    val sourceCitation: String,
    val permanenceCitation: String?,
)

/** Linha do tempo da Seção 7.2. Sem data de início, os itens vêm sem estado relativo (critério 7.2.1). */
public data class ExpectedTimeline(
    val hrtStart: LocalDate?,
    val monthsOnTreatment: Double?,
    val items: List<ExpectedChangeItem>,
)

/**
 * Junta o dataset clínico do banco (reseedado, ADR 0002), a data de início da TH do perfil e a primeira
 * observação registrada de cada mudança. Descritivo: nada aqui sugere o que "deveria" acontecer.
 */
@OptIn(ExperimentalCoroutinesApi::class)
public class ExpectedChangeRepository(
    private val database: ChangesDatabase,
    private val profiles: ProfileRepository,
    private val labels: ClinicalLabels,
    private val clock: Clock,
    private val timeZones: TimeZoneProvider,
    private val io: CoroutineDispatcher,
) {
    public fun observeTimeline(): Flow<ExpectedTimeline> = profiles.observe().flatMapLatest { profile ->
        val protocol = profile.treatmentProtocol.code
        val changes = database.expectedChangeQueries.selectByProtocol(protocol).asFlow().mapToList(io)
        val observations = database.bodyChangeQueries.selectFirstObservations().asFlow().mapToList(io)
        val types = database.bodyChangeQueries.selectAllTypes().asFlow().mapToList(io)
        combine(changes, observations, types) { rows, firsts, catalog ->
            val zone = timeZones.current()
            val today = clock.now().toLocalDateTime(zone).date
            val start = profile.hrtStartDate
            val timeline = ExpectedChangeRules.timeline(
                rows.map { it.toModel() },
                start,
                today,
                firstObservationDates(firsts, catalog, zone),
            )
            ExpectedTimeline(start, start?.let { ExpectedChangeRules.monthsBetween(it, today) }, timeline.map(::itemOf))
        }
    }

    /** Citações de todas as fontes, para a seção de referências da tela. */
    public fun references(): Map<SourceKey, String> = SourceKey.entries.associateWith(labels::reference)

    private fun itemOf(item: TimelineItem): ExpectedChangeItem = ExpectedChangeItem(
        timeline = item,
        sourceCitation = labels.reference(item.change.source),
        permanenceCitation = item.change.permanenceSource?.let(labels::reference),
    )
}

/** Data local da primeira observação, por código de mudança. Tipos excluídos não entram. */
private fun firstObservationDates(
    firsts: List<SelectFirstObservations>,
    catalog: List<Body_change_type>,
    zone: TimeZone,
): Map<String, LocalDate> {
    val codeById = catalog.associate { it.id to it.code }
    val result = mutableMapOf<String, LocalDate>()
    for (first in firsts) {
        val code = codeById[first.change_type_id]
        val at = first.first_observed_at
        if (code != null && at != null) result[code] = Instant.fromEpochMilliseconds(at).toLocalDateTime(zone).date
    }
    return result
}

internal fun Expected_change.toModel(): ExpectedChange = ExpectedChange(
    changeTypeCode = change_type_code,
    protocol = TreatmentProtocol(protocol),
    onsetMonthsMin = onset_months_min,
    onsetMonthsMax = onset_months_max,
    maxEffectMonthsMin = max_effect_months_min,
    maxEffectMonthsMax = max_effect_months_max,
    permanence = Permanence.valueOf(permanence),
    permanenceSource = permanence_source_key?.let { SourceKey.valueOf(it) },
    source = SourceKey.valueOf(source_key),
)
