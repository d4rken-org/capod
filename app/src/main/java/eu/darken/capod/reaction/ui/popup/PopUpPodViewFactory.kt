package eu.darken.capod.reaction.ui.popup

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.view.isInvisible
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.capod.R
import eu.darken.capod.common.debug.autoreport.DebugSettings
import eu.darken.capod.databinding.PopupNotificationDualPodsBinding
import eu.darken.capod.databinding.PopupNotificationSinglePodsBinding
import eu.darken.capod.pods.core.*
import eu.darken.capod.pods.core.apple.DualAirPods
import eu.darken.capod.pods.core.apple.SingleApplePods
import javax.inject.Inject


class PopUpPodViewFactory @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val debugSettings: DebugSettings,
) {

    private val context = ContextThemeWrapper(appContext, R.style.AppTheme)
    private val layoutInflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater

    fun createContentView(parent: ViewGroup, device: PodDevice): View = when (device) {
        is DualAirPods -> createDualApplePods(parent, device)
        // Unused, has no case to trigger reaction?
        is SingleApplePods -> createSingleApplePods(parent, device)
        else -> throw IllegalArgumentException("Unexpected device: $device")
    }

    private fun createDualApplePods(parent: ViewGroup, device: DualAirPods): View =
        PopupNotificationDualPodsBinding.inflate(layoutInflater, parent, false).apply {
            device.apply {
                podIcon.setImageResource(iconRes)
                podLabel.text = getLabel(context)
                signal.text = getSignalQuality(context)
                signal.isInvisible = debugSettings.isDebugModeEnabled.value

                // Left
                podLeftBatteryIcon.setImageResource(getBatteryDrawable(batteryLeftPodPercent))
                podLeftBatteryLabel.text = getBatteryLevelLeftPod(context)

                // Case
                podCaseBatteryIcon.setImageResource(getBatteryDrawable(batteryCasePercent))
                podCaseBatteryLabel.text = getBatteryLevelCase(context)

                // Right
                podRightBatteryIcon.setImageResource(getBatteryDrawable(batteryRightPodPercent))
                podRightBatteryLabel.text = getBatteryLevelRightPod(context)
            }
        }.root

    private fun createSingleApplePods(parent: ViewGroup, device: SingleApplePods): View =
        PopupNotificationSinglePodsBinding.inflate(layoutInflater, parent, false).apply {
            device.apply {
                headphonesIcon.setImageResource(iconRes)
                headphonesLabel.text = getLabel(context)
                signal.text = getSignalQuality(context)
                signal.isInvisible = debugSettings.isDebugModeEnabled.value

                headphonesBatteryIcon.setImageResource(getBatteryDrawable(batteryHeadsetPercent))
                headphonesBatteryLabel.text = getBatteryLevelHeadset(context)
            }
        }.root

}