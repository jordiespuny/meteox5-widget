package net.zoom3.meteox5widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.zoom3.meteox5widget.data.MockStationWeatherRepository
import net.zoom3.meteox5widget.data.StationWeatherData
import net.zoom3.meteox5widget.work.WeatherUpdateWorker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class MeteoX5Widget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        WeatherUpdateWorker.schedulePeriodic(context)
        // Pintado inmediato con datos de ejemplo; el worker sustituye por datos reales enseguida.
        CoroutineScope(Dispatchers.IO).launch {
            val data = MockStationWeatherRepository().getLatestWeather()
            withContext(Dispatchers.Main) {
                updateWidgets(context, appWidgetManager, appWidgetIds, data)
            }
        }
    }

    override fun onEnabled(context: Context) {
        WeatherUpdateWorker.schedulePeriodic(context)
    }

    override fun onDisabled(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WeatherUpdateWorker.WORK_NAME)
    }

    companion object {
        private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        private val compassPoints = listOf("N", "NE", "E", "SE", "S", "SO", "O", "NO")

        fun updateWidgets(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: IntArray,
            data: StationWeatherData
        ) {
            for (appWidgetId in appWidgetIds) {
                val views = RemoteViews(context.packageName, R.layout.widget_x5)

                views.setTextViewText(
                    R.id.widget_station_name,
                    context.getString(R.string.station_label, data.stationCode, data.stationName)
                )
                views.setTextViewText(
                    R.id.widget_precipitation_interval,
                    formatPrecipitation(context, data.precipitationMm)
                )
                views.setTextViewText(
                    R.id.widget_precipitation_today,
                    formatPrecipitation(context, data.todayAccumulatedMm)
                )
                views.setTextViewText(R.id.widget_temperature, formatTemperature(context, data.temperatureC))
                views.setTextViewText(R.id.widget_humidity, formatHumidity(context, data.humidityPct))
                views.setTextViewText(R.id.widget_wind, formatWind(context, data.windSpeedMs, data.windDirectionDeg))
                views.setTextViewText(
                    R.id.widget_updated_at,
                    context.getString(R.string.updated_at, timeFormat.format(Date(data.measuredAtEpochMillis)))
                )

                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }

        private fun formatPrecipitation(context: Context, mm: Double?): String =
            if (mm == null) "--" else context.getString(R.string.precipitation_value, mm)

        private fun formatTemperature(context: Context, celsius: Double?): String =
            if (celsius == null) "--" else context.getString(R.string.temperature_value, celsius)

        private fun formatHumidity(context: Context, pct: Double?): String =
            if (pct == null) "--" else context.getString(R.string.humidity_value, pct)

        private fun formatWind(context: Context, speedMs: Double?, directionDeg: Double?): String {
            if (speedMs == null) return "--"
            val kmh = speedMs * 3.6
            val direction = directionDeg?.let { compassPoints[compassIndex(it)] }
            return if (direction != null) {
                context.getString(R.string.wind_value, kmh, direction)
            } else {
                context.getString(R.string.wind_value_no_direction, kmh)
            }
        }

        private fun compassIndex(degrees: Double): Int {
            val normalized = ((degrees % 360) + 360) % 360
            return (normalized / 45.0).roundToInt() % 8
        }
    }
}
