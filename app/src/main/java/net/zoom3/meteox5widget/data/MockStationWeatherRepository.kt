package net.zoom3.meteox5widget.data

import java.util.concurrent.TimeUnit

/** Datos de ejemplo, útiles para desarrollar sin depender de la red. */
class MockStationWeatherRepository(
    private val stationCode: String = "X5",
    private val stationName: String = "PN dels Ports"
) : StationWeatherRepository {

    override suspend fun getLatestWeather(): StationWeatherData {
        return StationWeatherData(
            stationCode = stationCode,
            stationName = stationName,
            precipitationMm = (0..80).random() / 10.0,
            temperatureC = (150..300).random() / 10.0,
            humidityPct = (30..90).random().toDouble(),
            windSpeedMs = (0..120).random() / 10.0,
            windDirectionDeg = (0..359).random().toDouble(),
            measuredAtEpochMillis = System.currentTimeMillis(),
            dailyAccumulatedMm = (0..150).random() / 10.0,
            dailyAccumulatedDateEpochMillis = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(2)
        )
    }
}
