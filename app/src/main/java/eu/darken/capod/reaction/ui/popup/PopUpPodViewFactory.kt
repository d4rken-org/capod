package eu.darken.capod.reaction.ui.popup

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.view.isGone
import androidx.core.view.isVisible
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.capod.R
import eu.darken.capod.common.debug.DebugSettings
import eu.darken.capod.databinding.PopupNotificationDualPodsBinding
import eu.darken.capod.databinding.PopupNotificationSinglePodsBinding
import eu.darken.capod.pods.core.DualPodDevice
import eu.darken.capod.pods.core.HasCase
import eu.darken.capod.pods.core.PodDevice
import eu.darken.capod.pods.core.SinglePodDevice
import eu.darken.capod.pods.core.formatBatteryPercent
import eu.darken.capod.pods.core.getBatteryDrawable
import eu.darken.capod.pods.core.getSignalQuality
import javax.inject.Inject


class PopUpPodViewFactory @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val debugSettings: DebugSettings,
) {

    private val context = ContextThemeWrapper(appContext, R.style.AppTheme)
    private val layoutInflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater

    fun createContentView(parent: ViewGroup, device: PodDevice): View = when (device) {
        is DualPodDevice -> createDualPods(parent, device)
        // Unused, has no case to trigger reaction?
        is SinglePodDevice -> createSinglePod(parent, device)
        else -> throw IllegalArgumentException("Unexpected device: $device")
    }

    private fun createDualPods(parent: ViewGroup, device: DualPodDevice): View =
        PopupNotificationDualPodsBinding.inflate(layoutInflater, parent, false).apply {
            podLabel.text = device.getLabel(context)
            signal.text = device.getSignalQuality(context)
            signal.isGone = !debugSettings.isDebugModeEnabled.value

            // Left
            val leftPercent = device.batteryLeftPodPercent
            podLeftIcon.setImageResource(device.leftPodIcon)
            podLeftIcon.imageTintList = null
            podLeftBatteryIcon.setImageResource(getBatteryDrawable(leftPercent))
            podLeftBatteryLabel.text = formatBatteryPercent(context, leftPercent)

            // Case
            podCaseContainer.isVisible = device is HasCase
            (device as? HasCase)?.let { case ->
                val casePercent = case.batteryCasePercent
                podCaseIcon.setImageResource(case.caseIcon)
                podCaseIcon.imageTintList = null
                podCaseBatteryIcon.setImageResource(getBatteryDrawable(casePercent))
                podCaseBatteryLabel.text = formatBatteryPercent(context, casePercent)
            }

            // Right
            val rightPercent = device.batteryRightPodPercent
            podRightIcon.setImageResource(device.rightPodIcon)
            podRightIcon.imageTintList = null
            podRightBatteryIcon.setImageResource(getBatteryDrawable(rightPercent))
            podRightBatteryLabel.text = formatBatteryPercent(context, rightPercent)
        }.root

    private fun createSinglePod(parent: ViewGroup, device: SinglePodDevice): View =
        PopupNotificationSinglePodsBinding.inflate(layoutInflater, parent, false).apply {
            headphonesIcon.setImageResource(device.iconRes)
            headphonesIcon.imageTintList = null
            headphonesLabel.text = device.getLabel(context)
            signal.text = device.getSignalQuality(context)
            signal.isGone = !debugSettings.isDebugModeEnabled.value

            val headsetPercent = device.batteryHeadsetPercent
            headphonesBatteryIcon.setImageResource(getBatteryDrawable(headsetPercent))
            headphonesBatteryLabel.text = formatBatteryPercent(context, headsetPercent)
        }.root

}