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
 *
 * Si la estación principal (X5 · PN dels Ports) no tiene datos frescos —p. ej.
 * porque ha caído— se recurre a una estación de respaldo cercana
 * (C9 · Mas de Barberans, a ~11 km), marcando el resultado como respaldo.
 */
class SocrataStationWeatherRepository(
    private val primary: Station = Station("X5", "PN dels Ports"),
    private val backup: Station = Station("C9", "Mas de Barberans")
) : StationWeatherRepository {

    data class Station(val code: String, val name: String)

    override suspend fun getLatestWeather(): StationWeatherData = withContext(Dispatchers.IO) {
        val primaryData = runCatching { fetchStation(primary, isBackup = false) }.getOrNull()

        if (primaryData != null && isFresh(primaryData.measuredAtEpochMillis)) {
            return@withContext primaryData
        }

        // X5 caída o sin datos: intentamos el respaldo.
        val backupData = runCatching { fetchStation(backup, isBackup = true) }.getOrNull()

        // Preferimos el respaldo si lo tenemos; si no, lo que haya de X5 (aunque
        // sea viejo); si no hay nada, propagamos el fallo para que el worker reintente.
        backupData ?: primaryData ?: error("Sin datos de ${primary.code} ni del respaldo ${backup.code}")
    }

    private fun isFresh(measuredAtEpochMillis: Long): Boolean =
        System.currentTimeMillis() - measuredAtEpochMillis <= STALE_THRESHOLD_MS

    private fun fetchStation(station: Station, isBackup: Boolean): StationWeatherData {
        val interval = fetchLatestInterval(station.code)
        val todayAccumulatedMm = fetchTodayAccumulated(station.code)

        return StationWeatherData(
            stationCode = station.code,
            stationName = station.name,
            precipitationMm = interval.precipitationMm,
            temperatureC = interval.temperatureC,
            humidityPct = interval.humidityPct,
            windSpeedMs = interval.windSpeedMs,
            windDirectionDeg = interval.windDirectionDeg,
            measuredAtEpochMillis = interval.measuredAtEpochMillis,
            todayAccumulatedMm = todayAccumulatedMm,
            isBackup = isBackup
        )
    }

    private fun fetchLatestInterval(stationCode: String): IntervalReading {
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
        // 0 = "sin lectura": así isFresh() lo trata como no fresco y se activa el respaldo.
        var measuredAtEpochMillis = 0L

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
    private fun fetchTodayAccumulated(stationCode: String): Double? {
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
        // Más de 90 min sin dato fresco en X5 (la XEMA publica cada 30 min) => usamos el respaldo.
        // Coincide con el umbral de "dato viejo" que pinta el widget.
        private const val STALE_THRESHOLD_MS = 90 * 60 * 1000L
    }
}
