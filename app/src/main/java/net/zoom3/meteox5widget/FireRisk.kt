package net.zoom3.meteox5widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Índice de peligro meteorológico de incendio para pinar mediterráneo,
 * según el índice de Angström modificado con corrección por lluvia:
 *
 *   I0 = HR/20 + (27 - T)/10      (inflamabilidad atmosférica; menor = más seco)
 *   W  = 1 + (V/20)^1.5           (el viento acelera secado y propagación)
 *   B  = factor de atenuación por lluvia acumulada reciente
 *   R  = W / (I0 * B)
 *
 * Es un índice de **peligro meteorológico**, no una probabilidad de incendio:
 * indica cómo de favorables son las condiciones si aparece una ignición.
 *
 * Se descartó el FWI canadiense (más sólido) porque necesita arrastrar los
 * códigos FFMC/DMC/DC del día anterior, lecturas al mediodía solar y semanas
 * de spin-up antes de ser representativo — inviable en un widget.
 */
object FireRisk {

    enum class Level { LOW, MODERATE, HIGH, EXTREME }

    data class Result(
        /** Índice R crudo del modelo. */
        val index: Double,
        /** Mismo riesgo mapeado a 0-100 para la barra circular. */
        val score: Int,
        val level: Level
    )

    /**
     * @param tempC temperatura en °C
     * @param humidityPct humedad relativa en %
     * @param windKmh viento en km/h
     * @param rainMm precipitación acumulada del episodio reciente (últimas 24 h) en mm
     */
    fun evaluate(tempC: Double, humidityPct: Double, windKmh: Double, rainMm: Double): Result {
        // Evitamos dividir por cero o por valores insignificantes cuando hace mucho calor y está muy seco.
        val i0 = ((humidityPct / 20.0) + ((27.0 - tempC) / 10.0)).coerceAtLeast(0.1)
        val w = 1.0 + (windKmh.coerceAtLeast(0.0) / 20.0).pow(1.5)
        val b = when {
            rainMm < 2.0 -> 1.0   // se evapora rápido, sin efecto real
            rainMm < 10.0 -> 1.3  // humectación ligera de la superficie
            rainMm < 20.0 -> 1.8  // mantillo fino saturado
            else -> 2.5           // saturación completa
        }
        val r = w / (i0 * b)

        return Result(index = r, score = toScore(r), level = levelOf(r))
    }

    private fun levelOf(r: Double): Level = when {
        r < 0.5 -> Level.LOW
        r <= 1.2 -> Level.MODERATE
        r <= 2.5 -> Level.HIGH
        else -> Level.EXTREME
    }

    /**
     * Reparte el arco en cuatro cuartos, uno por nivel, para que la posición de
     * la barra coincida siempre con la categoría (R no es lineal ni acotado).
     */
    private fun toScore(r: Double): Int {
        val score = when {
            r < 0.5 -> r / 0.5 * 25.0
            r <= 1.2 -> 25.0 + (r - 0.5) / (1.2 - 0.5) * 25.0
            r <= 2.5 -> 50.0 + (r - 1.2) / (2.5 - 1.2) * 25.0
            else -> 75.0 + ((r - 2.5) / (4.0 - 2.5)).coerceAtMost(1.0) * 25.0
        }
        return score.roundToInt().coerceIn(0, 100)
    }

    /** Dibuja el arco circular (verde -> rojo según el riesgo) con el valor 0-100 en el centro. */
    fun renderGauge(context: Context, risk: Int): Bitmap {
        val density = context.resources.displayMetrics.density
        val size = (GAUGE_SIZE_DP * density).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val stroke = size * 0.13f
        val pad = stroke / 2f + size * 0.04f
        val rect = RectF(pad, pad, size - pad, size - pad)

        val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = stroke
            strokeCap = Paint.Cap.ROUND
            color = 0x33FFFFFF
        }
        canvas.drawArc(rect, 0f, 360f, false, backgroundPaint)

        val hue = 120f * (1f - risk / 100f) // 120 = verde, 0 = rojo
        val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = stroke
            strokeCap = Paint.Cap.ROUND
            color = Color.HSVToColor(floatArrayOf(hue, 0.85f, 0.95f))
        }
        canvas.drawArc(rect, -90f, 360f * risk / 100f, false, arcPaint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            textSize = size * 0.36f
            color = Color.WHITE
            typeface = Typeface.DEFAULT_BOLD
        }
        val centerX = size / 2f
        val centerY = size / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(risk.toString(), centerX, centerY, textPaint)

        return bitmap
    }

    private const val GAUGE_SIZE_DP = 34
}
