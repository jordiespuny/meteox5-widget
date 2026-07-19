package net.zoom3.meteox5widget.work

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import net.zoom3.meteox5widget.MeteoX5Widget
import net.zoom3.meteox5widget.data.RainAlertState
import net.zoom3.meteox5widget.data.SocrataStationWeatherRepository
import net.zoom3.meteox5widget.data.StationWeatherRepository
import net.zoom3.meteox5widget.notification.RainAlertNotifier
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

        data.precipitationMm?.let { current ->
            val alertState = RainAlertState(applicationContext)
            val previous = alertState.lastPrecipitationMm()
            if (previous <= 0.0 && current > 0.0) {
                RainAlertNotifier.notifyRainStarted(applicationContext, current)
            }
            alertState.save(current)
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
        // KEEP: si ya hay un ciclo periódico en marcha, no lo reiniciamos cada vez que el sistema
        // vuelve a llamar a onUpdate() (p. ej. al desbloquear el móvil en algunos launchers).
        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<WeatherUpdateWorker>(30, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        /** Pide un dato real cuanto antes (p. ej. al añadir el widget), sin esperar al ciclo de 30 min. */
        fun requestImmediateUpdate(context: Context) {
            val request = OneTimeWorkRequestBuilder<WeatherUpdateWorker>().build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
