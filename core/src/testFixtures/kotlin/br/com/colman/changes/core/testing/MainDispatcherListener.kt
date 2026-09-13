// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.core.testing

import io.kotest.core.listeners.TestListener
import io.kotest.core.test.TestCase
import io.kotest.engine.test.TestResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/** Troca `Dispatchers.Main` por um [TestDispatcher] em cada teste (ViewModels). */
public class MainDispatcherListener(public val dispatcher: TestDispatcher = UnconfinedTestDispatcher()) : TestListener {
    override suspend fun beforeTest(testCase: TestCase) {
        Dispatchers.setMain(dispatcher)
    }

    override suspend fun afterTest(testCase: TestCase, result: TestResult) {
        Dispatchers.resetMain()
    }
}
