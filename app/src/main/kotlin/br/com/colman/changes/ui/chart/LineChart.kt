// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.ui.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Um ponto do gráfico: [x] em qualquer unidade crescente (ex.: epoch day), [y] o valor. */
@Immutable
data class ChartPoint(val x: Double, val y: Double)

/** Faixa horizontal desenhada atrás da linha (ex.: faixa de referência do laudo). Limite `null` = aberto. */
@Immutable
data class ChartBand(val low: Double?, val high: Double?)

@Immutable
data class ChartSeries(val points: List<ChartPoint>, val color: Color? = null)

/**
 * Gráfico de linha simples, desenhado em Canvas, sem biblioteca externa. Cores neutras: nada de
 * vermelho de alarme (Seções 7.4 e 7.6). [summary] é obrigatório e é o que o TalkBack lê: o gráfico
 * inteiro vira um único nó acessível com o resumo textual (Seção 9).
 */
@Composable
fun LineChart(
    series: List<ChartSeries>,
    summary: String,
    modifier: Modifier = Modifier,
    band: ChartBand? = null,
    height: Dp = 180.dp,
) {
    val colors = ChartColors(
        line = MaterialTheme.colorScheme.primary,
        band = MaterialTheme.colorScheme.secondaryContainer,
        axis = MaterialTheme.colorScheme.outlineVariant,
    )
    val all = series.flatMap { it.points }
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .semantics { contentDescription = summary },
    ) {
        if (all.isNotEmpty()) {
            val bounds = ChartBounds.of(all, band)
            band?.let { drawBand(it, bounds, colors.band) }
            drawLine(colors.axis, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
            series.forEach { drawSeries(it, bounds, it.color ?: colors.line) }
        }
    }
}

private data class ChartColors(val line: Color, val band: Color, val axis: Color)

private fun DrawScope.xOf(bounds: ChartBounds, x: Double): Float = bounds.xFraction(x) * size.width

private fun DrawScope.yOf(bounds: ChartBounds, y: Double): Float = size.height - bounds.yFraction(y) * size.height

private fun DrawScope.drawBand(band: ChartBand, bounds: ChartBounds, color: Color) {
    val top = band.high?.let { yOf(bounds, it) } ?: 0f
    val bottom = band.low?.let { yOf(bounds, it) } ?: size.height
    drawRect(color, topLeft = Offset(0f, top), size = Size(size.width, bottom - top))
}

private fun DrawScope.drawSeries(series: ChartSeries, bounds: ChartBounds, color: Color) {
    val sorted = series.points.sortedBy { it.x }
    val offsets = sorted.map { Offset(xOf(bounds, it.x), yOf(bounds, it.y)) }
    if (offsets.size > 1) {
        val path = Path().apply {
            moveTo(offsets.first().x, offsets.first().y)
            offsets.drop(1).forEach { lineTo(it.x, it.y) }
        }
        drawPath(path, color, style = Stroke(width = 2.dp.toPx()))
    }
    offsets.forEach { drawCircle(color, radius = 3.dp.toPx(), center = it) }
}

/** Limites dos eixos, com margem de 5% e tolerância a série de um único valor. */
internal data class ChartBounds(val minX: Double, val maxX: Double, val minY: Double, val maxY: Double) {
    fun xFraction(x: Double): Float = if (maxX == minX) HALF else ((x - minX) / (maxX - minX)).toFloat()

    fun yFraction(y: Double): Float = if (maxY == minY) HALF else ((y - minY) / (maxY - minY)).toFloat()

    companion object {
        private const val HALF = 0.5f
        private const val MARGIN = 0.05

        fun of(points: List<ChartPoint>, band: ChartBand?): ChartBounds {
            val ys = points.map { it.y } + listOfNotNull(band?.low, band?.high)
            val low = ys.min()
            val high = ys.max()
            val pad = (high - low) * MARGIN
            return ChartBounds(points.minOf { it.x }, points.maxOf { it.x }, low - pad, high + pad)
        }
    }
}
