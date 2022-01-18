package eu.darken.capod.reaction.popup.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.capod.R
import eu.darken.capod.common.BuildConfigWrap
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.notifications.PendingIntentCompat
import eu.darken.capod.main.ui.MainActivity
import eu.darken.capod.pods.core.PodDevice
import javax.inject.Inject


class PopUpNotification @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notificationManager: NotificationManager,
    private val notificationViewFactory: PopUpNotificationViewFactory
) {

    private val builder: NotificationCompat.Builder

    init {
        NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            context.getString(R.string.notification_channel_reaction_popup_label),
            NotificationManager.IMPORTANCE_HIGH
        ).run {
            vibrationPattern = LongArray(1) { 0 }
            enableVibration(true)
            enableLights(false)
            setSound(null, null)
            notificationManager.createNotificationChannel(this)
        }

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
            setSmallIcon(R.drawable.ic_device_generic_earbuds)
            setContentTitle(context.getString(R.string.app_name))
        }
    }

    private fun getBuilder(device: PodDevice): NotificationCompat.Builder = builder.apply {
        setStyle(NotificationCompat.DecoratedCustomViewStyle())
        setCustomContentView(notificationViewFactory.createContentView(device))
        setSmallIcon(device.iconRes)
        setSubText(null)
    }

    fun showNotification(device: PodDevice) {
        val notification = getBuilder(device).build()
        log(TAG) { "showNotification(): $device" }
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    fun hideNotification() {
        log(TAG) { "hideNotification()" }
        notificationManager.cancel(NOTIFICATION_ID)
    }

    companion object {
        val TAG = logTag("Reaction", "PopUp", "Notifications")
        private val NOTIFICATION_CHANNEL_ID =
            "${BuildConfigWrap.APPLICATION_ID}.notification.channel.reaction.popup"
        internal const val NOTIFICATION_ID = 9000
    }
}
