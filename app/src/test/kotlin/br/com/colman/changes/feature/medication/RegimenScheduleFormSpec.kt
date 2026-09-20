// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.medication

import br.com.colman.changes.core.model.Schedule
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate

private val baseState = RegimenEditUiState(startDate = LocalDate(2026, 1, 1))

/** ADR 0007: tradução entre as opções de agenda da tela e os tipos do domínio, nos dois sentidos. */
class RegimenScheduleFormSpec : FunSpec({
    test("a stepped schedule with a continuous tail fills the continuous fields") {
        val schedule = Schedule.Stepped(steps = listOf(3, 5), thenEvery = 7)

        val state = baseState.withSchedule(schedule)

        state.scheduleOption shouldBe ScheduleOption.STEPPED
        state.stepDays shouldBe listOf("3", "5")
        state.continuous shouldBe true
        state.continuousEveryDays shouldBe "7"
    }

    test("a stepped schedule without a continuous tail leaves the continuous fields empty and off") {
        val schedule = Schedule.Stepped(steps = listOf(3), thenEvery = null)

        val state = baseState.withSchedule(schedule)

        state.continuous shouldBe false
        state.continuousEveryDays shouldBe ""
    }

    test("an as-needed schedule selects the as-needed option") {
        val state = baseState.withSchedule(Schedule.AsNeeded)

        state.scheduleOption shouldBe ScheduleOption.AS_NEEDED
    }
})
