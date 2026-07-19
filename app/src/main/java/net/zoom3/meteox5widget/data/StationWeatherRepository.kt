package net.zoom3.meteox5widget.data

interface StationWeatherRepository {
    suspend fun getLatestWeather(): StationWeatherData
}
