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
import net.zoom3.meteox5widget.data.MockRainfallRepository
import net.zoom3.meteox5widget.data.RainfallData
import net.zoom3.meteox5widget.work.RainfallUpdateWorker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MeteoX5Widget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        RainfallUpdateWorker.schedulePeriodic(context)
        CoroutineScope(Dispatchers.IO).launch {
            val data = MockRainfallRepository().getLatestRainfall()
            withContext(Dispatchers.Main) {
                updateWidgets(context, appWidgetManager, appWidgetIds, data)
            }
        }
    }

    override fun onEnabled(context: Context) {
        RainfallUpdateWorker.schedulePeriodic(context)
    }

    override fun onDisabled(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(RainfallUpdateWorker.WORK_NAME)
    }

    companion object {
        private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

        fun updateWidgets(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: IntArray,
            data: RainfallData
        ) {
            for (appWidgetId in appWidgetIds) {
                val views = RemoteViews(context.packageName, R.layout.widget_x5)
                views.setTextViewText(R.id.widget_station_name, data.stationName)
                views.setTextViewText(
                    R.id.widget_precipitation,
                    context.getString(R.string.precipitation_value, data.precipitationMm)
                )
                views.setTextViewText(
                    R.id.widget_updated_at,
                    context.getString(R.string.updated_at, timeFormat.format(Date(data.measuredAtEpochMillis)))
                )
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }
    }
}
