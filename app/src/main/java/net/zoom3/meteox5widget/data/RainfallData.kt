package net.zoom3.meteox5widget.data

data class RainfallData(
    val stationCode: String,
    val stationName: String,
    val precipitationMm: Double,
    val measuredAtEpochMillis: Long
)
