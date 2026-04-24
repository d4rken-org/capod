package eu.darken.capod.reaction.core.sleep

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.capod.R
import eu.darken.capod.common.BuildConfigWrap
import eu.darken.capod.common.debug.logging.Logging.Priority.WARN
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.notifications.PendingIntentCompat
import eu.darken.capod.main.ui.MainActivity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SleepReactionNotifications @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notificationManager: NotificationManager,
) {

    init {
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.reaction_sleep_channel_label),
                NotificationManager.IMPORTANCE_LOW,
            )
        )
    }

    fun show(deviceLabel: String) {
        if (!notificationManager.areNotificationsEnabled()) {
            log(TAG, WARN) { "Notifications disabled — sleep pause will be silent" }
            return
        }
        val openPi = PendingIntent.getActivity(
            context,
            PENDING_INTENT_REQUEST_CODE,
            Intent(context, MainActivity::class.java),
            PendingIntentCompat.FLAG_IMMUTABLE,
        )
        val text = context.getString(R.string.reaction_sleep_notification_text, deviceLabel)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.device_earbuds_generic_both)
            .setContentTitle(context.getString(R.string.reaction_sleep_notification_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openPi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private val TAG = logTag("Reaction", "Sleep", "Notifications")
        private val CHANNEL_ID = "${BuildConfigWrap.APPLICATION_ID}.notification.channel.reaction.sleep"
        private const val NOTIFICATION_ID = 3
        private const val PENDING_INTENT_REQUEST_CODE = 0
    }
}
