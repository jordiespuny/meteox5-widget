package net.zoom3.meteox5widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.roundToInt

/**
 * Índice de riesgo de incendio sencillo y transparente (0-100) a partir de
 * temperatura, humedad relativa y viento. No es un índice oficial (los tipo
 * FWI necesitan histórico de precipitación y humedad del combustible); es una
 * heurística: hace más calor, más seco y más viento => más riesgo.
 */
object FireRisk {

    /**
     * @param tempC temperatura en °C
     * @param humidityPct humedad relativa en %
     * @param windKmh viento en km/h
     * @return riesgo de 0 (muy bajo) a 100 (extremo)
     */
    fun score(tempC: Double, humidityPct: Double, windKmh: Double): Int {
        // Cada factor se normaliza a 0..1 en su rango relevante.
        val tempScore = ((tempC - 10.0) / (40.0 - 10.0)).coerceIn(0.0, 1.0)   // 10°C -> 0, 40°C -> 1
        val dryScore = ((60.0 - humidityPct) / (60.0 - 20.0)).coerceIn(0.0, 1.0) // 60% -> 0, 20% -> 1
        val windScore = (windKmh / 40.0).coerceIn(0.0, 1.0)                   // 0 -> 0, 40 km/h -> 1

        // La sequedad y el calor pesan más; el viento amplifica la propagación.
        val risk = 0.35 * tempScore + 0.35 * dryScore + 0.30 * windScore
        return (risk * 100).roundToInt().coerceIn(0, 100)
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
