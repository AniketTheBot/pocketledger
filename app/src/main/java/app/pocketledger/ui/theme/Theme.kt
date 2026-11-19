package app.pocketledger.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pocketledger.R

// ---- Sticker Ledger palette (design-tokens.json → themes[4]) ----
val Ink = Color(0xFF191614)
val Cream = Color(0xFFF6F3EC)
val CardCream = Color(0xFFFFFDF7)
val Mustard = Color(0xFFF2C14E)
val Pink = Color(0xFFF6AEDB)
val Green = Color(0xFFA9E8A2)
val Purple = Color(0xFFC9B8F5)
val MutedInk = Color(0xFF6E675D)
val CanvasYellow = Color(0xFFF0C64F)
val Danger = Color(0xFFC14E3E)
val SoftLine = Color(0xFFDCD5C4)
val Panel = Color(0xFFEFE9DA)

val ArchivoBlack = FontFamily(Font(R.font.archivoblack_regular, FontWeight.Black))

/** Extra roles Material3 doesn't model: candy positives/negatives + hard-shadow ink. */
data class StickerColors(
    val positive: Color,
    val onPositive: Color,
    val negative: Color,
    val onNegative: Color,
    val pending: Color,
    val onPending: Color,
    val border: Color,
    val hardShadow: Color,
    val canvas: Color,
    val muted: Color,
)

val LocalStickerColors = staticCompositionLocalOf {
    StickerColors(
        positive = Green, onPositive = Ink,
        negative = Pink, onNegative = Ink,
        pending = Purple, onPending = Ink,
        border = Ink, hardShadow = Ink,
        canvas = CanvasYellow, muted = MutedInk,
    )
}

private val StickerSchemes = lightColorScheme(
    primary = Mustard,
    onPrimary = Ink,
    primaryContainer = Color(0xFFFBE7AE),
    onPrimaryContainer = Color(0xFF4A3A0A),
    secondary = Pink,
    onSecondary = Ink,
    secondaryContainer = Pink,
    onSecondaryContainer = Color(0xFF5C2345),
    tertiary = Green,
    onTertiary = Ink,
    background = Cream,
    onBackground = Ink,
    surface = CardCream,
    onSurface = Ink,
    surfaceVariant = Panel,
    onSurfaceVariant = MutedInk,
    outline = Ink,
    outlineVariant = SoftLine,
    error = Danger,
    onError = Color.White,
)

private val StickerTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = ArchivoBlack,
        fontWeight = FontWeight.Black,
        fontSize = 30.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = ArchivoBlack,
        fontWeight = FontWeight.Black,
        fontSize = 22.sp,
        lineHeight = 27.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 10.sp,
        letterSpacing = 1.sp,
    ),
)

private val StickerShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun PocketLedgerTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalStickerColors provides StickerColors(
            positive = Green,
            onPositive = Ink,
            negative = Pink,
            onNegative = Ink,
            pending = Purple,
            onPending = Ink,
            border = Ink,
            hardShadow = Ink,
            canvas = CanvasYellow,
            muted = MutedInk,
        )
    ) {
        MaterialTheme(
            colorScheme = StickerSchemes,
            typography = StickerTypography,
            shapes = StickerShapes,
            content = content,
        )
    }
}
