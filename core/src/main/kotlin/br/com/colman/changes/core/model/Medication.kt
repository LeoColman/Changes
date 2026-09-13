// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.core.model

import kotlin.uuid.Uuid

/** Via de administração. Aberta a outros protocolos: nada aqui é exclusivo de terapia masculinizante. */
public enum class Route {
    INTRAMUSCULAR,
    SUBCUTANEOUS,
    TRANSDERMAL,
    ORAL,
    SUBLINGUAL,
    TOPICAL,
    NASAL,
    OTHER,
    ;

    public val isInjection: Boolean get() = this == INTRAMUSCULAR || this == SUBCUTANEOUS
}

public enum class DoseUnit { MG, ML, IU, G, PUFF, PATCH }

public enum class ConcentrationUnit { MG_PER_ML, MG_PER_G }

public data class Concentration(val value: Double, val unit: ConcentrationUnit)

public data class Dose(val value: Double, val unit: DoseUnit)

/** Local de aplicação. Enum aberto: [OTHER] cobre qualquer local não listado. */
public enum class InjectionSite {
    THIGH_LEFT,
    THIGH_RIGHT,
    GLUTE_LEFT,
    GLUTE_RIGHT,
    DELTOID_LEFT,
    DELTOID_RIGHT,
    ABDOMEN_LEFT,
    ABDOMEN_RIGHT,
    OTHER,
}

public data class Medication(
    val id: Uuid,
    val name: String,
    val substance: String?,
    val defaultRoute: Route?,
    val concentration: Concentration?,
    val isBuiltin: Boolean,
    val isHidden: Boolean,
)
