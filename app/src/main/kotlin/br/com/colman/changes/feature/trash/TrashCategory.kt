// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.trash

import br.com.colman.changes.core.data.TrashKind

/**
 * Agrupamento de exibição da lixeira (Seção 9): tipos aparentados (o tipo de mudança corporal e os
 * registros dele; o analito de exame e os resultados dele) aparecem juntos, com um nome amigável comum.
 */
enum class TrashCategory {
    MEDICATION,
    REGIMEN,
    DOSE_LOG,
    BODY_CHANGE,
    MEASUREMENT,
    EXERCISE,
    HEALTH_CONDITION,
    LAB,
    MOOD,
    CALENDAR_EVENT,
}

/** Cada [TrashKind] pertence a exatamente uma categoria. `when` sem `else`: um tipo novo quebra o build. */
fun trashCategoryOf(kind: TrashKind): TrashCategory = when (kind) {
    TrashKind.MEDICATION -> TrashCategory.MEDICATION
    TrashKind.REGIMEN -> TrashCategory.REGIMEN
    TrashKind.DOSE_LOG -> TrashCategory.DOSE_LOG
    TrashKind.BODY_CHANGE_TYPE, TrashKind.BODY_CHANGE_ENTRY -> TrashCategory.BODY_CHANGE
    TrashKind.MEASUREMENT -> TrashCategory.MEASUREMENT
    TrashKind.EXERCISE -> TrashCategory.EXERCISE
    TrashKind.HEALTH_CONDITION -> TrashCategory.HEALTH_CONDITION
    TrashKind.LAB_ANALYTE, TrashKind.LAB_RESULT -> TrashCategory.LAB
    TrashKind.MOOD -> TrashCategory.MOOD
    TrashKind.CALENDAR_EVENT -> TrashCategory.CALENDAR_EVENT
}
