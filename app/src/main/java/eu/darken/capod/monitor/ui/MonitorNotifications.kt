package eu.darken.capod.monitor.ui

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.capod.R
import eu.darken.capod.common.BuildConfigWrap
import eu.darken.capod.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.notifications.PendingIntentCompat
import eu.darken.capod.main.ui.MainActivity
import eu.darken.capod.monitor.core.MonitoredDevice
import eu.darken.capod.pods.core.PodModel
import eu.darken.capod.pods.core.formatBatteryPercent
import javax.inject.Inject


class MonitorNotifications @Inject constructor(
    @ApplicationContext private val context: Context,
    notificationManager: NotificationManager,
    private val notificationViewFactory: MonitorNotificationViewFactory
) {

    private val openPi: PendingIntent

    init {
        ensureChannel(context)
        NotificationChannel(
            NOTIFICATION_CHANNEL_ID_CONNECTED,
            context.getString(R.string.notification_channel_device_status_connected_label),
            NotificationManager.IMPORTANCE_LOW
        ).run { notificationManager.createNotificationChannel(this) }

        openPi = PendingIntent.getActivity(
            context,
            PENDING_INTENT_REQUEST_CODE,
            Intent(context, MainActivity::class.java),
            PendingIntentCompat.FLAG_IMMUTABLE
        )
    }

    private fun baseBuilder(channelId: String): NotificationCompat.Builder =
        NotificationCompat.Builder(context, channelId).apply {
            setContentIntent(openPi)
            priority = NotificationCompat.PRIORITY_LOW
            setSmallIcon(R.drawable.device_earbuds_generic_both)
            setOngoing(true)
        }

    private fun getBuilder(device: MonitoredDevice?, channelId: String, showHint: Boolean = false): NotificationCompat.Builder {
        if (device == null) {
            return baseBuilder(channelId).apply {
                if (showHint) {
                    setStyle(
                        NotificationCompat.BigTextStyle()
                            .bigText(context.getString(R.string.monitor_notification_extra_enabled_hint))
                    )
                } else {
                    setStyle(NotificationCompat.BigTextStyle())
                }
                setContentTitle(context.getString(R.string.pods_none_label_short))
                setSubText(context.getString(R.string.app_name))
            }
        }

        return baseBuilder(channelId).apply {

            val stateText = when {
                device.isHeadsetBeingCharged == true -> {
                    context.getString(R.string.pods_charging_label)
                }

                device.hasEarDetection -> {
                    if (device.isBeingWorn == true) context.getString(R.string.headset_being_worn_label)
                    else context.getString(R.string.headset_not_being_worn_label)
                }

                device.hasCase && device.isCaseCharging == true -> {
                    context.getString(R.string.pods_charging_label)
                }

                else -> context.getString(R.string.pods_case_unknown_state)
            }

            val batteryText = when {
                device.hasDualPods -> {
                    val left = formatBatteryPercent(context, device.batteryLeft)
                    val right = formatBatteryPercent(context, device.batteryRight)
                    if (device.hasCase) {
                        val case = formatBatteryPercent(context, device.batteryCase)
                        "$left $case $right"
                    } else {
                        "$left $right"
                    }
                }

                device.model != PodModel.UNKNOWN -> {
                    val headset = formatBatteryPercent(context, device.batteryHeadset)
                    if (device.hasCase) {
                        val case = formatBatteryPercent(context, device.batteryCase)
                        "$headset $case"
                    } else {
                        headset
                    }
                }

                else -> "?"
            }

            setStyle(NotificationCompat.DecoratedCustomViewStyle())
            setCustomContentView(notificationViewFactory.createContentView(device))
            setCustomBigContentView(notificationViewFactory.createBigContentView(device))
            setContentTitle("$batteryText ~ $stateText")
            setSubText(null)
            log(TAG, VERBOSE) { "updatingNotification(): $device" }
        }
    }

    fun getNotification(podDevice: MonitoredDevice?, showHint: Boolean = false): Notification =
        getBuilder(podDevice, NOTIFICATION_CHANNEL_ID, showHint).build()

    fun getNotificationConnected(podDevice: MonitoredDevice?): Notification =
        getBuilder(podDevice, NOTIFICATION_CHANNEL_ID_CONNECTED).build()

    fun getStartupNotification(): Notification =
        getBuilder(null, NOTIFICATION_CHANNEL_ID).build()

    companion object {
        val TAG = logTag("Monitor", "Notifications")
        internal val NOTIFICATION_CHANNEL_ID = "${BuildConfigWrap.APPLICATION_ID}.notification.channel.device.status"
        private val NOTIFICATION_CHANNEL_ID_CONNECTED =
            "${BuildConfigWrap.APPLICATION_ID}.notification.channel.device.status.connected"
        internal const val NOTIFICATION_ID = 1
        internal const val NOTIFICATION_ID_CONNECTED = 2
        private const val PENDING_INTENT_REQUEST_CODE = 0

        fun ensureChannel(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    context.getString(R.string.notification_channel_device_status_label),
                    NotificationManager.IMPORTANCE_LOW,
                )
            )
        }

        fun createEarlyNotification(context: Context): Notification {
            val openPi = PendingIntent.getActivity(
                context, PENDING_INTENT_REQUEST_CODE,
                Intent(context, MainActivity::class.java),
                PendingIntentCompat.FLAG_IMMUTABLE,
            )
            return NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID).apply {
                setContentIntent(openPi)
                setSmallIcon(R.drawable.device_earbuds_generic_both)
                setContentTitle(context.getString(R.string.app_name))
                setPriority(NotificationCompat.PRIORITY_LOW)
                setOngoing(true)
            }.build()
        }
    }
}
