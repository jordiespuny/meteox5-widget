package net.zoom3.meteox5widget.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Lee las lecturas de la XEMA para una estación desde los datasets abiertos
 * de Socrata (analisi.transparenciacatalunya.cat), que no requieren la API
 * key oficial de Meteocat (pendiente de aprobación):
 * - nzvn-apee: lecturas cada 30 min (precipitación del intervalo, temperatura, humedad, viento).
 * - 7bvh-jvq2: agregados diarios (precipitación acumulada del último día ya cerrado).
 */
class SocrataStationWeatherRepository(
    private val stationCode: String = "X5",
    private val stationName: String = "PN dels Ports"
) : StationWeatherRepository {

    override suspend fun getLatestWeather(): StationWeatherData = withContext(Dispatchers.IO) {
        val interval = fetchLatestInterval()
        val daily = fetchLatestDailyAccumulated()

        StationWeatherData(
            stationCode = stationCode,
            stationName = stationName,
            precipitationMm = interval.precipitationMm,
            temperatureC = interval.temperatureC,
            humidityPct = interval.humidityPct,
            windSpeedMs = interval.windSpeedMs,
            windDirectionDeg = interval.windDirectionDeg,
            measuredAtEpochMillis = interval.measuredAtEpochMillis,
            dailyAccumulatedMm = daily?.first,
            dailyAccumulatedDateEpochMillis = daily?.second
        )
    }

    private fun fetchLatestInterval(): IntervalReading {
        val where = "codi_estacio='$stationCode' AND codi_variable in ('30','31','32','33','35')"
        val query = "\$where=${URLEncoder.encode(where, "UTF-8")}" +
            "&\$order=${URLEncoder.encode("data_lectura DESC", "UTF-8")}" +
            "&\$limit=10"
        val records = JSONArray(fetchBody("$INTERVAL_BASE_URL?$query"))

        var precipitationMm: Double? = null
        var temperatureC: Double? = null
        var humidityPct: Double? = null
        var windSpeedMs: Double? = null
        var windDirectionDeg: Double? = null
        var measuredAtEpochMillis = System.currentTimeMillis()

        for (i in 0 until records.length()) {
            val record = records.getJSONObject(i)

            if (i == 0) {
                parseIsoDateTime(record.optString("data_lectura"))?.let { measuredAtEpochMillis = it }
            }

            val value = record.optString("valor_lectura").toDoubleOrNull() ?: continue
            when (record.optString("codi_variable")) {
                "35" -> if (precipitationMm == null) precipitationMm = value
                "32" -> if (temperatureC == null) temperatureC = value
                "33" -> if (humidityPct == null) humidityPct = value
                "30" -> if (windSpeedMs == null) windSpeedMs = value
                "31" -> if (windDirectionDeg == null) windDirectionDeg = value
            }
        }

        return IntervalReading(precipitationMm, temperatureC, humidityPct, windSpeedMs, windDirectionDeg, measuredAtEpochMillis)
    }

    /** Devuelve (mm acumulados, fecha del día al que corresponde) o null si no se pudo leer. */
    private fun fetchLatestDailyAccumulated(): Pair<Double?, Long?>? {
        return try {
            val where = "codi_estacio='$stationCode' AND codi_variable='1300'"
            val query = "\$where=${URLEncoder.encode(where, "UTF-8")}" +
                "&\$order=${URLEncoder.encode("data_lectura DESC", "UTF-8")}" +
                "&\$limit=1"
            val records = JSONArray(fetchBody("$DAILY_BASE_URL?$query"))
            if (records.length() == 0) return null

            val record = records.getJSONObject(0)
            val value = record.optString("valor").toDoubleOrNull()
            val date = parseIsoDateTime(record.optString("data_lectura"))
            value to date
        } catch (e: Exception) {
            // Un fallo en el dataset diario no debe tumbar el resto del widget.
            null
        }
    }

    private fun fetchBody(urlString: String): String {
        val connection = URL(urlString).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        connection.requestMethod = "GET"
        return try {
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun parseIsoDateTime(raw: String): Long? {
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.getDefault())
            format.timeZone = TimeZone.getTimeZone("Europe/Madrid")
            format.parse(raw)?.time
        } catch (e: Exception) {
            null
        }
    }

    private data class IntervalReading(
        val precipitationMm: Double?,
        val temperatureC: Double?,
        val humidityPct: Double?,
        val windSpeedMs: Double?,
        val windDirectionDeg: Double?,
        val measuredAtEpochMillis: Long
    )

    companion object {
        private const val INTERVAL_BASE_URL = "https://analisi.transparenciacatalunya.cat/resource/nzvn-apee.json"
        private const val DAILY_BASE_URL = "https://analisi.transparenciacatalunya.cat/resource/7bvh-jvq2.json"
    }
}
