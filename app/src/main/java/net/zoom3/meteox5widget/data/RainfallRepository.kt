package net.zoom3.meteox5widget.data

interface RainfallRepository {
    suspend fun getLatestRainfall(): RainfallData
}
