// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.body

/** Para onde a linha do tempo de um tipo pode navegar. */
sealed interface BodyChangeTypeNavigation {
    data object Back : BodyChangeTypeNavigation

    data object AddEntry : BodyChangeTypeNavigation

    data class EditEntry(val entryId: String) : BodyChangeTypeNavigation
}
