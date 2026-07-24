package net.zoom3.meteox5widget.data

import android.content.Context

/** Recuerda la última precipitación del intervalo para detectar cuándo empieza a llover (0 -> >0). */
class RainAlertState(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun lastPrecipitationMm(): Double = prefs.getFloat(KEY_LAST_PRECIPITATION, 0f).toDouble()

    fun lastStationCode(): String? = prefs.getString(KEY_LAST_STATION, null)

    fun save(stationCode: String, precipitationMm: Double) {
        prefs.edit()
            .putString(KEY_LAST_STATION, stationCode)
            .putFloat(KEY_LAST_PRECIPITATION, precipitationMm.toFloat())
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "meteox5_widget_state"
        private const val KEY_LAST_PRECIPITATION = "last_precipitation_mm"
        private const val KEY_LAST_STATION = "last_station_code"
    }
}
