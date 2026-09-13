// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.core.model

import br.com.colman.changes.core.model.InjectionSite.ABDOMEN_LEFT
import br.com.colman.changes.core.model.InjectionSite.ABDOMEN_RIGHT
import br.com.colman.changes.core.model.InjectionSite.DELTOID_LEFT
import br.com.colman.changes.core.model.InjectionSite.DELTOID_RIGHT
import br.com.colman.changes.core.model.InjectionSite.GLUTE_LEFT
import br.com.colman.changes.core.model.InjectionSite.GLUTE_RIGHT
import br.com.colman.changes.core.model.InjectionSite.OTHER
import br.com.colman.changes.core.model.InjectionSite.THIGH_LEFT
import br.com.colman.changes.core.model.InjectionSite.THIGH_RIGHT
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class InjectionSiteRotationSpec : FunSpec({
    test("with no history, suggests the first site of the rotation") {
        InjectionSiteRotation.suggest(emptyList()) shouldBe GLUTE_LEFT
    }

    test("suggests an unused site before any used one") {
        InjectionSiteRotation.suggest(listOf(GLUTE_LEFT)) shouldBe GLUTE_RIGHT
        InjectionSiteRotation.suggest(listOf(GLUTE_RIGHT, GLUTE_LEFT)) shouldBe THIGH_LEFT
    }

    test("suggests the least used site in the window") {
        val recent = listOf(
            GLUTE_LEFT,
            GLUTE_RIGHT,
            THIGH_LEFT,
            THIGH_RIGHT,
            DELTOID_LEFT,
            DELTOID_RIGHT,
            ABDOMEN_LEFT,
            GLUTE_LEFT,
        )
        InjectionSiteRotation.suggest(recent) shouldBe ABDOMEN_RIGHT
    }

    test("breaks ties by the site used longest ago") {
        val recent =
            listOf(
                THIGH_LEFT,
                ABDOMEN_RIGHT,
                GLUTE_LEFT,
                DELTOID_RIGHT,
                GLUTE_RIGHT,
                ABDOMEN_LEFT,
                THIGH_RIGHT,
                DELTOID_LEFT
            )
        InjectionSiteRotation.suggest(recent) shouldBe DELTOID_LEFT
    }

    test("only the last ${InjectionSiteRotation.WINDOW} records count") {
        val recent = listOf(GLUTE_RIGHT, THIGH_LEFT, THIGH_RIGHT, DELTOID_LEFT, DELTOID_RIGHT, ABDOMEN_LEFT, ABDOMEN_RIGHT, GLUTE_RIGHT) +
            List(20) { GLUTE_LEFT }
        InjectionSiteRotation.suggest(recent) shouldBe GLUTE_LEFT
    }

    test("OTHER in the history does not count against any site") {
        InjectionSiteRotation.suggest(listOf(OTHER, OTHER, GLUTE_LEFT)) shouldBe GLUTE_RIGHT
    }

    test("respects a restricted candidate list and returns null for none") {
        InjectionSiteRotation.suggest(listOf(ABDOMEN_LEFT), listOf(ABDOMEN_LEFT, ABDOMEN_RIGHT)) shouldBe ABDOMEN_RIGHT
        InjectionSiteRotation.suggest(listOf(ABDOMEN_LEFT), emptyList()).shouldBeNull()
    }

    test("the rotation lists every concrete site once") {
        InjectionSiteRotation.ROTATION.toSet() shouldBe InjectionSite.entries.toSet() - OTHER
        InjectionSiteRotation.ROTATION.size shouldBe 8
    }
})
