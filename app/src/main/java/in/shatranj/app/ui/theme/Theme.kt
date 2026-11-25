package `in`.shatranj.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object ArenaColors {
    val ObsidianCanvas = Color(0xFF0B0E14)
    val ObsidianSurface = Color(0xFF131822)
    val ObsidianRaised = Color(0xFF1B2230)
    val ObsidianInteractive = Color(0xFF242D3D)
    val SubtleBorder = Color(0x14FFFFFF)
    val StrongBorder = Color(0x24FFFFFF)

    val TextPrimary = Color(0xFFF1F5F9)
    val TextSecondary = Color(0xFFCBD5E1)
    val TextMuted = Color(0xFF94A3B8)

    val ChampagneGold = Color(0xFFE5B85C)
    val ChampagneGoldDeep = Color(0xFFD4A13E)
    val JadeEmerald = Color(0xFF2EC486)
    val MutedCrimson = Color(0xFFE5536B)
    val SoftSky = Color(0xFF56B4E9)

    val PieceWhite = Color(0xFFF8FAFC)
    val PieceBlack = Color(0xFF131822)
    val EvalWhite = Color(0xFFE6DFCF)
    val SelectedSquare = Color(0xFFD6B95C)
}

// Semantic aliases aligned to the Obsidian & Champagne Gold grade
val Slate950 = ArenaColors.ObsidianCanvas
val Slate900 = ArenaColors.ObsidianSurface
val Slate800 = ArenaColors.ObsidianRaised
val Slate700 = ArenaColors.ObsidianInteractive
val Slate400 = ArenaColors.TextMuted
val Amber400 = ArenaColors.ChampagneGold
val Amber500 = ArenaColors.ChampagneGoldDeep
val Emerald400 = ArenaColors.JadeEmerald
val Emerald500 = ArenaColors.JadeEmerald
val Rose400 = ArenaColors.MutedCrimson
val LightSquare = Color(0xFFE6D3B3)
val DarkSquare = Color(0xFF9E7352)
val SelectedSquare = ArenaColors.SelectedSquare

private val DarkColorScheme = darkColorScheme(
    primary = ArenaColors.ChampagneGold,
    onPrimary = ArenaColors.ObsidianCanvas,
    secondary = ArenaColors.JadeEmerald,
    onSecondary = ArenaColors.ObsidianCanvas,
    tertiary = ArenaColors.SoftSky,
    error = ArenaColors.MutedCrimson,
    background = ArenaColors.ObsidianCanvas,
    surface = ArenaColors.ObsidianSurface,
    surfaceVariant = ArenaColors.ObsidianRaised,
    outline = ArenaColors.StrongBorder,
    outlineVariant = ArenaColors.SubtleBorder,
    onBackground = ArenaColors.TextPrimary,
    onSurface = ArenaColors.TextPrimary,
    onSurfaceVariant = ArenaColors.TextMuted
)

@Composable
fun ChessArenaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
