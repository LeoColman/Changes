// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.today

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import br.com.colman.changes.core.data.CalendarRepository
import br.com.colman.changes.core.data.DoseLogRepository
import br.com.colman.changes.core.data.MoodRepository
import br.com.colman.changes.core.data.NewCalendarEvent
import br.com.colman.changes.core.data.NewRegimen
import br.com.colman.changes.core.data.ProfileRepository
import br.com.colman.changes.core.data.RegimenRepository
import br.com.colman.changes.core.model.CalendarCategory
import br.com.colman.changes.core.model.Dose
import br.com.colman.changes.core.model.DoseLog
import br.com.colman.changes.core.model.DoseUnit
import br.com.colman.changes.core.model.InjectionSite
import br.com.colman.changes.core.model.MoodLog
import br.com.colman.changes.core.model.Regimen
import br.com.colman.changes.core.model.Route
import br.com.colman.changes.core.model.Schedule
import br.com.colman.changes.core.model.getOrNull
import br.com.colman.changes.core.testing.FixedClock
import br.com.colman.changes.core.testing.FixedTimeZoneProvider
import br.com.colman.changes.core.testing.MainDispatcherListener
import br.com.colman.changes.core.testing.testDatabase
import br.com.colman.changes.core.testing.testDataset
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.uuid.Uuid

/**
 * Spec do ViewModel da tela Hoje. Repositórios reais sobre um banco em memória ([testDatabase]),
 * relógio e fuso fixos ([FixedClock], [FixedTimeZoneProvider]): nenhum mock de repositório.
 */
class TodayViewModelSpec : FunSpec({
    extension(MainDispatcherListener())
    val io = UnconfinedTestDispatcher()

    lateinit var clock: FixedClock
    lateinit var timeZones: FixedTimeZoneProvider
    lateinit var regimenRepository: RegimenRepository
    lateinit var doseLogRepository: DoseLogRepository
    lateinit var moodRepository: MoodRepository
    lateinit var calendarRepository: CalendarRepository
    lateinit var viewModel: TodayViewModel
    lateinit var medicationId: Uuid
    lateinit var zone: TimeZone
    lateinit var today: LocalDate

    beforeTest {
        clock = FixedClock()
        timeZones = FixedTimeZoneProvider()
        zone = timeZones.zone
        today = clock.now().toLocalDateTime(zone).date
        val database = testDatabase(clock)
        regimenRepository = RegimenRepository(database, io, clock)
        doseLogRepository = DoseLogRepository(database, io, clock, timeZones)
        moodRepository = MoodRepository(database, io, clock, timeZones)
        val profileRepository = ProfileRepository(database, io, clock)
        calendarRepository = CalendarRepository(database, io, clock, timeZones, profileRepository, testDataset)
        medicationId = testDataset.medications.first().id
        viewModel = TodayViewModel(regimenRepository, doseLogRepository, moodRepository, calendarRepository, clock, timeZones)
    }

    suspend fun createRegimen(
        startDate: LocalDate,
        endDate: LocalDate? = null,
        route: Route = Route.INTRAMUSCULAR,
        schedule: Schedule = Schedule.IntervalDays(14),
        timeOfDay: LocalTime? = LocalTime(8, 0),
    ): Regimen {
        val result = regimenRepository.create(
            NewRegimen(
                medicationId = medicationId,
                dose = Dose(50.0, DoseUnit.MG),
                route = route,
                schedule = schedule,
                timeOfDay = timeOfDay,
                startDate = startDate,
                endDate = endDate,
                notes = null,
            ),
        )
        return result.getOrNull().shouldNotBeNull()
    }

    suspend fun createEvent(date: LocalDate, title: String, category: CalendarCategory = CalendarCategory.APPOINTMENT) {
        val start = LocalDateTime(date, LocalTime(10, 0)).toInstant(zone)
        calendarRepository.create(
            NewCalendarEvent(
                title = title,
                description = null,
                start = start,
                end = null,
                isAllDay = false,
                category = category,
                reminderMinutesBefore = null,
                recurrence = null,
            ),
        )
    }

    suspend fun ReceiveTurbine<TodayUiState>.awaitLoaded(): TodayUiState {
        var item = awaitItem()
        while (item.isLoading) item = awaitItem()
        return item
    }

    suspend fun logDoseAndAwaitId(regimenId: Uuid): Uuid {
        var doseLogId: Uuid? = null
        viewModel.effects.test {
            viewModel.onEvent(TodayUiEvent.LogDose(regimenId))
            doseLogId = (awaitItem() as TodayEffect.DoseLogged).doseLogId
            cancelAndIgnoreRemainingEvents()
        }
        return doseLogId.shouldNotBeNull()
    }

    suspend fun findDoseLog(id: Uuid): DoseLog? {
        val from = LocalDateTime(today.minus(2, DateTimeUnit.DAY), LocalTime(0, 0)).toInstant(zone)
        val to = LocalDateTime(today.plus(2, DateTimeUnit.DAY), LocalTime(0, 0)).toInstant(zone)
        return doseLogRepository.observeBetween(from, to).first().find { it.id == id }
    }

    test("sem regime ativo mostra estado vazio") {
        viewModel.state.test {
            val loaded = awaitLoaded()
            loaded.hasActiveRegimen shouldBe false
            loaded.nextDoses.shouldBeEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    test("próxima dose calculada pelo regime quando a dose de hoje ainda não foi registrada") {
        val regimen = createRegimen(startDate = today, timeOfDay = LocalTime(8, 0))
        viewModel.state.test {
            val loaded = awaitLoaded()
            loaded.hasActiveRegimen shouldBe true
            loaded.nextDoses shouldHaveSize 1
            loaded.nextDoses.first().regimenId shouldBe regimen.id
            loaded.nextDoses.first().date shouldBe today
            loaded.nextDoses.first().time shouldBe LocalTime(8, 0)
            cancelAndIgnoreRemainingEvents()
        }
    }

    test("dose prevista no passado sem registro aparece como próxima dose, como fato neutro") {
        val regimen = createRegimen(startDate = today.minus(1, DateTimeUnit.DAY), schedule = Schedule.IntervalDays(30))
        viewModel.state.test {
            val loaded = awaitLoaded()
            loaded.nextDoses shouldHaveSize 1
            loaded.nextDoses.first().regimenId shouldBe regimen.id
            loaded.nextDoses.first().date shouldBe today.minus(1, DateTimeUnit.DAY)
            cancelAndIgnoreRemainingEvents()
        }
    }

    test("dose registrada com atraso cobre a dose prevista: a próxima passa a ser a seguinte") {
        val start = today.minus(3, DateTimeUnit.DAY)
        val regimen = createRegimen(startDate = start, schedule = Schedule.IntervalDays(30))
        val yesterday = LocalDateTime(today.minus(1, DateTimeUnit.DAY), LocalTime(9, 0)).toInstant(zone)
        doseLogRepository.logFromRegimen(regimen.id, yesterday, null).getOrNull().shouldNotBeNull()
        viewModel.state.test {
            val loaded = awaitLoaded()
            loaded.nextDoses.single().date shouldBe start.plus(30, DateTimeUnit.DAY)
            cancelAndIgnoreRemainingEvents()
        }
    }

    test("regime sem agenda fixa (AS_NEEDED) não aparece na próxima dose") {
        createRegimen(startDate = today, schedule = Schedule.AsNeeded, timeOfDay = null)
        viewModel.state.test {
            val loaded = awaitLoaded()
            loaded.hasActiveRegimen shouldBe true
            loaded.nextDoses.shouldBeEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    test("regime encerrado não aparece na próxima dose (critério 7.9.2)") {
        createRegimen(
            startDate = today.minus(10, DateTimeUnit.DAY),
            endDate = today.minus(1, DateTimeUnit.DAY),
            schedule = Schedule.IntervalDays(1),
        )
        viewModel.state.test {
            val loaded = awaitLoaded()
            loaded.hasActiveRegimen shouldBe true
            loaded.nextDoses.shouldBeEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    test("registrar em um toque grava a dose do regime com hora atual e local sugerido") {
        val regimen = createRegimen(startDate = today, route = Route.INTRAMUSCULAR)
        val doseLogId = logDoseAndAwaitId(regimen.id)
        val log = findDoseLog(doseLogId).shouldNotBeNull()
        log.regimenId shouldBe regimen.id
        log.medicationId shouldBe medicationId
        log.takenAt.instant shouldBe clock.now()
        log.injectionSite shouldBe InjectionSite.GLUTE_LEFT
    }

    test("registrar em um toque não sugere local para via não injetável") {
        val regimen = createRegimen(startDate = today, route = Route.ORAL)
        val doseLogId = logDoseAndAwaitId(regimen.id)
        val log = findDoseLog(doseLogId).shouldNotBeNull()
        log.injectionSite.shouldBeNull()
    }

    test("sugestão de local de aplicação roda entre injeções sucessivas") {
        val regimen = createRegimen(startDate = today, route = Route.INTRAMUSCULAR)
        val firstId = logDoseAndAwaitId(regimen.id)
        val secondId = logDoseAndAwaitId(regimen.id)
        findDoseLog(firstId).shouldNotBeNull().injectionSite shouldBe InjectionSite.GLUTE_LEFT
        findDoseLog(secondId).shouldNotBeNull().injectionSite shouldBe InjectionSite.GLUTE_RIGHT
    }

    test("Desfazer remove o registro feito em um toque") {
        val regimen = createRegimen(startDate = today, route = Route.INTRAMUSCULAR)
        val doseLogId = logDoseAndAwaitId(regimen.id)
        findDoseLog(doseLogId).shouldNotBeNull()
        viewModel.onEvent(TodayUiEvent.UndoDoseLog(doseLogId))
        findDoseLog(doseLogId).shouldBeNull()
    }

    test("check-in não feito quando não há registro do dia") {
        viewModel.state.test {
            val loaded = awaitLoaded()
            loaded.moodCheckedIn shouldBe false
            cancelAndIgnoreRemainingEvents()
        }
    }

    test("check-in feito quando já existe registro do dia") {
        moodRepository.upsert(
            MoodLog(
                id = Uuid.random(),
                date = today,
                mood = 3,
                energy = 3,
                anxiety = null,
                dysphoria = null,
                sleepHours = null,
                note = null,
                tags = emptyList(),
            ),
        )
        viewModel.state.test {
            val loaded = awaitLoaded()
            loaded.moodCheckedIn shouldBe true
            cancelAndIgnoreRemainingEvents()
        }
    }

    test("agenda limitada a 5 itens e à janela de 7 dias") {
        for (offset in 1..6) createEvent(today.plus(offset, DateTimeUnit.DAY), "Evento $offset")
        createEvent(today.plus(8, DateTimeUnit.DAY), "Fora da janela")
        viewModel.state.test {
            val loaded = awaitLoaded()
            loaded.agenda shouldHaveSize 5
            loaded.agenda.map { (it as AgendaEntryUi.EventEntry).title } shouldBe
                listOf("Evento 1", "Evento 2", "Evento 3", "Evento 4", "Evento 5")
            cancelAndIgnoreRemainingEvents()
        }
    }

    test("dose prevista dentro dos próximos 7 dias aparece na agenda") {
        val regimen = createRegimen(startDate = today.plus(2, DateTimeUnit.DAY), schedule = Schedule.IntervalDays(14))
        viewModel.state.test {
            val loaded = awaitLoaded()
            val planned = loaded.agenda.filterIsInstance<AgendaEntryUi.PlannedDoseEntry>()
            planned shouldHaveSize 1
            planned.first().regimenId shouldBe regimen.id
            planned.first().date shouldBe today.plus(2, DateTimeUnit.DAY)
            cancelAndIgnoreRemainingEvents()
        }
    }
})
