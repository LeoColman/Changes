// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.colman.changes.core.data.CalendarRepository
import br.com.colman.changes.core.data.DoseLogRepository
import br.com.colman.changes.core.data.MoodRepository
import br.com.colman.changes.core.data.RegimenRepository
import br.com.colman.changes.core.model.AgendaItem
import br.com.colman.changes.core.model.DoseSchedule
import br.com.colman.changes.core.model.MoodLog
import br.com.colman.changes.core.model.Regimen
import br.com.colman.changes.core.model.TimeZoneProvider
import br.com.colman.changes.core.model.errorOrNull
import br.com.colman.changes.core.model.getOrNull
import br.com.colman.changes.platform.AppLogger
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.uuid.Uuid

private const val AGENDA_FORWARD_DAYS = 7
private const val MISSED_DOSE_LOOKBACK_DAYS = 30
private const val MAX_AGENDA_ITEMS = 5
private const val STOP_TIMEOUT_MS = 5_000L
private const val LOG_TAG = "Today"

/**
 * Tela Hoje (Seção 9): próxima dose de um toque (7.1), check-in do dia (7.7) e os próximos eventos
 * da agenda (7.9). "Agora" e "hoje" só vêm de [clock] e [timeZoneProvider], nunca de `Clock.System`.
 */
class TodayViewModel(
    private val regimenRepository: RegimenRepository,
    private val doseLogRepository: DoseLogRepository,
    private val moodRepository: MoodRepository,
    private val calendarRepository: CalendarRepository,
    private val clock: Clock,
    private val timeZoneProvider: TimeZoneProvider,
) : ViewModel() {

    private val effectChannel = Channel<TodayEffect>(Channel.BUFFERED)
    val effects: Flow<TodayEffect> = effectChannel.receiveAsFlow()

    val state: StateFlow<TodayUiState> = combine(
        regimenRepository.observeActive(),
        moodRepository.observe(today()),
        calendarRepository.observeAgenda(agendaFrom(), agendaTo(), includeMilestones = false),
    ) { regimens, moodLog, agenda ->
        buildState(regimens, moodLog, agenda)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), TodayUiState())

    fun onEvent(event: TodayUiEvent) {
        when (event) {
            is TodayUiEvent.LogDose -> logDose(event.regimenId)
            is TodayUiEvent.UndoDoseLog -> undoDoseLog(event.doseLogId)
        }
    }

    private fun today(): LocalDate = clock.now().toLocalDateTime(timeZoneProvider.current()).date

    /** Início da janela usada para achar uma dose prevista não registrada (Seção 7.1). */
    private fun agendaFrom(): LocalDate = today().minus(MISSED_DOSE_LOOKBACK_DAYS, DateTimeUnit.DAY)

    private fun agendaTo(): LocalDate = today().plus(AGENDA_FORWARD_DAYS, DateTimeUnit.DAY)

    private fun logDose(regimenId: Uuid) {
        viewModelScope.launch {
            val regimen = regimenRepository.get(regimenId) ?: return@launch
            val site = if (regimen.route.isInjection) doseLogRepository.suggestInjectionSite() else null
            val result = doseLogRepository.logFromRegimen(regimenId, clock.now(), site)
            val logged = result.getOrNull()
            if (logged != null) {
                effectChannel.send(TodayEffect.DoseLogged(logged.id))
            } else {
                AppLogger.warn(LOG_TAG) { "logDose falhou: ${result.errorOrNull()}" }
            }
        }
    }

    private fun undoDoseLog(doseLogId: Uuid) {
        viewModelScope.launch { doseLogRepository.delete(doseLogId) }
    }

    private fun buildState(regimens: List<Regimen>, moodLog: MoodLog?, agenda: List<AgendaItem>): TodayUiState {
        val today = today()
        val zone = timeZoneProvider.current()
        val window = today..agendaTo()
        val upcoming = agenda.filter { it.date in window }.mapNotNull { it.toEntryUi(zone) }.take(MAX_AGENDA_ITEMS)
        return TodayUiState(
            isLoading = false,
            hasActiveRegimen = regimens.isNotEmpty(),
            nextDoses = regimens.mapNotNull { nextDoseFor(it, today, agenda) },
            moodCheckedIn = moodLog != null,
            agenda = upcoming,
        )
    }

    /**
     * Próxima dose de um regime (Seção 7.1): a dose prevista mais antiga, até hoje, posterior ao último
     * registro do regime (um registro feito com atraso cobre a dose prevista antes dele); sem nenhuma
     * pendente, a próxima ocorrência a partir de amanhã. Regime encerrado (`endDate` no passado) nunca
     * aparece (critério 7.9.2).
     */
    private fun nextDoseFor(regimen: Regimen, today: LocalDate, agenda: List<AgendaItem>): NextDoseUi? {
        val endDate = regimen.endDate
        if (endDate != null && endDate < today) return null
        val lastLogged = agenda.filterIsInstance<AgendaItem.LoggedDose>()
            .filter { it.log.regimenId == regimen.id }
            .maxOfOrNull { it.date }
        val due = agenda.filterIsInstance<AgendaItem.PlannedDose>()
            .filter { it.regimenId == regimen.id && it.date <= today && (lastLogged == null || it.date > lastLogged) }
            .minOfOrNull { it.date }
        val date = due ?: DoseSchedule.next(regimen, today.plus(1, DateTimeUnit.DAY)) ?: return null
        return NextDoseUi(regimen.id, date, regimen.timeOfDay)
    }

    private fun AgendaItem.toEntryUi(zone: TimeZone): AgendaEntryUi? = when (this) {
        is AgendaItem.PlannedDose -> AgendaEntryUi.PlannedDoseEntry(regimenId, date, at?.toLocalDateTime(zone)?.time)
        is AgendaItem.Event -> AgendaEntryUi.EventEntry(
            event.id,
            event.title,
            event.category,
            date,
            if (event.isAllDay) null else event.start.localDateTime.time,
        )
        else -> null
    }
}
