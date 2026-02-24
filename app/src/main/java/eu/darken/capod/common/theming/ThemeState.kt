package eu.darken.capod.common.theming

data class ThemeState(
    val mode: ThemeMode = ThemeMode.SYSTEM,
    val style: ThemeStyle = ThemeStyle.DEFAULT,
    val color: ThemeColor = ThemeColor.BLUE,
)
