// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.core.model

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class ResultSpec : FunSpec({
    val error = DomainError.NotFound("thing", "42")

    test("success exposes its value") {
        val result = 3.asSuccess()
        result.isSuccess.shouldBeTrue()
        result.getOrNull() shouldBe 3
        result.errorOrNull().shouldBeNull()
        result.getOrElse { -1 } shouldBe 3
    }

    test("failure exposes its error") {
        val result: Result<Int> = error.asFailure()
        result.isSuccess.shouldBeFalse()
        result.getOrNull().shouldBeNull()
        result.errorOrNull() shouldBe error
        result.getOrElse { -1 } shouldBe -1
    }

    test("map transforms success and keeps failure") {
        3.asSuccess().map { it * 2 } shouldBe Result.Success(6)
        error.asFailure().map { 1 } shouldBe Result.Failure(error)
    }

    test("domain errors carry their data") {
        val invalid = DomainError.Invalid("dose", DomainError.Reason.NOT_POSITIVE)
        invalid.field shouldBe "dose"
        invalid.reason shouldBe DomainError.Reason.NOT_POSITIVE
        error.entity shouldBe "thing"
        error.id shouldBe "42"
        DomainError.Reason.entries.map { it.name } shouldBe listOf(
            "REQUIRED",
            "OUT_OF_RANGE",
            "NOT_FINITE",
            "IN_THE_FUTURE",
            "END_BEFORE_START",
            "NOT_POSITIVE",
            "BUILTIN_IMMUTABLE",
            "DUPLICATE",
        )
    }

    test("flatMap chains success and short-circuits failure") {
        3.asSuccess().flatMap { (it + 1).asSuccess() } shouldBe Result.Success(4)
        3.asSuccess().flatMap { error.asFailure() } shouldBe Result.Failure(error)
        error.asFailure().flatMap { 1.asSuccess() } shouldBe Result.Failure(error)
    }
})
