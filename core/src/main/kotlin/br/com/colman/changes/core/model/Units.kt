// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.core.model

/**
 * `true` para número finito. Chamada direta ao JDK: a versão inline do stdlib copiaria comparações
 * para o chamador e geraria mutantes impossíveis de matar (docs/core-guidelines.md).
 */
internal fun isFiniteNumber(value: Double): Boolean = java.lang.Double.isFinite(value)

/**
 * Conversões de unidade. Toda conversão devolve [Result]: entrada ou saída não finita vira
 * `Failure(NOT_FINITE)`, nunca `NaN` ou `Infinity` (Seção 11.2, item 4).
 */
public object Units {
    public const val KG_PER_LB: Double = 0.45359237
    public const val CM_PER_INCH: Double = 2.54

    private val FACTORS: Map<Pair<MeasurementUnit, MeasurementUnit>, Double> = mapOf(
        (MeasurementUnit.KG to MeasurementUnit.LB) to 1.0 / KG_PER_LB,
        (MeasurementUnit.LB to MeasurementUnit.KG) to KG_PER_LB,
        (MeasurementUnit.CM to MeasurementUnit.IN) to 1.0 / CM_PER_INCH,
        (MeasurementUnit.IN to MeasurementUnit.CM) to CM_PER_INCH,
    )

    public fun convert(value: Double, from: MeasurementUnit, to: MeasurementUnit): Result<Double> {
        if (!isFiniteNumber(value)) return notFinite("value")
        if (from == to) return value.asSuccess()
        val factor = FACTORS[from to to]
            ?: return DomainError.Invalid("unit", DomainError.Reason.OUT_OF_RANGE).asFailure()
        return finite(value * factor)
    }

    // Entrada não finita sempre produz saída não finita, que `finite` rejeita: não há checagem de
    // entrada separada (seria um ramo sem efeito observável).

    /** mg para mL, dada a concentração em mg/mL. */
    public fun mgToMl(mg: Double, concentration: Concentration): Result<Double> =
        perMl(concentration).flatMap { finite(mg / it) }

    /** mL para mg, dada a concentração em mg/mL. */
    public fun mlToMg(ml: Double, concentration: Concentration): Result<Double> =
        perMl(concentration).flatMap { finite(ml * it) }

    private fun perMl(concentration: Concentration): Result<Double> = when {
        concentration.unit != ConcentrationUnit.MG_PER_ML ->
            DomainError.Invalid("concentration", DomainError.Reason.OUT_OF_RANGE).asFailure()
        !isFiniteNumber(concentration.value) -> notFinite("concentration")
        concentration.value <= 0.0 -> DomainError.Invalid("concentration", DomainError.Reason.NOT_POSITIVE).asFailure()
        else -> concentration.value.asSuccess()
    }

    private fun finite(output: Double): Result<Double> =
        if (isFiniteNumber(output)) output.asSuccess() else notFinite("value")

    private fun notFinite(field: String): Result<Nothing> =
        DomainError.Invalid(field, DomainError.Reason.NOT_FINITE).asFailure()
}

/** IMC é sempre calculado, nunca persistido. Sem classificação, sem faixa, sem meta (Seção 7.4). */
public object Bmi {
    private const val CM_PER_M = 100.0

    public fun calculate(weightKg: Double, heightCm: Double): Result<Double> = when {
        !isFiniteNumber(weightKg) -> invalid("weightKg", DomainError.Reason.NOT_FINITE)
        !isFiniteNumber(heightCm) -> invalid("heightCm", DomainError.Reason.NOT_FINITE)
        weightKg <= 0.0 -> invalid("weightKg", DomainError.Reason.NOT_POSITIVE)
        heightCm <= 0.0 -> invalid("heightCm", DomainError.Reason.NOT_POSITIVE)
        else -> {
            val meters = heightCm / CM_PER_M
            val bmi = weightKg / (meters * meters)
            if (isFiniteNumber(bmi)) bmi.asSuccess() else invalid("bmi", DomainError.Reason.NOT_FINITE)
        }
    }

    /** Peso da medida em kg, se a unidade for de massa. */
    public fun weightInKg(measurement: Measurement): Result<Double> =
        if (measurement.type != MeasurementType.WEIGHT) {
            invalid("type", DomainError.Reason.OUT_OF_RANGE)
        } else {
            Units.convert(measurement.value, measurement.unit, MeasurementUnit.KG)
        }

    private fun invalid(field: String, reason: DomainError.Reason): Result<Nothing> =
        DomainError.Invalid(field, reason).asFailure()
}
