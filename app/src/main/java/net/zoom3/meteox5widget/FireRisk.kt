package net.zoom3.meteox5widget

import kotlin.math.pow

/**
 * Índice de peligro meteorológico de incendio para pinar mediterráneo,
 * según el índice de Angström modificado con corrección por lluvia:
 *
 *   I0 = HR/20 + (27 - T)/10      (inflamabilidad atmosférica; menor = más seco)
 *   W  = 1 + (V/20)^1.5           (el viento acelera secado y propagación)
 *   B  = atenuación por la lluvia de las últimas 24 h
 *   R  = W / (I0 * B)
 *
 * Sobre ese instantáneo se aplican dos correcciones, R_aj = R * D * S:
 *
 *   D = sequedad de fondo, medida con la lluvia de 7 y 30 días
 *   S = factor estacional del combustible vivo (fenología, terpenos)
 *
 * Es un índice de **peligro meteorológico**, no una probabilidad de incendio:
 * indica cómo de favorables son las condiciones si aparece una ignición.
 *
 * Se descartó el FWI canadiense (más sólido) porque necesita arrastrar los
 * códigos FFMC/DMC/DC del día anterior, lecturas al mediodía solar y semanas
 * de spin-up antes de ser representativo — inviable en un widget.
 */
object FireRisk {

    enum class Level { LOW, MODERATE, HIGH, VERY_HIGH, EXTREME }

    data class Result(
        /** Índice R ya corregido por sequía y estación. */
        val index: Double,
        val level: Level
    )

    /**
     * @param tempC temperatura en °C
     * @param humidityPct humedad relativa en %
     * @param windKmh viento en km/h
     * @param rain24hMm precipitación de las últimas 24 h en mm
     * @param rain7dMm lluvia de los últimos 7 días; null si no se pudo obtener
     * @param rain30dMm lluvia de los últimos 30 días; null si no se pudo obtener
     * @param month mes del año (1-12) para el factor estacional
     */
    fun evaluate(
        tempC: Double,
        humidityPct: Double,
        windKmh: Double,
        rain24hMm: Double,
        rain7dMm: Double?,
        rain30dMm: Double?,
        month: Int
    ): Result {
        // Evitamos dividir por cero o por valores insignificantes cuando hace mucho calor y está muy seco.
        val i0 = ((humidityPct / 20.0) + ((27.0 - tempC) / 10.0)).coerceAtLeast(0.1)
        val w = 1.0 + (windKmh.coerceAtLeast(0.0) / 20.0).pow(1.5)
        val b = when {
            rain24hMm < 2.0 -> 1.0   // se evapora rápido, sin efecto real
            rain24hMm < 10.0 -> 1.3  // humectación ligera de la superficie
            rain24hMm < 20.0 -> 1.8  // mantillo fino saturado
            else -> 2.5              // saturación completa
        }
        val r = w / (i0 * b) * droughtFactor(rain7dMm, rain30dMm) * seasonFactor(month)

        return Result(index = r, level = levelOf(r))
    }

    /**
     * Sequedad de fondo del combustible. El instantáneo (T, HR, V) no distingue
     * entre "hoy no ha llovido" y "no llueve desde hace un mes", y en pinar
     * mediterráneo eso cambia mucho las cosas. Sin datos se queda neutro (1.0).
     */
    private fun droughtFactor(rain7dMm: Double?, rain30dMm: Double?): Double {
        if (rain30dMm == null) return 1.0

        val background = when {
            rain30dMm < 5.0 -> 1.35   // sequía marcada
            rain30dMm < 20.0 -> 1.20  // mes seco
            rain30dMm < 50.0 -> 1.00  // normal
            else -> 0.85              // mes lluvioso
        }
        // Alivio adicional si la lluvia es además reciente (combustible intermedio aún húmedo).
        val recent = when {
            rain7dMm == null -> 1.0
            rain7dMm >= 20.0 -> 0.85
            rain7dMm >= 5.0 -> 0.95
            else -> 1.0
        }
        // La lluvia de los últimos 7 días ya está contada dentro de la de 30, así que
        // acotamos el resultado para no aplicar el mismo alivio dos veces.
        return (background * recent).coerceIn(0.85, 1.35)
    }

    /**
     * Estado estacional del combustible **vivo**: en verano el sotobosque entra
     * en letargo y los pinos emiten más terpenos, lo que no se deduce de la
     * lluvia. Va deliberadamente suave (0.85-1.15) y no con los 1.4-1.5 que se
     * usan cuando el factor estacional es el único sustituto de la sequía:
     * aquí la sequedad ya se mide aparte y duplicarla inflaría el índice.
     */
    private fun seasonFactor(month: Int): Double = when (month) {
        12, 1, 2 -> 0.85   // invierno
        3, 4, 5 -> 0.95    // primavera, vegetación en crecimiento
        6 -> 1.10          // inicio del secado del sotobosque
        7, 8 -> 1.15       // estrés hídrico máximo
        9 -> 1.05          // aún cálido y seco
        else -> 0.90       // octubre-noviembre
    }

    /**
     * Cinco niveles, para poder comparar directamente con el mapa municipal de
     * perill d'incendi forestal del DARP (baix / moderat / alt / molt alt /
     * extrem) sin traducción mental.
     *
     * Los cortes altos (1.2 y 2.5) son los del modelo Angström original. Los
     * dos bajos (0.35 y 0.60) son un **ajuste propio**, no un valor oficial:
     * están calibrados contra una observación puntual — 31/07/2026 en el PN
     * dels Ports, 27.3 °C / 64 % / 11 km/h, R≈0.65, con el mapa oficial en
     * "alt" ese día. Un único punto de contraste, así que conviene revisarlos
     * cuando se acumulen más comparaciones.
     */
    private fun levelOf(r: Double): Level = when {
        r < 0.35 -> Level.LOW
        r < 0.60 -> Level.MODERATE
        r <= 1.20 -> Level.HIGH
        r <= 2.50 -> Level.VERY_HIGH
        else -> Level.EXTREME
    }

}
