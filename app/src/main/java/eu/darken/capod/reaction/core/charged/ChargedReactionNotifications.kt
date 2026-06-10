package eu.darken.capod.reaction.core.charged

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
class ChargedReactionNotifications @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notificationManager: NotificationManager,
) {

    init {
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.reaction_charged_channel_label),
                NotificationManager.IMPORTANCE_LOW,
            )
        )
    }

    fun show(profileId: String, deviceLabel: String, thresholdPercent: Int) {
        if (!notificationManager.areNotificationsEnabled()) {
            log(TAG, WARN) { "Notifications disabled — charged notification suppressed" }
            return
        }
        val openPi = PendingIntent.getActivity(
            context,
            PENDING_INTENT_REQUEST_CODE,
            Intent(context, MainActivity::class.java),
            PendingIntentCompat.FLAG_IMMUTABLE,
        )
        val text = if (thresholdPercent >= 100) {
            context.getString(R.string.reaction_charged_notification_text_full, deviceLabel)
        } else {
            context.getString(R.string.reaction_charged_notification_text_partial, deviceLabel, thresholdPercent)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.device_earbuds_generic_both)
            .setContentTitle(context.getString(R.string.reaction_charged_notification_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openPi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        notificationManager.notify(profileId.toTag(), NOTIFICATION_ID, notification)
    }

    fun cancel(profileId: String) {
        notificationManager.cancel(profileId.toTag(), NOTIFICATION_ID)
    }

    /**
     * Drops every charged notification still on screen. Used when monitoring stops — without a
     * running monitor the auto-dismiss-on-unplug promise can't be kept, so don't leave them up.
     */
    fun cancelAll() {
        notificationManager.activeNotifications
            .filter { it.id == NOTIFICATION_ID && it.tag?.startsWith(TAG_PREFIX) == true }
            .forEach { notificationManager.cancel(it.tag, it.id) }
    }

    private fun String.toTag() = "$TAG_PREFIX$this"

    companion object {
        private val TAG = logTag("Reaction", "Charged", "Notifications")
        private val CHANNEL_ID = "${BuildConfigWrap.APPLICATION_ID}.notification.channel.reaction.charged"
        private const val NOTIFICATION_ID = 4
        private const val TAG_PREFIX = "charged:"
        private const val PENDING_INTENT_REQUEST_CODE = 1
    }
}
