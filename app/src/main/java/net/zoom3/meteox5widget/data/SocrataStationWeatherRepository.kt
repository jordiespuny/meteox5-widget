package net.zoom3.meteox5widget.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Lee las lecturas de la XEMA para una estación desde el dataset abierto de
 * Socrata (analisi.transparenciacatalunya.cat/resource/nzvn-apee), lecturas
 * cada 30 min: precipitación del intervalo, temperatura, humedad y viento.
 * No requiere la API key oficial de Meteocat (pendiente de aprobación).
 *
 * El acumulado de "hoy" se calcula sumando esas mismas lecturas de intervalo
 * desde la medianoche local, en vez de usar el producto diario certificado
 * de Meteocat (que se publica con 1-2 días de retraso).
 */
class SocrataStationWeatherRepository(
    private val stationCode: String = "X5",
    private val stationName: String = "PN dels Ports"
) : StationWeatherRepository {

    override suspend fun getLatestWeather(): StationWeatherData = withContext(Dispatchers.IO) {
        val interval = fetchLatestInterval()
        val todayAccumulatedMm = fetchTodayAccumulated()

        StationWeatherData(
            stationCode = stationCode,
            stationName = stationName,
            precipitationMm = interval.precipitationMm,
            temperatureC = interval.temperatureC,
            humidityPct = interval.humidityPct,
            windSpeedMs = interval.windSpeedMs,
            windDirectionDeg = interval.windDirectionDeg,
            measuredAtEpochMillis = interval.measuredAtEpochMillis,
            todayAccumulatedMm = todayAccumulatedMm
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

    /** Suma la precipitación (codi_variable 35) de todos los intervalos desde la medianoche local. */
    private fun fetchTodayAccumulated(): Double? {
        return try {
            val midnightUtcIso = formatUtcIso(startOfTodayEpochMillis())
            val where = "codi_estacio='$stationCode' AND codi_variable='35' AND data_lectura >= '$midnightUtcIso'"
            val query = "\$where=${URLEncoder.encode(where, "UTF-8")}" +
                "&\$order=${URLEncoder.encode("data_lectura ASC", "UTF-8")}" +
                "&\$limit=100"
            val records = JSONArray(fetchBody("$INTERVAL_BASE_URL?$query"))

            var sum = 0.0
            for (i in 0 until records.length()) {
                sum += records.getJSONObject(i).optString("valor_lectura").toDoubleOrNull() ?: 0.0
            }
            sum
        } catch (e: Exception) {
            // Un fallo en este cálculo no debe tumbar el resto del widget.
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

    private fun startOfTodayEpochMillis(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    private fun formatUtcIso(epochMillis: Long): String {
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.getDefault())
        format.timeZone = TimeZone.getTimeZone("UTC")
        return format.format(Date(epochMillis))
    }

    /** Socrata devuelve data_lectura en UTC, sin offset explícito en el string. */
    private fun parseIsoDateTime(raw: String): Long? {
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.getDefault())
            format.timeZone = TimeZone.getTimeZone("UTC")
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
    }
}
