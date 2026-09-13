// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.body

/** De onde a próxima foto do formulário deve vir. */
sealed interface BodyPhotoPickSource {
    data object Camera : BodyPhotoPickSource

    data object Gallery : BodyPhotoPickSource
}
