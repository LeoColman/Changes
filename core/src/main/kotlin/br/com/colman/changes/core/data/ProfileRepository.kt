// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.core.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import br.com.colman.changes.core.db.sql.ChangesDatabase
import br.com.colman.changes.core.model.BodyVocabulary
import br.com.colman.changes.core.model.DomainError
import br.com.colman.changes.core.model.Profile
import br.com.colman.changes.core.model.Result
import br.com.colman.changes.core.model.TreatmentProtocol
import br.com.colman.changes.core.model.UnitSystem
import br.com.colman.changes.core.model.asFailure
import br.com.colman.changes.core.model.asSuccess
import br.com.colman.changes.core.model.isFiniteNumber
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

private const val MAX_HEIGHT_CM = 300.0
private const val MIN_BIRTH_YEAR = 1900

/** Perfil da pessoa. Linha única (Seção 6.1). */
public class ProfileRepository(
    private val database: ChangesDatabase,
    private val io: CoroutineDispatcher,
    private val clock: Clock,
) {
    public fun observe(): Flow<Profile> = database.profileQueries.get().asFlow().mapToOneOrNull(io).mapRow { row ->
        row?.toModel() ?: defaultProfile()
    }

    public suspend fun update(transform: (Profile) -> Profile): Result<Profile> = withContext(io) {
        val current = database.profileQueries.get().executeAsOneOrNull()?.toModel() ?: defaultProfile()
        val updated = transform(current)
        val error = profileError(updated)
        if (error != null) {
            error.asFailure()
        } else {
            val now = clock.now().toEpochMilliseconds()
            database.profileQueries.update(
                updated.displayName,
                updated.heightCm,
                updated.birthYear?.toLong(),
                updated.treatmentProtocol.code,
                updated.hrtStartDate?.asEpochDay(),
                updated.locale,
                updated.unitSystem.name,
                updated.showBmi.asLong(),
                Codecs.encodeVocabulary(updated.bodyVocabulary),
                now,
            )
            updated.asSuccess()
        }
    }

    private fun profileError(profile: Profile): DomainError? {
        val currentYear = clock.now().toLocalDateTime(TimeZone.UTC).year
        return firstError(
            profile.heightCm?.let(::heightError),
            profile.birthYear?.let { rangeError("birthYear", it, MIN_BIRTH_YEAR..currentYear) },
        )
    }

    /** Altura finita, maior que zero e até 300 cm. */
    private fun heightError(height: Double): DomainError? = when {
        !isFiniteNumber(height) -> DomainError.Invalid("heightCm", DomainError.Reason.NOT_FINITE)
        height <= 0.0 || height > MAX_HEIGHT_CM -> DomainError.Invalid("heightCm", DomainError.Reason.OUT_OF_RANGE)
        else -> null
    }

    private fun defaultProfile(): Profile = Profile(
        displayName = null,
        heightCm = null,
        birthYear = null,
        treatmentProtocol = TreatmentProtocol.MASCULINIZING,
        hrtStartDate = null,
        locale = null,
        unitSystem = UnitSystem.METRIC,
        showBmi = false,
        bodyVocabulary = BodyVocabulary(),
    )
}
