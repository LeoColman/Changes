// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.colman.changes.core.clinical.BodyVocabularyResolver
import br.com.colman.changes.core.clinical.ClinicalLabels
import br.com.colman.changes.core.data.CalendarRepository
import br.com.colman.changes.core.data.ProfileRepository
import br.com.colman.changes.core.model.AgendaItem
import br.com.colman.changes.core.model.Profile
import br.com.colman.changes.core.model.TimeZoneProvider
import br.com.colman.changes.platform.AppSettings
import br.com.colman.changes.platform.SettingsStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toLocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Clock

private const val STOP_TIMEOUT_MS = 5_000L

/** Janela da visão Agenda: lista contínua a partir de hoje, limitada para não crescer sem fim. */
private const val AGENDA_WINDOW_DAYS = 180
private const val DAYS_PER_WEEK = 7L

private data class QueryControls(val viewMode: CalendarViewMode, val displayedMonth: LocalDate)

private data class QueryRange(val from: LocalDate, val to: LocalDate)

/**
 * Tela Calendário (Seção 7.9): visões Mês e Agenda sobre a mesma fonte unificada
 * ([CalendarRepository.observeAgenda]). "Hoje" e o fuso usado para resolver horas só vêm de [clock]
 * e [timeZones], nunca de `Clock.System`/`TimeZone.currentSystemDefault()` (critério 7.9.1).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModel(
    private val calendarRepository: CalendarRepository,
    private val profileRepository: ProfileRepository,
    private val clinicalLabels: ClinicalLabels,
    private val settingsStore: SettingsStore,
    private val clock: Clock,
    private val timeZones: TimeZoneProvider,
) : ViewModel() {

    private fun today(): LocalDate = clock.now().toLocalDateTime(timeZones.current()).date

    private val queryControls = MutableStateFlow(QueryControls(CalendarViewMode.MONTH, monthStartOf(today())))
    private val selectedDate = MutableStateFlow(today())

    private val agenda = combine(queryControls, settingsStore.settings) { controls, settings -> controls to settings }
        .flatMapLatest { (controls, settings) ->
            val range = rangeFor(controls)
            calendarRepository.observeAgenda(range.from, range.to, settings.showMilestones)
        }

    val state: StateFlow<CalendarUiState> = combine(
        queryControls,
        agenda,
        selectedDate,
        settingsStore.settings,
        profileRepository.observe(),
    ) { controls, items, selected, settings, profile ->
        buildState(controls, items, selected, settings, profile)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), CalendarUiState())

    fun onEvent(event: CalendarUiEvent) {
        when (event) {
            is CalendarUiEvent.ChangeViewMode -> queryControls.update { it.copy(viewMode = event.mode) }
            CalendarUiEvent.PreviousMonth -> shiftMonth(-1)
            CalendarUiEvent.NextMonth -> shiftMonth(1)
            is CalendarUiEvent.SelectDate -> selectedDate.value = event.date
            is CalendarUiEvent.ToggleMilestones -> settingsStore.update { it.copy(showMilestones = event.enabled) }
        }
    }

    private fun shiftMonth(deltaMonths: Long) = queryControls.update {
        it.copy(displayedMonth = it.displayedMonth.plus(deltaMonths, DateTimeUnit.MONTH))
    }

    private fun rangeFor(controls: QueryControls): QueryRange = when (controls.viewMode) {
        CalendarViewMode.MONTH -> monthGridRange(controls.displayedMonth)
        CalendarViewMode.AGENDA -> QueryRange(today(), today().plus(AGENDA_WINDOW_DAYS, DateTimeUnit.DAY))
    }

    private fun buildState(
        controls: QueryControls,
        items: List<AgendaItem>,
        selected: LocalDate,
        settings: AppSettings,
        profile: Profile,
    ): CalendarUiState {
        val locale = profile.locale ?: Locale.getDefault().toLanguageTag()
        val resolver = BodyVocabularyResolver(clinicalLabels.forLocale(locale))
        val resolvedItems = items.map { it.toItemUi(resolver, profile) }
        return CalendarUiState(
            isLoading = false,
            viewMode = controls.viewMode,
            showMilestones = settings.showMilestones,
            monthLabel = monthLabel(controls.displayedMonth),
            monthDays = monthGrid(controls.displayedMonth, resolvedItems, selected, today()),
            selectedDate = selected,
            selectedDayItems = resolvedItems.filter { it.date == selected },
            agendaGroups = if (controls.viewMode == CalendarViewMode.AGENDA) {
                agendaGroups(
                    resolvedItems
                )
            } else {
                emptyList()
            },
        )
    }

    private fun AgendaItem.toItemUi(resolver: BodyVocabularyResolver, profile: Profile): CalendarItemUiState =
        when (this) {
            is AgendaItem.PlannedDose ->
                CalendarItemUiState.PlannedDoseItem(regimenId, date, at?.toLocalDateTime(timeZones.current())?.time)
            is AgendaItem.LoggedDose ->
                CalendarItemUiState.LoggedDoseItem(log.id, date, log.takenAt.localDateTime.time)
            is AgendaItem.Event -> CalendarItemUiState.EventItem(
                eventId = event.id,
                title = event.title,
                category = event.category,
                date = date,
                time = if (event.isAllDay) null else event.start.localDateTime.time,
                isCompleted = event.completedAt != null,
            )
            is AgendaItem.Milestone -> CalendarItemUiState.MilestoneItem(
                changeTypeCode = changeTypeCode,
                label = resolver.builtinLabel(changeTypeCode, profile.bodyVocabulary),
                date = date,
            )
        }
}

private fun monthStartOf(date: LocalDate): LocalDate = LocalDate(date.year, date.month, 1)

private fun mondayOf(date: LocalDate): LocalDate = date.minus(date.dayOfWeek.isoDayNumber - 1, DateTimeUnit.DAY)

/** Grade cheia de semanas (segunda a domingo) cobrindo o mês de [monthStart] (sempre dia 1). */
private fun monthGridRange(monthStart: LocalDate): QueryRange {
    val monthEnd = monthStart.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY)
    val gridStart = mondayOf(monthStart)
    val gridEnd = mondayOf(monthEnd).plus(DAYS_PER_WEEK - 1, DateTimeUnit.DAY)
    return QueryRange(gridStart, gridEnd)
}

private fun monthLabel(monthStart: LocalDate): String =
    DateTimeFormatter.ofPattern("LLLL yyyy", Locale.getDefault()).format(monthStart.toJavaLocalDate())

private fun monthGrid(
    monthStart: LocalDate,
    items: List<CalendarItemUiState>,
    selected: LocalDate,
    today: LocalDate,
): List<CalendarDayUiState> {
    val range = monthGridRange(monthStart)
    val itemsByDate = items.groupBy { it.date }
    val days = mutableListOf<CalendarDayUiState>()
    var date = range.from
    while (date <= range.to) {
        days += CalendarDayUiState(
            date = date,
            isCurrentMonth = date.month == monthStart.month && date.year == monthStart.year,
            isToday = date == today,
            isSelected = date == selected,
            markers = itemsByDate[date].orEmpty().map { it.kind() }.toSet(),
        )
        date = date.plus(1, DateTimeUnit.DAY)
    }
    return days
}

private fun CalendarItemUiState.kind(): CalendarItemKind = when (this) {
    is CalendarItemUiState.PlannedDoseItem -> CalendarItemKind.PLANNED_DOSE
    is CalendarItemUiState.LoggedDoseItem -> CalendarItemKind.LOGGED_DOSE
    is CalendarItemUiState.EventItem -> CalendarItemKind.EVENT
    is CalendarItemUiState.MilestoneItem -> CalendarItemKind.MILESTONE
}

private fun agendaGroups(items: List<CalendarItemUiState>): List<CalendarAgendaGroupUiState> =
    items.groupBy { it.date }.toSortedMap().map { (date, dayItems) -> CalendarAgendaGroupUiState(date, dayItems) }
