// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.core.model

import kotlinx.datetime.LocalDate

/** Protocolo de tratamento. Código aberto (não é enum) para acomodar outros protocolos no futuro. */
@JvmInline
public value class TreatmentProtocol(public val code: String) {
    public companion object {
        public val MASCULINIZING: TreatmentProtocol = TreatmentProtocol("MASCULINIZING")
    }
}

public enum class UnitSystem { METRIC, IMPERIAL }

public data class Profile(
    val displayName: String?,
    val heightCm: Double?,
    val birthYear: Int?,
    val treatmentProtocol: TreatmentProtocol,
    /** Âncora de todo cálculo de "tempo de tratamento". */
    val hrtStartDate: LocalDate?,
    val locale: String?,
    val unitSystem: UnitSystem,
    val showBmi: Boolean,
    val bodyVocabulary: BodyVocabulary,
)
