// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.nav

import kotlinx.serialization.Serializable

// Rotas type-safe (Navigation Compose). Features não conhecem rotas: recebem callbacks de navegação
// e é o grafo em nav/ que liga uma feature à outra (Seção 4.2).

@Serializable
data object TodayRoute

@Serializable
data object BodyRoute

@Serializable
data object HealthRoute

@Serializable
data object CalendarRoute

@Serializable
data object SettingsRoute

@Serializable
data class LogDoseRoute(val regimenId: String? = null, val plannedEpochDay: Long? = null, val doseLogId: String? = null)

@Serializable
data object DoseHistoryRoute

@Serializable
data class RegimenEditRoute(val regimenId: String? = null)

@Serializable
data object RegimenListRoute

@Serializable
data class BodyChangeTypeRoute(val typeId: String)

@Serializable
data class BodyEntryEditRoute(val typeId: String? = null, val entryId: String? = null)

@Serializable
data object ExpectedChangesRoute

@Serializable
data object MeasurementsRoute

@Serializable
data object ExerciseRoute

@Serializable
data object ConditionsRoute

@Serializable
data class LabResultsRoute(val analyteId: String? = null)

@Serializable
data class MoodCheckInRoute(val epochDay: Long? = null)

@Serializable
data object MoodHistoryRoute

@Serializable
data class EventEditRoute(val eventId: String? = null, val epochDay: Long? = null)

@Serializable
data object BackupRoute

@Serializable
data object ImportPreviewRoute

@Serializable
data object ProfileRoute

@Serializable
data object VocabularyRoute

@Serializable
data object TrashRoute

@Serializable
data object AboutRoute

@Serializable
data object LicensesRoute

@Serializable
data object SupportRoute

@Serializable
data object OnboardingRoute
