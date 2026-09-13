// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.core.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import br.com.colman.changes.core.clinical.ClinicalDataset
import br.com.colman.changes.core.clinical.ExpectedChangeRules
import br.com.colman.changes.core.db.sql.Calendar_event
import br.com.colman.changes.core.db.sql.ChangesDatabase
import br.com.colman.changes.core.model.AgendaItem
import br.com.colman.changes.core.model.CalendarCategory
import br.com.colman.changes.core.model.CalendarEvent
import br.com.colman.changes.core.model.DomainError
import br.com.colman.changes.core.model.DoseLog
import br.com.colman.changes.core.model.DoseSchedule
import br.com.colman.changes.core.model.EventSourceType
import br.com.colman.changes.core.model.Profile
import br.com.colman.changes.core.model.RecordedTime
import br.com.colman.changes.core.model.RecurrenceRule
import br.com.colman.changes.core.model.Regimen
import br.com.colman.changes.core.model.Result
import br.com.colman.changes.core.model.TimeZoneProvider
import br.com.colman.changes.core.model.asFailure
import br.com.colman.changes.core.model.asSuccess
import br.com.colman.changes.core.model.errorOrNull
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Dados de um novo evento manual. Tipo próprio (em vez de 8 parâmetros soltos) para não violar o
 * limite de parâmetros do Detekt; `ignoreDataClasses` isenta o construtor da checagem.
 */
public data class NewCalendarEvent(
    val title: String,
    val description: String?,
    val start: Instant,
    val end: Instant?,
    val isAllDay: Boolean,
    val category: CalendarCategory,
    val reminderMinutesBefore: Int?,
    val recurrence: RecurrenceRule?,
)

/** Fontes de dados já lidas do banco para montar uma emissão da agenda. */
private data class AgendaSources(
    val regimens: List<Regimen>,
    val doseLogs: List<DoseLog>,
    val events: List<CalendarEvent>,
    val profile: Profile,
)

/** Janela e opções de uma consulta de agenda. */
private data class AgendaWindow(
    val from: LocalDate,
    val to: LocalDate,
    val includeMilestones: Boolean,
    val zone: TimeZone
)

/**
 * Eventos manuais e agenda unificada (Seção 7.9). Fala com regime, dose e perfil diretamente pelo
 * banco / [ProfileRepository]: não depende de [RegimenRepository] nem de [DoseLogRepository].
 */
public class CalendarRepository(
    private val database: ChangesDatabase,
    private val io: CoroutineDispatcher,
    private val clock: Clock,
    private val timeZones: TimeZoneProvider,
    private val profiles: ProfileRepository,
    private val dataset: ClinicalDataset,
) {
    public suspend fun get(id: Uuid): CalendarEvent? = withContext(io) {
        database.calendarQueries.selectById(id.toString()).executeAsOneOrNull()?.toModel()
    }

    /**
     * Eventos fora da lixeira que têm lembrete: fonte do agendamento de alarmes (Seção 7.9). O filtro
     * `IS NOT NULL` faz o SQLDelight gerar um tipo próprio; o construtor da linha da tabela serve de
     * mapper para reaproveitar o mesmo `toModel`.
     */
    public fun observeWithReminders(): Flow<List<CalendarEvent>> =
        database.calendarQueries.selectWithReminders(::Calendar_event)
            .asFlow()
            .mapToList(io)
            .mapRows { it.toModel() }

    /** Eventos fora da lixeira de uma categoria, do início mais antigo ao mais novo (rotinas, ADR 0011). */
    public fun observeByCategory(category: CalendarCategory): Flow<List<CalendarEvent>> =
        database.calendarQueries.selectByCategory(category.name)
            .asFlow()
            .mapToList(io)
            .mapRows { it.toModel() }

    /** Evento de dia inteiro grava `start` como meia-noite local da data escolhida (Seção 7.9). */
    public suspend fun create(newEvent: NewCalendarEvent): Result<CalendarEvent> = withContext(io) {
        val recordedStart = normalizedStart(newEvent.start, newEvent.isAllDay)
        val error = eventError(
            newEvent.title,
            recordedStart.instant,
            newEvent.end,
            newEvent.reminderMinutesBefore,
            newEvent.recurrence,
        )
        if (error != null) {
            error.asFailure()
        } else {
            val model = CalendarEvent(
                id = Uuid.random(),
                title = newEvent.title,
                description = newEvent.description,
                start = recordedStart,
                end = newEvent.end?.let { RecordedTime.of(it, timeZones.current()) },
                isAllDay = newEvent.isAllDay,
                category = newEvent.category,
                sourceType = EventSourceType.MANUAL,
                sourceId = null,
                reminderMinutesBefore = newEvent.reminderMinutesBefore,
                recurrence = newEvent.recurrence,
                completedAt = null,
            )
            val now = clock.now().toEpochMilliseconds()
            database.calendarQueries.insert(model.toRow(now, now))
            model.asSuccess()
        }
    }

    public suspend fun update(event: CalendarEvent): Result<CalendarEvent> = withContext(io) {
        val error =
            eventError(
                event.title,
                event.start.instant,
                event.end?.instant,
                event.reminderMinutesBefore,
                event.recurrence
            )
        if (error != null) {
            error.asFailure()
        } else {
            val now = clock.now().toEpochMilliseconds()
            database.calendarQueries.update(
                event.title,
                event.description,
                event.start.epochMillis,
                event.start.offsetSeconds,
                event.end?.epochMillis,
                event.end?.offsetSeconds,
                event.isAllDay.asLong(),
                event.category.name,
                event.reminderMinutesBefore?.toLong(),
                event.recurrence?.format(),
                now,
                event.id.toString(),
            )
            event.asSuccess()
        }
    }

    public suspend fun setCompleted(id: Uuid, completedAt: Instant?): Result<Unit> = withContext(io) {
        val recorded = completedAt?.let { RecordedTime.of(it, timeZones.current()) }
        val now = clock.now().toEpochMilliseconds()
        database.calendarQueries.setCompleted(recorded?.epochMillis, recorded?.offsetSeconds, now, id.toString())
        Unit.asSuccess()
    }

    public suspend fun delete(id: Uuid): Result<Unit> = withContext(io) {
        val now = clock.now().toEpochMilliseconds()
        database.calendarQueries.softDelete(now, id.toString())
        Unit.asSuccess()
    }

    public suspend fun restore(id: Uuid): Result<Unit> = withContext(io) {
        val now = clock.now().toEpochMilliseconds()
        database.calendarQueries.restore(now, id.toString())
        Unit.asSuccess()
    }

    /**
     * Agenda unificada (Seção 7.9): doses previstas de regimes ativos, doses registradas, eventos
     * manuais (expandindo recorrência) e, se pedido, marcos do dataset clínico. Consulta o banco com
     * uma janela alargada em 1 dia de cada lado e filtra por data local antes de devolver.
     */
    public fun observeAgenda(from: LocalDate, to: LocalDate, includeMilestones: Boolean): Flow<List<AgendaItem>> {
        val zone = timeZones.current()
        val fromMillis = LocalDateTime(
            from.minus(1, DateTimeUnit.DAY),
            LocalTime(0, 0)
        ).toInstant(zone).toEpochMilliseconds()
        val toMillis = LocalDateTime(
            to.plus(2, DateTimeUnit.DAY),
            LocalTime(0, 0)
        ).toInstant(zone).toEpochMilliseconds()
        val regimensFlow = database.regimenQueries.selectActive().asFlow().mapToList(io)
        val doseLogsFlow = database.doseLogQueries.selectBetween(fromMillis, toMillis).asFlow().mapToList(io)
        val eventsFlow = database.calendarQueries.selectCandidatesBetween(toMillis, fromMillis).asFlow().mapToList(io)
        val window = AgendaWindow(from, to, includeMilestones, zone)
        return combine(
            regimensFlow,
            doseLogsFlow,
            eventsFlow,
            profiles.observe()
        ) { regimenRows, doseLogRows, eventRows, profile ->
            val sources = AgendaSources(
                regimens = regimenRows.map { it.toModel() },
                doseLogs = doseLogRows.map { it.toModel() },
                events = eventRows.map { it.toModel() },
                profile = profile,
            )
            buildAgenda(sources, window)
        }
    }

    private fun normalizedStart(start: Instant, isAllDay: Boolean): RecordedTime {
        val zone = timeZones.current()
        return if (isAllDay) {
            val localDate = RecordedTime.of(start, zone).localDate
            RecordedTime.of(LocalDateTime(localDate, LocalTime(0, 0)).toInstant(zone), zone)
        } else {
            RecordedTime.of(start, zone)
        }
    }

    private fun eventError(
        title: String,
        start: Instant,
        end: Instant?,
        reminderMinutesBefore: Int?,
        recurrence: RecurrenceRule?,
    ): DomainError? {
        val endError = if (end != null && end < start) {
            DomainError.Invalid(
                "end",
                DomainError.Reason.END_BEFORE_START
            )
        } else {
            null
        }
        return firstError(
            blankError("title", title),
            endError,
            reminderMinutesBefore?.let { notNegativeError("reminderMinutesBefore", it) },
            recurrence?.let { RecurrenceRule.validated(it).errorOrNull() },
        )
    }

    private fun buildAgenda(sources: AgendaSources, window: AgendaWindow): List<AgendaItem> {
        val from = window.from
        val to = window.to
        val items = mutableListOf<AgendaItem>()
        for (regimen in sources.regimens) {
            for (date in DoseSchedule.occurrences(regimen, from, to)) {
                val at = DoseSchedule.instantOf(date, regimen.timeOfDay, window.zone)
                items += AgendaItem.PlannedDose(regimen.id, regimen.medicationId, date, at)
            }
        }
        for (log in sources.doseLogs) {
            if (log.takenAt.localDate in from..to) items += AgendaItem.LoggedDose(log)
        }
        for (event in sources.events) {
            for (date in eventDates(event, from, to)) {
                items += AgendaItem.Event(event, date)
            }
        }
        if (window.includeMilestones) {
            addMilestones(items, sources.profile, from, to)
        }
        return sortedByDate(items)
    }

    private fun addMilestones(items: MutableList<AgendaItem>, profile: Profile, from: LocalDate, to: LocalDate) {
        val hrtStart = profile.hrtStartDate
        if (hrtStart != null) {
            for (change in dataset.expectedChanges) {
                val onset = ExpectedChangeRules.onsetStart(change, hrtStart)
                if (onset in from..to) items += AgendaItem.Milestone(change.changeTypeCode, onset)
            }
        }
    }

    private fun eventDates(event: CalendarEvent, from: LocalDate, to: LocalDate): List<LocalDate> {
        val recurrence = event.recurrence
        return if (recurrence != null) {
            recurrence.occurrences(event.start.localDate, from, to)
        } else if (event.start.localDate in from..to) {
            listOf(event.start.localDate)
        } else {
            emptyList()
        }
    }

    /** Ordenação por data, estável, sem `sortedBy`/`compareBy` (guideline 16): laço de inserção simples. */
    private fun sortedByDate(items: List<AgendaItem>): List<AgendaItem> {
        val result = mutableListOf<AgendaItem>()
        for (item in items) {
            var index = result.size
            while (index > 0 && result[index - 1].date > item.date) index--
            result.add(index, item)
        }
        return result
    }
}
