package net.zoom3.meteox5widget.work

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import net.zoom3.meteox5widget.MeteoX5Widget
import net.zoom3.meteox5widget.data.SocrataStationWeatherRepository
import net.zoom3.meteox5widget.data.StationWeatherRepository
import java.util.concurrent.TimeUnit

class WeatherUpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val repository: StationWeatherRepository = SocrataStationWeatherRepository()

    override suspend fun doWork(): Result {
        val data = try {
            repository.getLatestWeather()
        } catch (e: Exception) {
            // Sin red o servicio caído: lo reintenta WorkManager con backoff, el widget conserva el último valor.
            return Result.retry()
        }

        val appWidgetManager = AppWidgetManager.getInstance(applicationContext)
        val ids = appWidgetManager.getAppWidgetIds(
            ComponentName(applicationContext, MeteoX5Widget::class.java)
        )
        MeteoX5Widget.updateWidgets(applicationContext, appWidgetManager, ids, data)

        return Result.success()
    }

    companion object {
        const val WORK_NAME = "weather_update_x5"

        // La XEMA publica lecturas cada 30 min; alineamos la cadencia del worker con esa frecuencia.
        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<WeatherUpdateWorker>(30, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }
}
