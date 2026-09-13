// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.body

import androidx.compose.runtime.Immutable
import br.com.colman.changes.core.model.BodyChangeCategory
import br.com.colman.changes.core.model.BodyMeasurementUnit
import br.com.colman.changes.core.model.Intensity
import br.com.colman.changes.core.model.MediaAttachment
import br.com.colman.changes.core.model.RecordedTime

/** Resumo de um tipo de mudança na tela inicial: rótulo já resolvido e a última observação. */
@Immutable
data class BodyTypeSummary(
    val typeId: String,
    val label: String,
    val lastObservedAt: RecordedTime?,
    val lastIntensity: Intensity?,
    val lastPhoto: MediaAttachment?,
)

/** Uma categoria com os tipos visíveis dela, na ordem de exibição. */
@Immutable
data class BodyCategorySection(val category: BodyChangeCategory, val types: List<BodyTypeSummary>)

/** Resumo de uma entrada na linha do tempo de um tipo. */
@Immutable
data class BodyEntrySummary(
    val entryId: String,
    val observedAt: RecordedTime,
    val intensity: Intensity?,
    val measurementValue: Double?,
    val measurementUnit: BodyMeasurementUnit?,
    val notes: String?,
    val photo: MediaAttachment?,
)
