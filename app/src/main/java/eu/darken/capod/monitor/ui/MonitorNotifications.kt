package eu.darken.capod.monitor.ui

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.capod.R
import eu.darken.capod.common.BuildConfigWrap
import eu.darken.capod.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.hasApiLevel
import eu.darken.capod.common.notifications.PendingIntentCompat
import eu.darken.capod.main.ui.MainActivity
import eu.darken.capod.pods.core.PodDevice
import javax.inject.Inject


class MonitorNotifications @Inject constructor(
    @ApplicationContext private val context: Context,
    notificationManager: NotificationManager,
) {

    private val builder: NotificationCompat.Builder

    init {
        NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            context.getString(R.string.notification_channel_device_status_label),
            NotificationManager.IMPORTANCE_LOW
        ).run { notificationManager.createNotificationChannel(this) }

        val openIntent = Intent(context, MainActivity::class.java)
        val openPi = PendingIntent.getActivity(
            context,
            0,
            openIntent,
            PendingIntentCompat.FLAG_IMMUTABLE
        )

        builder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setChannelId(NOTIFICATION_CHANNEL_ID)
            .setContentIntent(openPi)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSmallIcon(R.drawable.ic_device_generic_earbuds)
            .setOngoing(true)
            .setContentTitle(context.getString(R.string.app_name))
    }

    fun getBuilder(device: PodDevice?): NotificationCompat.Builder {
        if (device == null) {
            builder.setContentTitle(context.getString(R.string.pods_none_label_short))
            builder.setSmallIcon(R.drawable.ic_device_generic_earbuds)
            return builder
        }

        builder.setSmallIcon(device.iconRes)
        builder.setContentTitle(device.getShortStatus(context))
        log(TAG, VERBOSE) { "updatingNotification(): $device" }
        return builder
    }

    fun getNotification(podDevice: PodDevice?): Notification = getBuilder(podDevice).build()

    fun getForegroundInfo(podDevice: PodDevice?): ForegroundInfo = getBuilder(podDevice).toForegroundInfo()

    @SuppressLint("InlinedApi")
    private fun NotificationCompat.Builder.toForegroundInfo(): ForegroundInfo = if (hasApiLevel(29)) {
        ForegroundInfo(
            NOTIFICATION_ID,
            this.build(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        )
    } else {
        ForegroundInfo(
            NOTIFICATION_ID,
            this.build()
        )
    }

    companion object {
        val TAG = logTag("Monitor", "Notifications")
        private val NOTIFICATION_CHANNEL_ID =
            "${BuildConfigWrap.APPLICATION_ID}.notification.channel.device.status"
        internal const val NOTIFICATION_ID = 1
    }
}
