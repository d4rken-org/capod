package eu.darken.capod.monitor.ui

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.annotation.StringRes
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
import eu.darken.capod.pods.core.HasCase
import eu.darken.capod.pods.core.HasChargeDetection
import eu.darken.capod.pods.core.HasEarDetection
import eu.darken.capod.pods.core.PodDevice
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject


class MonitorNotifications @Inject constructor(
    @ApplicationContext private val context: Context,
    notificationManager: NotificationManager,
    private val notificationViewFactory: MonitorNotificationViewFactory
) {

    private val builderLock = Mutex()
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

        builder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID).apply {
            setChannelId(NOTIFICATION_CHANNEL_ID)
            setContentIntent(openPi)
            priority = NotificationCompat.PRIORITY_LOW
            setSmallIcon(eu.darken.capod.common.R.drawable.devic_earbuds_generic_both)
            setOngoing(true)
            setContentTitle(context.getString(eu.darken.capod.common.R.string.app_name))
        }
    }

    private fun getBuilder(device: PodDevice?): NotificationCompat.Builder {
        if (device == null) {
            return builder.apply {
                setCustomContentView(null)
                setStyle(NotificationCompat.BigTextStyle())
                setContentTitle(context.getString(eu.darken.capod.common.R.string.pods_none_label_short))
                setSubText(context.getString(eu.darken.capod.common.R.string.app_name))
                setSmallIcon(eu.darken.capod.common.R.drawable.devic_earbuds_generic_both)
            }
        }

        return builder.apply {
            @StringRes var contentTitleRes = eu.darken.capod.common.R.string.pods_case_unknown_state
            // Options here should be mutually exclusive, and are prioritized by their order of importance
            // Some options are omitted here, as they will conflict with other options
            // TODO: Implement a settings pane to allow user to customize this
            device.apply {
                // Pods charging state
                // This goes first as pods should not be worn if it is still charging
                if (this is HasChargeDetection && isHeadsetBeingCharged) {
                    contentTitleRes = eu.darken.capod.common.R.string.pods_charging_label
                    return@apply
                }

                // Pods wear state
                if (this is HasEarDetection) {
                    contentTitleRes = if (isBeingWorn) eu.darken.capod.common.R.string.headset_being_worn_label
                        else eu.darken.capod.common.R.string.headset_not_being_worn_label
                    return@apply
                }

                // Case charge state
                // This is under pods wear state as we don't want it conflicting with it
                if (this is HasCase && isCaseCharging) {
                    contentTitleRes = eu.darken.capod.common.R.string.pods_charging_label
                    return@apply
                }
            }

            setStyle(NotificationCompat.DecoratedCustomViewStyle())
            setCustomBigContentView(notificationViewFactory.createContentView(device))
            setSmallIcon(device.iconRes)
            setContentTitle(context.getString(contentTitleRes))
            setSubText(null)
            log(TAG, VERBOSE) { "updatingNotification(): $device" }
        }
    }

    suspend fun getNotification(podDevice: PodDevice?): Notification = builderLock.withLock {
        getBuilder(podDevice).build()
    }

    suspend fun getForegroundInfo(podDevice: PodDevice?): ForegroundInfo = builderLock.withLock {
        getBuilder(podDevice).toForegroundInfo()
    }

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
