package net.zoom3.meteox5widget.data

import android.content.Context

/**
 * Guarda la última lectura pintada para poder repintar el widget (con datos
 * reales, no placeholder) cuando el sistema vuelve a llamar a onUpdate()
 * —al desbloquear, reiniciar o reinstalar— sin esperar a que el worker
 * termine su siguiente petición de red.
 */
class WeatherCache(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun save(data: StationWeatherData) {
        prefs.edit()
            .putString(KEY_STATION_CODE, data.stationCode)
            .putString(KEY_STATION_NAME, data.stationName)
            .putFloat(KEY_PRECIPITATION, data.precipitationMm.toFloatOrNaN())
            .putFloat(KEY_TEMPERATURE, data.temperatureC.toFloatOrNaN())
            .putFloat(KEY_HUMIDITY, data.humidityPct.toFloatOrNaN())
            .putFloat(KEY_WIND_SPEED, data.windSpeedMs.toFloatOrNaN())
            .putFloat(KEY_WIND_DIRECTION, data.windDirectionDeg.toFloatOrNaN())
            .putLong(KEY_MEASURED_AT, data.measuredAtEpochMillis)
            .putFloat(KEY_TODAY_ACCUMULATED, data.todayAccumulatedMm.toFloatOrNaN())
            .putFloat(KEY_RAIN_24H, data.rain24hMm.toFloatOrNaN())
            .putFloat(KEY_RAIN_7D, data.rain7dMm.toFloatOrNaN())
            .putFloat(KEY_RAIN_30D, data.rain30dMm.toFloatOrNaN())
            .putBoolean(KEY_IS_BACKUP, data.isBackup)
            .apply()
    }

    fun load(): StationWeatherData? {
        if (!prefs.contains(KEY_MEASURED_AT)) return null
        return StationWeatherData(
            stationCode = prefs.getString(KEY_STATION_CODE, "X5") ?: "X5",
            stationName = prefs.getString(KEY_STATION_NAME, "") ?: "",
            precipitationMm = prefs.getFloat(KEY_PRECIPITATION, Float.NaN).toDoubleOrNull(),
            temperatureC = prefs.getFloat(KEY_TEMPERATURE, Float.NaN).toDoubleOrNull(),
            humidityPct = prefs.getFloat(KEY_HUMIDITY, Float.NaN).toDoubleOrNull(),
            windSpeedMs = prefs.getFloat(KEY_WIND_SPEED, Float.NaN).toDoubleOrNull(),
            windDirectionDeg = prefs.getFloat(KEY_WIND_DIRECTION, Float.NaN).toDoubleOrNull(),
            measuredAtEpochMillis = prefs.getLong(KEY_MEASURED_AT, System.currentTimeMillis()),
            todayAccumulatedMm = prefs.getFloat(KEY_TODAY_ACCUMULATED, Float.NaN).toDoubleOrNull(),
            rain24hMm = prefs.getFloat(KEY_RAIN_24H, Float.NaN).toDoubleOrNull(),
            rain7dMm = prefs.getFloat(KEY_RAIN_7D, Float.NaN).toDoubleOrNull(),
            rain30dMm = prefs.getFloat(KEY_RAIN_30D, Float.NaN).toDoubleOrNull(),
            isBackup = prefs.getBoolean(KEY_IS_BACKUP, false)
        )
    }

    private fun Double?.toFloatOrNaN(): Float = this?.toFloat() ?: Float.NaN

    private fun Float.toDoubleOrNull(): Double? = if (isNaN()) null else toDouble()

    companion object {
        private const val PREFS_NAME = "meteox5_widget_cache"
        private const val KEY_STATION_CODE = "station_code"
        private const val KEY_STATION_NAME = "station_name"
        private const val KEY_PRECIPITATION = "precipitation"
        private const val KEY_TEMPERATURE = "temperature"
        private const val KEY_HUMIDITY = "humidity"
        private const val KEY_WIND_SPEED = "wind_speed"
        private const val KEY_WIND_DIRECTION = "wind_direction"
        private const val KEY_MEASURED_AT = "measured_at"
        private const val KEY_TODAY_ACCUMULATED = "today_accumulated"
        private const val KEY_RAIN_24H = "rain_24h"
        private const val KEY_RAIN_7D = "rain_7d"
        private const val KEY_RAIN_30D = "rain_30d"
        private const val KEY_IS_BACKUP = "is_backup"
    }
}
