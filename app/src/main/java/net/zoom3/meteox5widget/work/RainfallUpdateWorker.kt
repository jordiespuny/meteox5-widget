package net.zoom3.meteox5widget.work

import android.content.ComponentName
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import android.appwidget.AppWidgetManager
import net.zoom3.meteox5widget.MeteoX5Widget
import net.zoom3.meteox5widget.data.MockRainfallRepository
import net.zoom3.meteox5widget.data.RainfallRepository
import java.util.concurrent.TimeUnit

class RainfallUpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val repository: RainfallRepository = MockRainfallRepository()

    override suspend fun doWork(): Result {
        val data = repository.getLatestRainfall()

        val appWidgetManager = AppWidgetManager.getInstance(applicationContext)
        val ids = appWidgetManager.getAppWidgetIds(
            ComponentName(applicationContext, MeteoX5Widget::class.java)
        )
        MeteoX5Widget.updateWidgets(applicationContext, appWidgetManager, ids, data)

        return Result.success()
    }

    companion object {
        const val WORK_NAME = "rainfall_update_x5"

        // La XEMA publica lecturas cada 30 min; alineamos la cadencia del worker con esa frecuencia.
        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<RainfallUpdateWorker>(30, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }
}
