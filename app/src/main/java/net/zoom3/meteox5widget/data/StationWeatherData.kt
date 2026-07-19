package net.zoom3.meteox5widget.data

data class StationWeatherData(
    val stationCode: String,
    val stationName: String,
    val precipitationMm: Double?,
    val temperatureC: Double?,
    val humidityPct: Double?,
    val windSpeedMs: Double?,
    val windDirectionDeg: Double?,
    val measuredAtEpochMillis: Long,
    /** Suma de las precipitaciones de intervalo desde la medianoche local; null si no se pudo calcular. */
    val todayAccumulatedMm: Double?
)
