// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.core.data

import br.com.colman.changes.core.db.sql.Dose_log
import br.com.colman.changes.core.model.Concentration
import br.com.colman.changes.core.model.ConcentrationUnit
import br.com.colman.changes.core.model.Dose
import br.com.colman.changes.core.model.DoseLog
import br.com.colman.changes.core.model.DoseUnit
import br.com.colman.changes.core.model.InjectionSite
import br.com.colman.changes.core.model.RecordedTime
import br.com.colman.changes.core.model.Route
import br.com.colman.changes.core.model.ScheduleType
import br.com.colman.changes.core.model.TreatmentProtocol
import br.com.colman.changes.core.model.UnitSystem
import br.com.colman.changes.core.db.sql.Medication as MedicationRow
import br.com.colman.changes.core.db.sql.Profile as ProfileRow
import br.com.colman.changes.core.db.sql.Regimen as RegimenRow
import br.com.colman.changes.core.model.Medication as MedicationModel
import br.com.colman.changes.core.model.Profile as ProfileModel
import br.com.colman.changes.core.model.Regimen as RegimenModel

internal fun ProfileRow.toModel(): ProfileModel = ProfileModel(
    displayName = display_name,
    heightCm = height_cm,
    birthYear = birth_year?.toInt(),
    treatmentProtocol = TreatmentProtocol(treatment_protocol),
    hrtStartDate = hrt_start_date?.asLocalDate(),
    locale = locale,
    unitSystem = UnitSystem.valueOf(unit_system),
    showBmi = show_bmi.asBoolean(),
    bodyVocabulary = Codecs.decodeVocabulary(body_vocabulary),
)

internal fun MedicationRow.toModel(): MedicationModel = MedicationModel(
    id = id.asUuid(),
    name = name,
    substance = substance,
    defaultRoute = default_route?.let { Route.valueOf(it) },
    concentration = concentration_value?.let { value ->
        Concentration(value, ConcentrationUnit.valueOf(checkNotNull(concentration_unit)))
    },
    isBuiltin = is_builtin.asBoolean(),
    isHidden = is_hidden.asBoolean(),
)

internal fun MedicationModel.toRow(createdAt: Long, updatedAt: Long): MedicationRow = MedicationRow(
    id = id.toString(),
    name = name,
    substance = substance,
    default_route = defaultRoute?.name,
    concentration_value = concentration?.value,
    concentration_unit = concentration?.unit?.name,
    is_builtin = isBuiltin.asLong(),
    is_hidden = isHidden.asLong(),
    created_at = createdAt,
    updated_at = updatedAt,
    deleted_at = null,
)

internal fun RegimenRow.toModel(): RegimenModel = RegimenModel(
    id = id.asUuid(),
    medicationId = medication_id.asUuid(),
    dose = Dose(dose_value, DoseUnit.valueOf(dose_unit)),
    route = Route.valueOf(route),
    schedule = checkNotNull(Codecs.decodeSchedule(ScheduleType.valueOf(schedule_type), schedule_config)) {
        "Corrupted schedule for regimen $id"
    },
    timeOfDay = time_of_day?.asLocalTime(),
    startDate = start_date.asLocalDate(),
    endDate = end_date?.asLocalDate(),
    isActive = is_active.asBoolean(),
    notes = notes,
)

internal fun RegimenModel.toRow(createdAt: Long, updatedAt: Long): RegimenRow = RegimenRow(
    id = id.toString(),
    medication_id = medicationId.toString(),
    dose_value = dose.value,
    dose_unit = dose.unit.name,
    route = route.name,
    schedule_type = schedule.type.name,
    schedule_config = Codecs.encodeSchedule(schedule),
    time_of_day = timeOfDay?.asMinutes(),
    start_date = startDate.asEpochDay(),
    end_date = endDate?.asEpochDay(),
    is_active = isActive.asLong(),
    notes = notes,
    created_at = createdAt,
    updated_at = updatedAt,
    deleted_at = null,
)

internal fun Dose_log.toModel(): DoseLog = DoseLog(
    id = id.asUuid(),
    regimenId = regimen_id?.asUuid(),
    medicationId = medication_id.asUuid(),
    dose = Dose(dose_value, DoseUnit.valueOf(dose_unit)),
    route = Route.valueOf(route),
    injectionSite = injection_site?.let { InjectionSite.valueOf(it) },
    takenAt = RecordedTime.fromDb(taken_at, taken_at_offset_seconds),
    notes = notes,
)

internal fun DoseLog.toRow(createdAt: Long, updatedAt: Long): Dose_log = Dose_log(
    id = id.toString(),
    regimen_id = regimenId?.toString(),
    medication_id = medicationId.toString(),
    dose_value = dose.value,
    dose_unit = dose.unit.name,
    route = route.name,
    injection_site = injectionSite?.name,
    taken_at = takenAt.epochMillis,
    taken_at_offset_seconds = takenAt.offsetSeconds,
    notes = notes,
    created_at = createdAt,
    updated_at = updatedAt,
    deleted_at = null,
)
