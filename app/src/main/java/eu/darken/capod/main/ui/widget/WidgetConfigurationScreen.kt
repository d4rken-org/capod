package eu.darken.capod.main.ui.widget

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Shader
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Check
import androidx.compose.material.icons.twotone.Palette
import androidx.compose.material.icons.twotone.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.isVisible
import eu.darken.capod.R
import eu.darken.capod.common.compose.Preview2
import eu.darken.capod.common.compose.PreviewWrapper
import eu.darken.capod.common.compose.preview.MockPodDataProvider
import eu.darken.capod.pods.core.PodDevice
import eu.darken.capod.profiles.core.DeviceProfile

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun WidgetConfigurationScreen(
    state: WidgetConfigurationViewModel.State,
    onSelectProfile: (DeviceProfile) -> Unit,
    onSelectPreset: (WidgetTheme.Preset) -> Unit,
    onEnterCustomMode: (defaultBg: Int, defaultFg: Int) -> Unit,
    onSetBackgroundColor: (Int) -> Unit,
    onSetForegroundColor: (Int) -> Unit,
    onSetBackgroundAlpha: (Int) -> Unit,
    onSetShowDeviceLabel: (Boolean) -> Unit,
    onReset: () -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val screenHPad = 16.dp
    val cardPad = 16.dp

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(top = 24.dp, bottom = 16.dp),
        ) {
            Text(
                text = stringResource(R.string.widget_config_screen_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = screenHPad, end = screenHPad, bottom = 16.dp),
            )

            // Card A — Select Device
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = screenHPad),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(modifier = Modifier.padding(cardPad)) {
                    Text(
                        text = stringResource(R.string.widget_configuration_title),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = stringResource(R.string.widget_configuration_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    state.profiles.forEach { profile ->
                        ProfileSelectionItem(
                            profile = profile,
                            isSelected = profile.id == state.selectedProfile,
                            onClick = { onSelectProfile(profile) },
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Card B — Appearance
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = screenHPad),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(modifier = Modifier.padding(cardPad)) {
                    // Appearance header + reset
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = stringResource(R.string.widget_config_appearance_label),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        TextButton(
                            onClick = onReset,
                            modifier = Modifier.align(Alignment.End),
                        ) {
                            Text(text = stringResource(R.string.widget_config_reset_label))
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Live preview
                    WidgetPreview(
                        theme = state.theme,
                        deviceLabel = state.profiles.firstOrNull { it.id == state.selectedProfile }?.label,
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Transparency slider
                    val hasCustomBg = state.theme.backgroundColor != null
                    val transparencyPercent = ((255 - state.theme.backgroundAlpha) / 255f * 100f)
                    val displayPercent = (transparencyPercent / 5f).toInt() * 5

                    Text(
                        text = buildString {
                            append(stringResource(R.string.widget_config_transparency_label))
                            if (hasCustomBg && displayPercent > 0) append(" ($displayPercent%)")
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (hasCustomBg) 1f else 0.5f),
                    )

                    Slider(
                        value = displayPercent.toFloat(),
                        onValueChange = { value ->
                            val alpha = 255 - (value / 100f * 255f).toInt()
                            onSetBackgroundAlpha(alpha)
                        },
                        valueRange = 0f..100f,
                        steps = 19,
                        enabled = hasCustomBg,
                    )

                    // Show device label switch
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.widget_config_show_device_label),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = state.theme.showDeviceLabel,
                            onCheckedChange = onSetShowDeviceLabel,
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Preset chips
                    Text(
                        text = stringResource(R.string.widget_config_preset_label),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    PresetChips(
                        activePreset = state.activePreset,
                        isCustomMode = state.isCustomMode,
                        onSelectPreset = onSelectPreset,
                        onEnterCustomMode = onEnterCustomMode,
                    )

                    // Custom color sections — animated expand/collapse
                    AnimatedVisibility(
                        visible = state.isCustomMode,
                        enter = expandVertically(
                            expandFrom = Alignment.Top,
                        ) + fadeIn(animationSpec = tween(200)),
                        exit = shrinkVertically(
                            shrinkTowards = Alignment.Top,
                        ) + fadeOut(animationSpec = tween(150)),
                    ) {
                        Column {
                            Spacer(modifier = Modifier.height(16.dp))

                            // Background color
                            Text(
                                text = stringResource(R.string.widget_config_background_color_label),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            ColorSwatchGrid(
                                selectedColor = state.theme.backgroundColor,
                                onColorSelected = onSetBackgroundColor,
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            HexColorInput(
                                color = state.theme.backgroundColor,
                                onColorChanged = onSetBackgroundColor,
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Foreground color
                            Text(
                                text = stringResource(R.string.widget_config_foreground_color_label),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            ColorSwatchGrid(
                                selectedColor = state.theme.foregroundColor,
                                onColorSelected = onSetForegroundColor,
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            HexColorInput(
                                color = state.theme.foregroundColor,
                                onColorChanged = onSetForegroundColor,
                            )
                        }
                    }
                }
            }
        }

        // Bottom bar
        Surface(tonalElevation = 3.dp) {
            Column(modifier = Modifier.windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Bottom))) {
                if (!state.isPro) {
                    Text(
                        text = stringResource(R.string.common_feature_requires_pro_msg),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 4.dp),
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    OutlinedButton(onClick = onCancel) {
                        Text(text = stringResource(android.R.string.cancel))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onConfirm,
                        enabled = if (state.isPro) state.canConfirm else true,
                    ) {
                        Text(
                            text = if (state.isPro) {
                                stringResource(android.R.string.ok)
                            } else {
                                stringResource(R.string.general_upgrade_action)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileSelectionItem(
    profile: DeviceProfile,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outlineVariant
        },
        animationSpec = tween(durationMillis = 200),
        label = "profileBorderColor",
    )
    val borderWidth by animateDpAsState(
        targetValue = if (isSelected) 2.dp else 1.dp,
        animationSpec = tween(durationMillis = 200),
        label = "profileBorderWidth",
    )
    val containerColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        animationSpec = tween(durationMillis = 200),
        label = "profileContainerColor",
    )

    OutlinedCard(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(borderWidth, borderColor),
        colors = CardDefaults.outlinedCardColors(
            containerColor = containerColor,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(profile.model.iconRes),
                contentDescription = null,
                modifier = Modifier.size(28.dp),
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = profile.label,
                    style = MaterialTheme.typography.bodyLarge,
                )
                val modelText = when (profile.model) {
                    PodDevice.Model.UNKNOWN -> stringResource(R.string.pods_unknown_label)
                    else -> profile.model.label
                }
                Text(
                    text = modelText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            RadioButton(
                selected = isSelected,
                onClick = null,
            )
        }
    }
}

@Composable
private fun WidgetPreview(
    theme: WidgetTheme,
    deviceLabel: String?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    val checkerboardDrawable = remember(density) {
        val cellSize = (8 * context.resources.displayMetrics.density).toInt()
        val bitmap = createBitmap(cellSize * 2, cellSize * 2)
        val canvas = Canvas(bitmap)
        val paint = Paint()
        paint.color = 0xFFE8E8E8.toInt()
        canvas.drawRect(0f, 0f, (cellSize * 2).toFloat(), (cellSize * 2).toFloat(), paint)
        paint.color = 0xFFD0D0D0.toInt()
        canvas.drawRect(0f, 0f, cellSize.toFloat(), cellSize.toFloat(), paint)
        canvas.drawRect(
            cellSize.toFloat(), cellSize.toFloat(),
            (cellSize * 2).toFloat(), (cellSize * 2).toFloat(), paint,
        )
        bitmap.toDrawable(context.resources).apply {
            tileModeX = Shader.TileMode.REPEAT
            tileModeY = Shader.TileMode.REPEAT
        }
    }

    val resolvedBgColor = remember(context) {
        resolveThemeColor(context, android.R.attr.colorBackground)
    }
    val resolvedTextColor = remember(context) {
        resolveThemeColor(context, android.R.attr.textColorPrimary)
    }
    val resolvedAccentColor = remember(context) {
        resolveThemeColor(context, android.R.attr.colorAccent)
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        tonalElevation = 2.dp,
    ) {
        AndroidView(
            factory = { ctx ->
                val container = android.widget.FrameLayout(ctx)

                // Outer container for checkerboard
                val outerPadding = (24 * ctx.resources.displayMetrics.density).toInt()
                container.setPadding(outerPadding, outerPadding, outerPadding, outerPadding)

                // Clip wrapper — rounded background + clipToOutline so the inner content is clipped
                val clipWrapper = android.widget.FrameLayout(ctx).apply {
                    setBackgroundResource(R.drawable.widget_preview_bg)
                    clipToOutline = true
                }

                // Inner preview content
                LayoutInflater.from(ctx).inflate(R.layout.widget_config_preview, clipWrapper, true)

                container.addView(
                    clipWrapper, android.widget.FrameLayout.LayoutParams(
                        android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                        android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                        android.view.Gravity.CENTER,
                    )
                )

                container
            },
            update = { container ->
                val hasTransparency = theme.backgroundColor != null && theme.backgroundAlpha < 255
                container.background = if (hasTransparency) {
                    checkerboardDrawable
                } else {
                    androidx.appcompat.content.res.AppCompatResources.getDrawable(
                        context, R.drawable.widget_preview_checkerboard
                    )
                }

                val clipWrapper = container.getChildAt(0) ?: return@AndroidView
                val widgetRoot = clipWrapper.findViewById<View>(R.id.preview_widget_root) ?: return@AndroidView

                // Background color applied to the inner view — clipWrapper's outline clips the corners
                val bgColor = theme.backgroundColor
                if (bgColor != null) {
                    widgetRoot.setBackgroundColor(WidgetTheme.applyAlpha(bgColor, theme.backgroundAlpha))
                } else {
                    widgetRoot.setBackgroundColor(resolvedBgColor)
                }

                // Foreground colors
                val fgColor = theme.foregroundColor
                val textColor = fgColor ?: resolvedTextColor
                val iconColor = fgColor ?: resolvedAccentColor

                val textIds = intArrayOf(
                    R.id.preview_left_label, R.id.preview_right_label,
                    R.id.preview_case_label, R.id.preview_device_label,
                )
                val iconIds = intArrayOf(
                    R.id.preview_left_icon, R.id.preview_right_icon, R.id.preview_case_icon,
                )

                for (id in textIds) {
                    clipWrapper.findViewById<TextView>(id)?.setTextColor(textColor)
                }
                for (id in iconIds) {
                    clipWrapper.findViewById<ImageView>(id)?.setColorFilter(iconColor, PorterDuff.Mode.SRC_IN)
                }

                // Device label
                val labelView = clipWrapper.findViewById<TextView>(R.id.preview_device_label)
                labelView?.isVisible = theme.showDeviceLabel
                labelView?.text = deviceLabel ?: ""
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun resolveThemeColor(context: android.content.Context, attr: Int): Int {
    val wrapper = androidx.appcompat.view.ContextThemeWrapper(
        context,
        com.google.android.material.R.style.Theme_Material3_DynamicColors_DayNight,
    )
    val typedArray = wrapper.theme.obtainStyledAttributes(intArrayOf(attr))
    val color = typedArray.getColor(0, android.graphics.Color.BLACK)
    typedArray.recycle()
    return color
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PresetChips(
    activePreset: WidgetTheme.Preset?,
    isCustomMode: Boolean,
    onSelectPreset: (WidgetTheme.Preset) -> Unit,
    onEnterCustomMode: (defaultBg: Int, defaultFg: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    val presetNames = mapOf(
        WidgetTheme.Preset.MATERIAL_YOU to stringResource(R.string.widget_config_preset_material_you),
        WidgetTheme.Preset.CLASSIC_DARK to stringResource(R.string.widget_config_preset_dark),
        WidgetTheme.Preset.CLASSIC_LIGHT to stringResource(R.string.widget_config_preset_light),
        WidgetTheme.Preset.BLUE to stringResource(R.string.widget_config_preset_blue),
        WidgetTheme.Preset.GREEN to stringResource(R.string.widget_config_preset_green),
        WidgetTheme.Preset.RED to stringResource(R.string.widget_config_preset_red),
    )

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        WidgetTheme.Preset.entries.forEach { preset ->
            FilterChip(
                selected = activePreset == preset,
                onClick = { onSelectPreset(preset) },
                label = { Text(presetNames[preset] ?: preset.name) },
                leadingIcon = if (preset == WidgetTheme.Preset.MATERIAL_YOU) {
                    {
                        Icon(
                            imageVector = Icons.TwoTone.Palette,
                            contentDescription = null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize),
                        )
                    }
                } else {
                    {
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(Color(preset.presetBg!! or 0xFF000000.toInt()))
                            )
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(Color(preset.presetFg!! or 0xFF000000.toInt()))
                            )
                        }
                    }
                },
            )
        }
        FilterChip(
            selected = isCustomMode,
            onClick = {
                val defaultBg = resolveThemeColor(context, android.R.attr.colorBackground)
                val defaultFg = resolveThemeColor(context, android.R.attr.textColorPrimary)
                onEnterCustomMode(defaultBg, defaultFg)
            },
            label = { Text(stringResource(R.string.widget_config_custom_label)) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.TwoTone.Tune,
                    contentDescription = null,
                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                )
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorSwatchGrid(
    selectedColor: Int?,
    onColorSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SWATCH_COLORS.forEach { color ->
            val isSelected = selectedColor != null &&
                    (selectedColor or 0xFF000000.toInt()) == (color or 0xFF000000.toInt())

            ColorSwatch(
                color = color,
                isSelected = isSelected,
                onClick = { onColorSelected(color) },
            )
        }
    }
}

@Composable
private fun ColorSwatch(
    color: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val borderWidth by animateDpAsState(
        targetValue = if (isSelected) 3.dp else 1.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "swatchBorderWidth",
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outlineVariant
        },
        animationSpec = tween(durationMillis = 200),
        label = "swatchBorderColor",
    )
    val swatchSize by animateDpAsState(
        targetValue = if (isSelected) 38.dp else 36.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "swatchSize",
    )
    val checkAlpha by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = tween(durationMillis = 150),
        label = "swatchCheckAlpha",
    )

    Box(
        modifier = Modifier
            .size(44.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(swatchSize)
                .clip(CircleShape)
                .background(Color(color))
                .border(
                    width = borderWidth,
                    color = borderColor,
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            val checkColor = WidgetTheme.bestContrastForeground(color)
            Icon(
                imageVector = Icons.TwoTone.Check,
                contentDescription = null,
                modifier = Modifier
                    .size(16.dp)
                    .alpha(checkAlpha),
                tint = Color(checkColor),
            )
        }
    }
}

@Composable
private fun HexColorInput(
    color: Int?,
    onColorChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hexString = color?.let { String.format("%06X", 0xFFFFFF and it) } ?: ""

    var textFieldValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = hexString,
                selection = TextRange(hexString.length)
            )
        )
    }

    // Sync from external color changes (e.g. swatch clicks) only when content genuinely differs
    LaunchedEffect(hexString) {
        val currentNormalized = textFieldValue.text.uppercase().filter { it in "0123456789ABCDEF" }
        if (currentNormalized != hexString) {
            textFieldValue = TextFieldValue(text = hexString, selection = TextRange(hexString.length))
        }
    }

    OutlinedTextField(
        value = textFieldValue,
        onValueChange = { newValue ->
            val filtered = newValue.text.uppercase().filter { it in "0123456789ABCDEF" }.take(6)
            textFieldValue = newValue.copy(text = filtered)
            if (filtered.length == 6) {
                try {
                    val parsed = android.graphics.Color.parseColor("#$filtered")
                    onColorChanged(parsed)
                } catch (_: IllegalArgumentException) {
                }
            }
        },
        label = { Text("#") },
        singleLine = true,
        modifier = modifier.width(160.dp),
    )
}

@Preview2
@Composable
private fun WidgetConfigurationScreenPreview() = PreviewWrapper {
    val profiles = listOf(
        MockPodDataProvider.profile("My AirPods Pro", PodDevice.Model.AIRPODS_PRO2),
        MockPodDataProvider.profile("AirPods Max", PodDevice.Model.AIRPODS_MAX),
    )
    WidgetConfigurationScreen(
        state = WidgetConfigurationViewModel.State(
            profiles = profiles,
            selectedProfile = profiles.first().id,
            isPro = true,
            theme = WidgetTheme.DEFAULT,
            activePreset = WidgetTheme.Preset.MATERIAL_YOU,
            isCustomMode = false,
        ),
        onSelectProfile = {},
        onSelectPreset = {},
        onEnterCustomMode = { _, _ -> },
        onSetBackgroundColor = {},
        onSetForegroundColor = {},
        onSetBackgroundAlpha = {},
        onSetShowDeviceLabel = {},
        onReset = {},
        onConfirm = {},
        onCancel = {},
    )
}

@Preview2
@Composable
private fun WidgetConfigurationScreenCustomPreview() = PreviewWrapper {
    val profiles = listOf(
        MockPodDataProvider.profile("My AirPods Pro", PodDevice.Model.AIRPODS_PRO2),
    )
    WidgetConfigurationScreen(
        state = WidgetConfigurationViewModel.State(
            profiles = profiles,
            selectedProfile = profiles.first().id,
            isPro = true,
            theme = WidgetTheme(
                backgroundColor = 0xFF1565C0.toInt(),
                foregroundColor = 0xFFFFFFFF.toInt(),
                backgroundAlpha = 200,
                showDeviceLabel = true,
            ),
            activePreset = null,
            isCustomMode = true,
        ),
        onSelectProfile = {},
        onSelectPreset = {},
        onEnterCustomMode = { _, _ -> },
        onSetBackgroundColor = {},
        onSetForegroundColor = {},
        onSetBackgroundAlpha = {},
        onSetShowDeviceLabel = {},
        onReset = {},
        onConfirm = {},
        onCancel = {},
    )
}

@Preview2
@Composable
private fun WidgetConfigurationScreenNonProPreview() = PreviewWrapper {
    val profiles = listOf(
        MockPodDataProvider.profile("My AirPods Pro", PodDevice.Model.AIRPODS_PRO2),
    )
    WidgetConfigurationScreen(
        state = WidgetConfigurationViewModel.State(
            profiles = profiles,
            selectedProfile = profiles.first().id,
            isPro = false,
            theme = WidgetTheme.DEFAULT,
            activePreset = WidgetTheme.Preset.MATERIAL_YOU,
            isCustomMode = false,
        ),
        onSelectProfile = {},
        onSelectPreset = {},
        onEnterCustomMode = { _, _ -> },
        onSetBackgroundColor = {},
        onSetForegroundColor = {},
        onSetBackgroundAlpha = {},
        onSetShowDeviceLabel = {},
        onReset = {},
        onConfirm = {},
        onCancel = {},
    )
}

private val SWATCH_COLORS = intArrayOf(
    0xFFF44336.toInt(), // Red
    0xFFE91E63.toInt(), // Pink
    0xFF9C27B0.toInt(), // Purple
    0xFF673AB7.toInt(), // Deep Purple
    0xFF3F51B5.toInt(), // Indigo
    0xFF2196F3.toInt(), // Blue
    0xFF03A9F4.toInt(), // Light Blue
    0xFF00BCD4.toInt(), // Cyan
    0xFF009688.toInt(), // Teal
    0xFF4CAF50.toInt(), // Green
    0xFF8BC34A.toInt(), // Light Green
    0xFFCDDC39.toInt(), // Lime
    0xFFFFEB3B.toInt(), // Yellow
    0xFFFFC107.toInt(), // Amber
    0xFFFF9800.toInt(), // Orange
    0xFFFF5722.toInt(), // Deep Orange
    0xFF795548.toInt(), // Brown
    0xFF9E9E9E.toInt(), // Grey
    0xFF607D8B.toInt(), // Blue Grey
    0xFFFFFFFF.toInt(), // White
    0xFF1E1E1E.toInt(), // Near Black
    0xFF37474F.toInt(), // Dark Blue Grey
    0xFF1B5E20.toInt(), // Dark Green
    0xFF0D47A1.toInt(), // Dark Blue
)
