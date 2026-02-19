package eu.darken.capod.main.ui.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.toColorInt
import androidx.core.view.isVisible
import com.google.android.material.chip.Chip
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.capod.R
import eu.darken.capod.common.EdgeToEdgeHelper
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.uix.Activity2
import eu.darken.capod.common.upgrade.UpgradeRepo
import eu.darken.capod.databinding.WidgetConfigurationActivityBinding
import javax.inject.Inject

@AndroidEntryPoint
class WidgetConfigurationActivity : Activity2() {

    private val vm: WidgetConfigurationViewModel by viewModels()
    private lateinit var ui: WidgetConfigurationActivityBinding

    @Inject lateinit var profileAdapter: WidgetProfileSelectionAdapter
    @Inject lateinit var upgradeRepo: UpgradeRepo
    @ApplicationContext @Inject lateinit var appContext: Context

    private var widgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

    private var isUpdatingHexFromCode = false
    private val checkerboardDrawable: BitmapDrawable by lazy { createCheckerboardDrawable() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setResult(RESULT_CANCELED)

        widgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )

        log(TAG) { "onCreate(widgetId=$widgetId)" }

        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            log(TAG) { "Invalid widget ID, finishing" }
            finish()
            return
        }

        ui = WidgetConfigurationActivityBinding.inflate(layoutInflater)
        setContentView(ui.root)

        EdgeToEdgeHelper(this).apply {
            insetsPadding(ui.root, top = true, bottom = true, left = true, right = true)
        }

        ui.profilesRecycler.adapter = profileAdapter

        setupPreviewContainer()
        setupPresetChips()
        setupColorSwatches(ui.bgColorGrid, isBg = true)
        setupColorSwatches(ui.fgColorGrid, isBg = false)
        setupHexInput()
        setupTransparencySlider()
        setupShowDeviceLabelSwitch()
        setupResetButton()

        ui.cancelButton.setOnClickListener {
            log(TAG) { "Cancel clicked" }
            finish()
        }

        ui.confirmButton.setOnClickListener {
            if (vm.state.value?.isPro == true) {
                log(TAG) { "Confirm clicked" }
                confirmSelection()
            } else {
                log(TAG) { "Upgrade clicked" }
                upgradeRepo.launchBillingFlow(this@WidgetConfigurationActivity)
            }
        }

        vm.state.observe2 { state ->
            val adapterItems = state.profiles.map { profile ->
                WidgetProfileSelectionVH.Item(
                    profile = profile,
                    isSelected = profile.id == state.selectedProfile,
                    onProfileClick = { vm.selectProfile(it.id) }
                )
            }
            profileAdapter.asyncDiffer.submitUpdate(adapterItems)

            ui.proRequiredCaption.isVisible = !state.isPro

            if (state.isPro) {
                ui.confirmButton.text = getString(android.R.string.ok)
                ui.confirmButton.isEnabled = state.canConfirm
            } else {
                ui.confirmButton.text = getString(R.string.general_upgrade_action)
                ui.confirmButton.isEnabled = true
            }

            updatePresetChipSelection(state.activePreset)
            updateCustomSectionsVisibility(state.isCustomMode)
            updateSwatchSelection(ui.bgColorGrid, state.theme.backgroundColor)
            updateSwatchSelection(ui.fgColorGrid, state.theme.foregroundColor)
            updateHexInputs(state.theme)

            // Disable transparency slider when using Material You (no custom bg to apply alpha to)
            val hasCustomBg = state.theme.backgroundColor != null
            ui.transparencySlider.isEnabled = hasCustomBg
            ui.transparencyLabel.alpha = if (hasCustomBg) 1.0f else 0.5f

            val transparencyPercent = ((255 - state.theme.backgroundAlpha) / 255f * 100f)
            val displayPercent = (transparencyPercent / 5f).toInt() * 5
            if (!ui.transparencySlider.isPressed) {
                ui.transparencySlider.value = displayPercent.toFloat()
            }
            ui.transparencyLabel.text = getString(
                R.string.widget_config_transparency_label
            ) + if (hasCustomBg && displayPercent > 0) " ($displayPercent%)" else ""

            ui.showDeviceLabelSwitch.isChecked = state.theme.showDeviceLabel

            val selectedProfileLabel = state.profiles.firstOrNull { it.id == state.selectedProfile }?.label
            val deviceLabel = ui.previewCard.findViewById<android.widget.TextView>(R.id.preview_device_label)
            deviceLabel?.text = selectedProfileLabel ?: ""

            updatePreview(state.theme)
        }
    }

    private val widgetThemeContext: Context by lazy {
        ContextThemeWrapper(this, com.google.android.material.R.style.Theme_Material3_DynamicColors_DayNight)
    }

    private fun resolveWidgetThemeColor(attr: Int): Int {
        val typedArray = widgetThemeContext.theme.obtainStyledAttributes(intArrayOf(attr))
        val color = typedArray.getColor(0, Color.BLACK)
        typedArray.recycle()
        return color
    }

    private fun setupPreviewContainer() {
        // Initial background set from XML, updated dynamically in updatePreview
    }

    private fun updatePreview(theme: WidgetTheme) {
        val previewRoot = ui.previewCard.findViewById<View>(R.id.preview_widget_root)

        // Background color applied to the inner view, matching the real widget
        val bgColor = theme.backgroundColor
        if (bgColor != null) {
            previewRoot.setBackgroundColor(WidgetTheme.applyAlpha(bgColor, theme.backgroundAlpha))
        } else {
            previewRoot.setBackgroundColor(resolveWidgetThemeColor(android.R.attr.colorBackground))
        }

        // Show checkerboard behind preview only when there's actual transparency to visualize
        val hasTransparency = bgColor != null && theme.backgroundAlpha < 255
        ui.previewContainer.background = if (hasTransparency) {
            checkerboardDrawable
        } else {
            AppCompatResources.getDrawable(this, R.drawable.widget_preview_checkerboard)
        }

        // Foreground (text + icon colors)
        val fgColor = theme.foregroundColor
        val defaultTextColor = resolveWidgetThemeColor(android.R.attr.textColorPrimary)
        val defaultIconColor = resolveWidgetThemeColor(android.R.attr.colorAccent)

        val textViews = listOf(
            R.id.preview_left_label,
            R.id.preview_right_label,
            R.id.preview_case_label,
            R.id.preview_device_label,
        )
        val iconViews = listOf(
            R.id.preview_left_icon,
            R.id.preview_right_icon,
            R.id.preview_case_icon,
        )

        for (id in textViews) {
            val tv = ui.previewCard.findViewById<android.widget.TextView>(id) ?: continue
            tv.setTextColor(fgColor ?: defaultTextColor)
        }

        for (id in iconViews) {
            val iv = ui.previewCard.findViewById<ImageView>(id) ?: continue
            if (fgColor != null) {
                iv.setColorFilter(fgColor, PorterDuff.Mode.SRC_IN)
            } else {
                iv.setColorFilter(defaultIconColor, PorterDuff.Mode.SRC_IN)
            }
        }

        // Device label visibility
        val deviceLabel = ui.previewCard.findViewById<View>(R.id.preview_device_label)
        deviceLabel?.isVisible = theme.showDeviceLabel
    }

    private fun createCheckerboardDrawable(): BitmapDrawable {
        val cellSize = (8 * resources.displayMetrics.density).toInt()
        val bitmap = createBitmap(cellSize * 2, cellSize * 2)
        val canvas = Canvas(bitmap)
        val paint = Paint()
        // Light squares
        paint.color = 0xFFE8E8E8.toInt()
        canvas.drawRect(0f, 0f, (cellSize * 2).toFloat(), (cellSize * 2).toFloat(), paint)
        // Dark squares
        paint.color = 0xFFD0D0D0.toInt()
        canvas.drawRect(0f, 0f, cellSize.toFloat(), cellSize.toFloat(), paint)
        canvas.drawRect(
            cellSize.toFloat(),
            cellSize.toFloat(),
            (cellSize * 2).toFloat(),
            (cellSize * 2).toFloat(),
            paint
        )

        return bitmap.toDrawable(resources).apply {
            tileModeX = Shader.TileMode.REPEAT
            tileModeY = Shader.TileMode.REPEAT
        }
    }

    private fun setupPresetChips() {
        val presetNames = mapOf(
            WidgetTheme.Preset.MATERIAL_YOU to getString(R.string.widget_config_preset_material_you),
            WidgetTheme.Preset.CLASSIC_DARK to getString(R.string.widget_config_preset_dark),
            WidgetTheme.Preset.CLASSIC_LIGHT to getString(R.string.widget_config_preset_light),
            WidgetTheme.Preset.BLUE to getString(R.string.widget_config_preset_blue),
            WidgetTheme.Preset.GREEN to getString(R.string.widget_config_preset_green),
            WidgetTheme.Preset.RED to getString(R.string.widget_config_preset_red),
        )

        for (preset in WidgetTheme.Preset.entries) {
            val chip = Chip(this).apply {
                text = presetNames[preset] ?: preset.name
                isCheckable = true
                tag = preset
                setOnClickListener { vm.selectPreset(preset) }
            }
            ui.presetChipGroup.addView(chip)
        }

        // Custom chip
        val customChip = Chip(this).apply {
            text = getString(R.string.widget_config_custom_label)
            isCheckable = true
            tag = CUSTOM_CHIP_TAG
            setOnClickListener {
                val defaultBg = resolveWidgetThemeColor(android.R.attr.colorBackground)
                val defaultFg = resolveWidgetThemeColor(android.R.attr.textColorPrimary)
                vm.enterCustomMode(defaultBg, defaultFg)
            }
        }
        ui.presetChipGroup.addView(customChip)
    }

    private fun updatePresetChipSelection(activePreset: WidgetTheme.Preset?) {
        for (i in 0 until ui.presetChipGroup.childCount) {
            val chip = ui.presetChipGroup.getChildAt(i) as? Chip ?: continue
            chip.isChecked = if (activePreset != null) {
                chip.tag == activePreset
            } else {
                chip.tag == CUSTOM_CHIP_TAG
            }
        }
    }

    private fun updateCustomSectionsVisibility(isCustomMode: Boolean) {
        val visibility = if (isCustomMode) View.VISIBLE else View.GONE
        ui.bgColorLabel.visibility = visibility
        ui.bgColorGrid.visibility = visibility
        ui.bgHexInputLayout.visibility = visibility
        ui.fgColorLabel.visibility = visibility
        ui.fgColorGrid.visibility = visibility
        ui.fgHexInputLayout.visibility = visibility
    }

    private fun setupColorSwatches(grid: GridLayout, isBg: Boolean) {
        for (color in SWATCH_COLORS) {
            val itemView = LayoutInflater.from(this)
                .inflate(R.layout.widget_color_swatch_item, grid, false)

            val swatchColor = itemView.findViewById<View>(R.id.swatch_color)
            val bgDrawable = swatchColor.background?.mutate() as? GradientDrawable ?: continue
            bgDrawable.setColor(color)
            swatchColor.background = bgDrawable

            itemView.setOnClickListener {
                ui.bgHexInput.clearFocus()
                ui.fgHexInput.clearFocus()
                if (isBg) vm.setBackgroundColor(color) else vm.setForegroundColor(color)
            }

            val params = GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED),
                GridLayout.spec(GridLayout.UNDEFINED, 1f),
            ).apply {
                width = GridLayout.LayoutParams.WRAP_CONTENT
                height = GridLayout.LayoutParams.WRAP_CONTENT
                setGravity(Gravity.CENTER)
            }
            grid.addView(itemView, params)
        }
    }

    private fun updateSwatchSelection(grid: GridLayout, selectedColor: Int?) {
        for (i in 0 until grid.childCount) {
            val itemView = grid.getChildAt(i) as? FrameLayout ?: continue
            val color = SWATCH_COLORS.getOrNull(i) ?: continue
            val isSelected =
                selectedColor != null && (selectedColor or 0xFF000000.toInt()) == (color or 0xFF000000.toInt())

            itemView.findViewById<View>(R.id.swatch_selected_ring)?.isVisible = isSelected
            itemView.findViewById<ImageView>(R.id.swatch_check)?.apply {
                isVisible = isSelected
                if (isSelected) {
                    val checkColor = WidgetTheme.bestContrastForeground(color)
                    setColorFilter(checkColor)
                }
            }
        }
    }

    private fun setupHexInput() {
        val hexFilter = InputFilter { source, _, _, _, _, _ ->
            val filtered = source.toString().uppercase().filter { it in "0123456789ABCDEF" }
            if (filtered == source.toString()) null else filtered
        }

        ui.bgHexInput.filters = arrayOf(hexFilter, InputFilter.LengthFilter(6))
        ui.fgHexInput.filters = arrayOf(hexFilter, InputFilter.LengthFilter(6))

        ui.bgHexInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (isUpdatingHexFromCode) return
                val hex = s?.toString() ?: return
                if (hex.length == 6) {
                    try {
                        val color = "#$hex".toColorInt()
                        vm.setBackgroundColor(color)
                    } catch (_: IllegalArgumentException) {
                    }
                }
            }
        })

        ui.fgHexInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (isUpdatingHexFromCode) return
                val hex = s?.toString() ?: return
                if (hex.length == 6) {
                    try {
                        val color = "#$hex".toColorInt()
                        vm.setForegroundColor(color)
                    } catch (_: IllegalArgumentException) {
                    }
                }
            }
        })
    }

    private fun updateHexInputs(theme: WidgetTheme) {
        isUpdatingHexFromCode = true
        try {
            val bgHex = theme.backgroundColor?.let { String.format("%06X", 0xFFFFFF and it) } ?: ""
            if (ui.bgHexInput.text.toString() != bgHex && !ui.bgHexInput.hasFocus()) {
                ui.bgHexInput.setText(bgHex)
            }

            val fgHex = theme.foregroundColor?.let { String.format("%06X", 0xFFFFFF and it) } ?: ""
            if (ui.fgHexInput.text.toString() != fgHex && !ui.fgHexInput.hasFocus()) {
                ui.fgHexInput.setText(fgHex)
            }
        } finally {
            isUpdatingHexFromCode = false
        }
    }

    private fun setupTransparencySlider() {
        ui.transparencySlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val alpha = 255 - (value / 100f * 255f).toInt()
                vm.setBackgroundAlpha(alpha)
            }
        }
    }

    private fun setupShowDeviceLabelSwitch() {
        ui.showDeviceLabelSwitch.setOnCheckedChangeListener { _, isChecked ->
            vm.setShowDeviceLabel(isChecked)
        }
    }

    private fun setupResetButton() {
        ui.resetButton.setOnClickListener {
            vm.resetToDefaults()
        }
    }

    private fun confirmSelection() {
        vm.confirmSelection()

        val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        setResult(RESULT_OK, resultValue)

        val appWidgetManager = AppWidgetManager.getInstance(appContext)

        WidgetProvider.updateWidget(
            context = appContext,
            appWidgetManager = appWidgetManager,
            widgetId = widgetId
        )

        finish()
    }

    companion object {
        private val TAG = logTag("Widget", "ConfigurationActivity")
        private const val CUSTOM_CHIP_TAG = "custom"

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
    }
}
