package eu.darken.capod.monitor.ui

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.capod.R
import eu.darken.capod.common.BuildConfigWrap
import eu.darken.capod.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.notifications.PendingIntentCompat
import eu.darken.capod.main.ui.MainActivity
import eu.darken.capod.monitor.core.PodDevice
import eu.darken.capod.monitor.core.battery.BatteryEstimate
import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.pods.core.apple.ble.formatBatteryPercent
import eu.darken.capod.pods.core.apple.ble.isKnownBattery
import javax.inject.Inject
import kotlin.math.roundToInt


class MonitorNotifications @Inject constructor(
    @ApplicationContext private val context: Context,
    notificationManager: NotificationManager,
    private val notificationViewFactory: MonitorNotificationViewFactory
) {

    private val openPi: PendingIntent

    private var cachedBatteryIcon: Pair<Int, IconCompat>? = null

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

    private fun getBuilder(
        device: PodDevice?,
        channelId: String,
        estimate: BatteryEstimate? = null,
        showHint: Boolean = false,
        showBatteryInStatusBar: Boolean = false,
    ): NotificationCompat.Builder {
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
            setCustomBigContentView(notificationViewFactory.createBigContentView(device, estimate))
            setContentTitle("$batteryText ~ $stateText")
            setSubText(null)
            val batteryIcon = if (showBatteryInStatusBar) {
                device.statusBarBatteryPercent()?.let { batteryIcon(it) }
            } else {
                null
            }
            batteryIcon?.let { setSmallIcon(it) }
            log(TAG, VERBOSE) { "updatingNotification(): $device" }
        }
    }

    private fun batteryIcon(percent: Int): IconCompat {
        cachedBatteryIcon?.let { (cachedPercent, icon) -> if (cachedPercent == percent) return icon }

        val size = (STATUS_BAR_ICON_DP * context.resources.displayMetrics.density).roundToInt()
        val text = percent.toString()
        // The status bar only uses the alpha channel, the system tints the icon itself.
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            textSize = size.toFloat()
        }
        val textWidth = paint.measureText(text)
        if (textWidth > size) paint.textSize = size * (size / textWidth)

        val bounds = Rect().also { paint.getTextBounds(text, 0, text.length, it) }
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawText(text, size / 2f, size / 2f - bounds.exactCenterY(), paint)

        return IconCompat.createWithBitmap(bitmap).also { cachedBatteryIcon = percent to it }
    }

    fun getNotification(
        podDevice: PodDevice?,
        estimate: BatteryEstimate? = null,
        showHint: Boolean = false,
        showBatteryInStatusBar: Boolean = false,
    ): Notification =
        getBuilder(podDevice, NOTIFICATION_CHANNEL_ID, estimate, showHint, showBatteryInStatusBar).build()

    fun getNotificationConnected(
        podDevice: PodDevice?,
        estimate: BatteryEstimate? = null,
        showBatteryInStatusBar: Boolean = false,
    ): Notification = getBuilder(
        podDevice,
        NOTIFICATION_CHANNEL_ID_CONNECTED,
        estimate,
        showBatteryInStatusBar = showBatteryInStatusBar,
    ).build()

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
        private const val STATUS_BAR_ICON_DP = 24

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
                setContentText(context.getString(R.string.monitor_notification_starting))
                setPriority(NotificationCompat.PRIORITY_LOW)
                setOngoing(true)
            }.build()
        }
    }
}

/**
 * The icon only has room for one figure, so for earbuds the lower pod wins: it runs out first.
 * A pod charging in the case while the other is in use would hide the one that is draining, so
 * it is skipped.
 */
internal fun PodDevice.statusBarBatteryPercent(): Int? {
    if (!hasDualPods) return lowestKnownPercent(batteryHeadset)

    val leftCharging = isLeftPodCharging == true
    val rightCharging = isRightPodCharging == true
    val inUse = when {
        leftCharging && !rightCharging -> lowestKnownPercent(batteryRight)
        rightCharging && !leftCharging -> lowestKnownPercent(batteryLeft)
        else -> null
    }
    return inUse ?: lowestKnownPercent(batteryLeft, batteryRight)
}

internal fun lowestKnownPercent(vararg levels: Float): Int? = levels
    .filter { isKnownBattery(it) }
    .minOrNull()
    ?.let { (it * 100).roundToInt().coerceIn(0, 100) }
