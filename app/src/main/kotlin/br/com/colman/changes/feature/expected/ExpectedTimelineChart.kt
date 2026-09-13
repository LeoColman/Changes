// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.expected

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

private const val ROW_HEIGHT_DP = 28
private const val BAR_VERTICAL_INSET = 0.28f
private const val MARKER_RADIUS_DP = 3
private const val TODAY_LINE_WIDTH_DP = 2

/**
 * Linha do tempo da Seção 7.2 (Canvas próprio da feature): uma faixa horizontal por mudança,
 * cobrindo a janela de início típico em meses desde o início da TH, com um marcador de "hoje" e,
 * quando existe, um marcador da primeira observação da pessoa sobre a própria faixa.
 *
 * Tonalidade neutra única (Seção 7.2): a mesma cor para toda faixa, esteja a pessoa dentro ou fora
 * da janela típica; nada aqui destaca quem passou da janela. O [summary] é o único texto que o
 * TalkBack lê deste gráfico (Seção 9): o Canvas inteiro vira um único nó acessível.
 */
@Composable
fun ExpectedTimelineChart(
    items: List<ExpectedItemUiState>,
    todayMonths: Double,
    summary: String,
    modifier: Modifier = Modifier,
) {
    val barColor = MaterialTheme.colorScheme.secondaryContainer
    val markerColor = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT_DP.dp * items.size)
            .semantics { contentDescription = summary },
    ) {
        if (items.isEmpty()) return@Canvas
        val maxMonths = items.maxOf { it.onsetMonthsMax }.coerceAtLeast(todayMonths)
        val rowHeight = size.height / items.size
        items.forEachIndexed { index, item -> drawRow(index, rowHeight, maxMonths, item, barColor, markerColor) }
        val todayX = xOf(todayMonths, maxMonths)
        drawLine(
            markerColor,
            Offset(todayX, 0f),
            Offset(todayX, size.height),
            strokeWidth = TODAY_LINE_WIDTH_DP.dp.toPx(),
        )
    }
}

private fun DrawScope.xOf(months: Double, maxMonths: Double): Float =
    (months / maxMonths).toFloat().coerceIn(0f, 1f) * size.width

private fun DrawScope.drawRow(
    index: Int,
    rowHeight: Float,
    maxMonths: Double,
    item: ExpectedItemUiState,
    barColor: Color,
    markerColor: Color,
) {
    val top = index * rowHeight + rowHeight * BAR_VERTICAL_INSET
    val bottom = (index + 1) * rowHeight - rowHeight * BAR_VERTICAL_INSET
    val startX = xOf(item.onsetMonthsMin, maxMonths)
    val endX = xOf(item.onsetMonthsMax, maxMonths)
    drawRect(barColor, topLeft = Offset(startX, top), size = Size(endX - startX, bottom - top))
    item.firstObservedMonths?.let { months ->
        val markerCenter = Offset(xOf(months, maxMonths), (top + bottom) / 2)
        drawCircle(markerColor, radius = MARKER_RADIUS_DP.dp.toPx(), center = markerCenter)
    }
}
