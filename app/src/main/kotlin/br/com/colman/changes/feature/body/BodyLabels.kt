// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.body

import br.com.colman.changes.core.clinical.BodyVocabularyResolver
import br.com.colman.changes.core.clinical.ClinicalLabels
import br.com.colman.changes.core.data.ProfileRepository
import br.com.colman.changes.core.model.BodyChangeType
import br.com.colman.changes.core.model.BodyVocabulary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Locale

/**
 * Combina o vocabulário escolhido no perfil com os rótulos do locale atual para resolver nomes de
 * mudanças corporais (Seção 7.10). Reúne [ProfileRepository] e [ClinicalLabels] num único ponto de
 * injeção para os ViewModels de `feature.body`, mantendo a lista de dependências curta.
 */
class BodyLabels(private val profileRepository: ProfileRepository, clinicalLabels: ClinicalLabels) {
    private val resolver = BodyVocabularyResolver(clinicalLabels.forLocale(Locale.getDefault().toLanguageTag()))

    /** Vocabulário atual da pessoa, atualizado sempre que o perfil muda. */
    fun observeVocabulary(): Flow<BodyVocabulary> = profileRepository.observe().map { it.bodyVocabulary }

    /** Rótulo de um tipo (builtin resolvido pelo vocabulário, ou o texto customizado da pessoa). */
    fun label(type: BodyChangeType, vocabulary: BodyVocabulary): String = resolver.label(type, vocabulary)
}
