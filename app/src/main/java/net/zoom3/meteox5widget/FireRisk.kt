package net.zoom3.meteox5widget

import kotlin.math.pow

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

        return Result(index = r, level = levelOf(r))
    }

    private fun levelOf(r: Double): Level = when {
        r < 0.5 -> Level.LOW
        r <= 1.2 -> Level.MODERATE
        r <= 2.5 -> Level.HIGH
        else -> Level.EXTREME
    }

}
