package eu.darken.capod.main.ui.widget

import android.content.Context
import eu.darken.capod.R
import eu.darken.capod.main.ui.components.shortLabel
import eu.darken.capod.monitor.core.PodDevice
import eu.darken.capod.monitor.core.visibleAncModes
import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting

object AncWidgetRenderStateMapper {

    fun map(
        context: Context,
        device: PodDevice?,
        theme: WidgetTheme,
        isPro: Boolean,
        hasConfiguredProfile: Boolean,
        profileLabel: String?,
        layout: AncLayout,
    ): AncWidgetRenderState {
        val bgColor = WidgetRenderStateMapper.resolvedBgColor(context, theme)
        val textColor = WidgetRenderStateMapper.resolvedTextColor(context, theme)
        val iconColor = WidgetRenderStateMapper.resolvedIconColor(context, theme)
        val activeColor = WidgetRenderStateMapper.resolveThemeColor(context, com.google.android.material.R.attr.colorSecondaryContainer)
        val onActiveColor = WidgetRenderStateMapper.resolveThemeColor(context, com.google.android.material.R.attr.colorOnSecondaryContainer)

        return when {
            !isPro -> AncWidgetRenderState.Message(
                theme = theme,
                resolvedBgColor = bgColor,
                resolvedTextColor = textColor,
                resolvedIconColor = iconColor,
                primaryText = context.getString(R.string.upgrade_capod_label),
                secondaryText = context.getString(R.string.upgrade_capod_description),
            )

            device == null -> {
                val messageRes = if (hasConfiguredProfile) {
                    R.string.widget_no_data_label
                } else {
                    R.string.overview_nomaindevice_label
                }
                AncWidgetRenderState.Message(
                    theme = theme,
                    resolvedBgColor = bgColor,
                    resolvedTextColor = textColor,
                    resolvedIconColor = iconColor,
                    primaryText = context.getString(messageRes),
                )
            }

            !device.hasAncControl -> AncWidgetRenderState.Message(
                theme = theme,
                resolvedBgColor = bgColor,
                resolvedTextColor = textColor,
                resolvedIconColor = iconColor,
                primaryText = context.getString(R.string.anc_widget_no_anc_support_label),
            )

            !device.isAapConnected -> AncWidgetRenderState.Message(
                theme = theme,
                resolvedBgColor = bgColor,
                resolvedTextColor = textColor,
                resolvedIconColor = iconColor,
                primaryText = context.getString(R.string.anc_widget_aap_not_connected_label),
            )

            !device.isAapReady -> AncWidgetRenderState.Message(
                theme = theme,
                resolvedBgColor = bgColor,
                resolvedTextColor = textColor,
                resolvedIconColor = iconColor,
                primaryText = context.getString(R.string.anc_widget_aap_connecting_label),
            )

            else -> {
                val ancMode = device.ancMode ?: return AncWidgetRenderState.Message(
                    theme = theme,
                    resolvedBgColor = bgColor,
                    resolvedTextColor = textColor,
                    resolvedIconColor = iconColor,
                    primaryText = context.getString(R.string.anc_widget_aap_connecting_label),
                )

                val currentMode = ancMode.current
                val pendingMode = device.pendingAncMode

                val filteredModes = device.visibleAncModes

                // If the pending mode has been filtered out of visibleAncModes (e.g. device
                // reports AllowOff=false while OFF is still pending), fall back to currentMode
                // for the ACTIVE highlight so the widget never renders with zero active buttons.
                val pendingVisible = pendingMode != null && filteredModes.contains(pendingMode)
                val displayMode = if (pendingVisible) pendingMode else currentMode

                val modeItems = filteredModes.map { mode ->
                    ModeItem(
                        mode = mode,
                        label = mode.shortLabel(context),
                        iconRes = mode.iconDrawableRes(),
                        state = when {
                            pendingVisible && mode == pendingMode -> ButtonState.PENDING
                            mode == displayMode -> ButtonState.ACTIVE
                            else -> ButtonState.INACTIVE
                        },
                    )
                }

                AncWidgetRenderState.Active(
                    theme = theme,
                    resolvedBgColor = bgColor,
                    resolvedTextColor = textColor,
                    resolvedIconColor = iconColor,
                    resolvedActiveColor = activeColor,
                    resolvedOnActiveColor = onActiveColor,
                    modes = modeItems,
                    deviceLabel = profileLabel ?: device.getLabel(context),
                    layout = layout,
                )
            }
        }
    }
}

private fun AapSetting.AncMode.Value.iconDrawableRes(): Int = when (this) {
    AapSetting.AncMode.Value.OFF -> R.drawable.ic_anc_off
    AapSetting.AncMode.Value.ON -> R.drawable.ic_anc_on
    AapSetting.AncMode.Value.TRANSPARENCY -> R.drawable.ic_anc_transparency
    AapSetting.AncMode.Value.ADAPTIVE -> R.drawable.ic_anc_adaptive
}
