// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.core.model

/**
 * Sugestão mecânica de rodízio (Seção 7.1): o local menos usado nos últimos [WINDOW] registros.
 * Empate: o usado há mais tempo (nunca usado primeiro), depois a ordem de [ROTATION].
 * Não é recomendação clínica.
 */
public object InjectionSiteRotation {
    public const val WINDOW: Int = 8

    public val ROTATION: List<InjectionSite> = listOf(
        InjectionSite.GLUTE_LEFT,
        InjectionSite.GLUTE_RIGHT,
        InjectionSite.THIGH_LEFT,
        InjectionSite.THIGH_RIGHT,
        InjectionSite.DELTOID_LEFT,
        InjectionSite.DELTOID_RIGHT,
        InjectionSite.ABDOMEN_LEFT,
        InjectionSite.ABDOMEN_RIGHT,
    )

    /** [recentMostRecentFirst]: locais dos registros anteriores, do mais recente para o mais antigo. */
    public fun suggest(
        recentMostRecentFirst: List<InjectionSite>,
        candidates: List<InjectionSite> = ROTATION,
    ): InjectionSite? {
        val window = recentMostRecentFirst.take(WINDOW)
        var best: InjectionSite? = null
        var bestUses = Int.MAX_VALUE
        var bestAge = Int.MIN_VALUE
        for (site in candidates) {
            val uses = usesOf(site, window)
            // Índice do último uso: maior = mais antigo. Nunca usado (-1) só empata com outro nunca usado,
            // porque aí o número de usos (0) já decidiu contra qualquer local usado.
            val age = window.indexOf(site)
            if (uses < bestUses || (uses == bestUses && age > bestAge)) {
                best = site
                bestUses = uses
                bestAge = age
            }
        }
        return best
    }

    private fun usesOf(site: InjectionSite, window: List<InjectionSite>): Int {
        var uses = 0
        for (used in window) {
            if (used == site) uses++
        }
        return uses
    }
}
