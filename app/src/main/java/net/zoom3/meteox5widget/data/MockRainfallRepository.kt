package net.zoom3.meteox5widget.data

/**
 * Devuelve datos de ejemplo. Sustituir por una implementación real de
 * [RainfallRepository] contra la API de Meteocat (XEMA) cuando esté
 * disponible la API key y el endpoint/variable de precipitación estén
 * confirmados.
 */
class MockRainfallRepository : RainfallRepository {

    override suspend fun getLatestRainfall(): RainfallData {
        return RainfallData(
            stationCode = "X5",
            stationName = "Estació X5",
            precipitationMm = (0..80).random() / 10.0,
            measuredAtEpochMillis = System.currentTimeMillis()
        )
    }
}
