// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.core.data.backup

import br.com.colman.changes.core.model.getOrNull
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.random.Random

/** Seção 7.8, critérios 1 e 2, e Seção 11.2, itens 1, 2 e 6. */
class BackupSpec : BehaviorSpec({
    val iterations = roundTripIterations()

    Given("property 11.2.1 / criterion 7.8.1: any database state") {
        Then("export, then import into a clean install, reproduces every table and every media file") {
            checkAll(PropTestConfig(iterations = iterations), Arb.long()) { seed ->
                runTest {
                    val source = device()
                    StateBuilder(Random(seed)).build().applyTo(source)
                    val target = device()
                    target.import(source.exportBytes(), ImportMode.REPLACE)
                    target.dump() shouldBe source.dump()
                    target.mediaChecksums() shouldBe source.mediaChecksums()
                }
            }
        }
    }

    Given("property 11.2.2 / criterion 7.8.2: the same archive merged twice") {
        Then("the second merge changes nothing") {
            checkAll(PropTestConfig(iterations = iterations / 4), Arb.long()) { seed ->
                runTest {
                    val source = device()
                    StateBuilder(Random(seed)).build().applyTo(source)
                    val archive = source.exportBytes()
                    val target = device()
                    target.import(archive, ImportMode.MERGE)
                    val once = target.dump()
                    val plan = target.backup.plan(archiveOf(archive), ImportMode.MERGE).getOrNull().shouldNotBeNull()
                    plan.tables.sumOf { it.inserted + it.updated } shouldBe 0
                    target.backup.apply(plan)
                    target.dump() shouldBe once
                }
            }
        }
    }

    Given("property 11.2.6: two independent exports with ids in common") {
        Then("merge(a, b) == merge(b, a)") {
            checkAll(PropTestConfig(iterations = iterations / 4), Arb.long()) { seed ->
                runTest {
                    val builder = StateBuilder(Random(seed))
                    val a = builder.build()
                    val b = builder.conflicting(a)
                    val exportA = device().also { a.applyTo(it) }.exportBytes()
                    val exportB = device().also { b.applyTo(it) }.exportBytes()
                    val ab = device().apply {
                        import(exportA, ImportMode.MERGE)
                        import(exportB, ImportMode.MERGE)
                    }
                    val ba = device().apply {
                        import(exportB, ImportMode.MERGE)
                        import(exportA, ImportMode.MERGE)
                    }
                    ab.dump() shouldBe ba.dump()
                    ab.mediaChecksums() shouldBe ba.mediaChecksums()
                }
            }
        }
    }
})
