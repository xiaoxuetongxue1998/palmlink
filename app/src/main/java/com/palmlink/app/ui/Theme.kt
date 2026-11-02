package com.palmlink.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

internal val BgBase = Color(0xFF0B0E12)
internal val Surface1 = Color(0xFF151B22)
internal val Surface2 = Color(0xFF1D2733)
internal val Primary = Color(0xFF2ED3C6)
internal val PrimaryDim = Color(0x332ED3C6)
internal val OnPrimary = Color(0xFF04201D)
internal val TextMain = Color(0xFFE8F0F7)
internal val TextMuted = Color(0xFF8B9DAD)
internal val TextDim = Color(0xFF5A6A7A)
internal val Danger = Color(0xFFE5484D)
internal val DangerDim = Color(0x33E5484D)
internal val Ok = Color(0xFF3ECF8E)

private val PalmColorScheme = darkColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    background = BgBase,
    onBackground = TextMain,
    surface = Surface1,
    onSurface = TextMain,
    error = Danger
)

@Composable
fun PalmLinkTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = PalmColorScheme, content = content)
}
