package net.zoom3.meteox5widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.work.WorkManager
import net.zoom3.meteox5widget.data.StationWeatherData
import net.zoom3.meteox5widget.data.WeatherCache
import net.zoom3.meteox5widget.work.WeatherUpdateWorker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class MeteoX5Widget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        // Repintamos desde la última lectura cacheada (con el botón de recargar ya
        // funcional). Si no hay caché, el widget se queda con el placeholder del
        // layout; nunca pintamos datos inventados. El worker traerá lo más reciente.
        val cached = WeatherCache(context).load()
        if (cached != null) {
            updateWidgets(context, appWidgetManager, appWidgetIds, cached)
        }
        WeatherUpdateWorker.schedulePeriodic(context)
        WeatherUpdateWorker.requestImmediateUpdate(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            showRefreshing(context)
            WeatherUpdateWorker.requestImmediateUpdate(context)
        }
    }

    override fun onEnabled(context: Context) {
        WeatherUpdateWorker.schedulePeriodic(context)
    }

    override fun onDisabled(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WeatherUpdateWorker.WORK_NAME)
    }

    companion object {
        private const val ACTION_REFRESH = "net.zoom3.meteox5widget.ACTION_REFRESH"
        // Margen holgado sobre los 30 min de publicación de la XEMA + retraso normal de la fuente.
        private const val STALE_THRESHOLD_MS = 90 * 60 * 1000L
        private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        private val compassPoints = listOf("N", "NE", "E", "SE", "S", "SO", "O", "NO")

        private fun refreshPendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, MeteoX5Widget::class.java).apply {
                action = ACTION_REFRESH
                component = ComponentName(context, MeteoX5Widget::class.java)
            }
            return PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        /** Feedback inmediato al tocar el botón: cambia solo la línea de "Actualizado". */
        private fun showRefreshing(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val ids = appWidgetManager.getAppWidgetIds(
                ComponentName(context, MeteoX5Widget::class.java)
            )
            for (appWidgetId in ids) {
                val views = RemoteViews(context.packageName, R.layout.widget_x5)
                views.setTextViewText(R.id.widget_updated_at, context.getString(R.string.updating))
                views.setOnClickPendingIntent(R.id.widget_refresh, refreshPendingIntent(context))
                appWidgetManager.partiallyUpdateAppWidget(appWidgetId, views)
            }
        }

        fun updateWidgets(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: IntArray,
            data: StationWeatherData
        ) {
            // La XEMA publica cada 30 min; más de STALE_THRESHOLD sin dato nuevo suele
            // significar que la estación ha caído o que la fuente va muy rezagada.
            val isStale = System.currentTimeMillis() - data.measuredAtEpochMillis > STALE_THRESHOLD_MS
            val time = timeFormat.format(Date(data.measuredAtEpochMillis))
            val valueColor = context.getColor(
                if (isStale) R.color.widget_text_muted else R.color.widget_text_primary
            )

            for (appWidgetId in appWidgetIds) {
                val views = RemoteViews(context.packageName, R.layout.widget_x5)

                views.setOnClickPendingIntent(R.id.widget_refresh, refreshPendingIntent(context))

                val stationLabel = if (data.isBackup) R.string.station_label_backup else R.string.station_label
                views.setTextViewText(
                    R.id.widget_station_name,
                    context.getString(stationLabel, data.stationCode, data.stationName)
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

                // Cuando el dato es viejo, atenuamos los valores y avisamos en la línea de estado.
                views.setTextColor(R.id.widget_precipitation_interval, valueColor)
                views.setTextColor(R.id.widget_precipitation_today, valueColor)
                views.setTextColor(R.id.widget_temperature, valueColor)
                views.setTextColor(R.id.widget_humidity, valueColor)
                views.setTextColor(R.id.widget_wind, valueColor)

                when {
                    // Mostrando el respaldo: avisamos de que X5 no tiene datos (aunque el respaldo sí).
                    data.isBackup -> {
                        views.setTextViewText(
                            R.id.widget_updated_at,
                            context.getString(R.string.backup_status, time)
                        )
                        views.setTextColor(R.id.widget_updated_at, context.getColor(R.color.widget_stale))
                    }
                    isStale -> {
                        views.setTextViewText(
                            R.id.widget_updated_at,
                            context.getString(R.string.updated_at_stale, time)
                        )
                        views.setTextColor(R.id.widget_updated_at, context.getColor(R.color.widget_stale))
                    }
                    else -> {
                        views.setTextViewText(
                            R.id.widget_updated_at,
                            context.getString(R.string.updated_at, time)
                        )
                        views.setTextColor(R.id.widget_updated_at, context.getColor(R.color.widget_text_secondary))
                    }
                }

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
