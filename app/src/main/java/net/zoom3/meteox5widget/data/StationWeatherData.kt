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
    /** Precipitación acumulada del último día ya cerrado (suele ir 1-2 días por detrás). */
    val dailyAccumulatedMm: Double?,
    val dailyAccumulatedDateEpochMillis: Long?
)
