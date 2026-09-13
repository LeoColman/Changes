// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.body

/** Para onde a tela inicial da aba Corpo pode navegar (Seção 4.2: features só navegam por callback). */
sealed interface BodyHomeNavigation {
    data object ExpectedChanges : BodyHomeNavigation

    data object Measurements : BodyHomeNavigation

    data class ChangeType(val typeId: String) : BodyHomeNavigation
}
