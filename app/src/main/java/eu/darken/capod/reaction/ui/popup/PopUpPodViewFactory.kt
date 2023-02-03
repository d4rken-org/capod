package eu.darken.capod.reaction.ui.popup

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.capod.R
import eu.darken.capod.common.debug.DebugSettings
import eu.darken.capod.databinding.PopupNotificationDualPodsBinding
import eu.darken.capod.databinding.PopupNotificationSinglePodsBinding
import eu.darken.capod.pods.core.*
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
            device.apply {
                podIcon.setImageResource(iconRes)
                podLabel.text = getLabel(context)
                signal.text = getSignalQuality(context)
                signal.isInvisible = debugSettings.isDebugModeEnabled.value

                // Left
                podLeftIcon.setImageResource(device.leftPodIcon)
                podLeftBatteryIcon.setImageResource(getBatteryDrawable(batteryLeftPodPercent))
                podLeftBatteryLabel.text = getBatteryLevelLeftPod(context)

                // Case
                podCaseContainer.isVisible = device is HasCase
                (device as? HasCase)?.let { case ->
                    podCaseIcon.setImageResource(case.caseIcon)
                    podCaseBatteryIcon.setImageResource(getBatteryDrawable(case.batteryCasePercent))
                    podCaseBatteryLabel.text = case.getBatteryLevelCase(context)
                }

                // Right
                podRightIcon.setImageResource(device.rightPodIcon)
                podRightBatteryIcon.setImageResource(getBatteryDrawable(batteryRightPodPercent))
                podRightBatteryLabel.text = getBatteryLevelRightPod(context)
            }
        }.root

    private fun createSinglePod(parent: ViewGroup, device: SinglePodDevice): View =
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