package net.zoom3.meteox5widget.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import net.zoom3.meteox5widget.R

/** Aviso puntual (con sonido) cuando la precipitación del intervalo pasa de 0 a más de 0. */
object RainAlertNotifier {

    // v2: el sonido de un canal ya creado no se puede cambiar en Android, así que al
    // sustituir rain_start por el mp3 real hace falta un ID de canal nuevo.
    private const val CHANNEL_ID = "rain_start_alerts_v2"
    private val OLD_CHANNEL_IDS = listOf("rain_start_alerts")
    private const val NOTIFICATION_ID = 1001

    fun notifyRainStarted(context: Context, precipitationMm: Double) {
        ensureChannel(context)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(context.getString(R.string.rain_alert_title))
            .setContentText(context.getString(R.string.rain_alert_text, precipitationMm))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // El usuario no ha concedido el permiso de notificaciones (Android 13+); no hacemos nada más.
        }
    }

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        OLD_CHANNEL_IDS.forEach { manager.deleteNotificationChannel(it) }
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return

        val soundUri = Uri.parse("android.resource://${context.packageName}/${R.raw.rain_start}")
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.rain_alert_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.rain_alert_channel_description)
            setSound(soundUri, audioAttributes)
        }
        manager.createNotificationChannel(channel)
    }
}
