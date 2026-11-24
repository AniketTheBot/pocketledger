package app.pocketledger.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pocketledger.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Illustrations built from the original Figma sticker parts (Coin, Hand, Human,
 * SpeechBubble, Wallet) shipped in `design/assets/welcome-svg`.
 *
 * Every part pops in with a spring, then keeps a slow ambient bob/tilt so screens
 * outside the welcome flow share the same animated art language instead of the
 * old hand-drawn flower placeholders.
 */

private data class ArtPart(
    @DrawableRes val res: Int,
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
    val delayMs: Int = 0,
    val bobDp: Float = 0f,
    val bobMs: Int = 3000,
    val tiltDeg: Float = 0f,
    val tiltMs: Int = 4200,
    val breath: Boolean = false,
    val drop: DropSpec? = null,
)

/** A coin dipping into a wallet: hovers, drops, rests, rises back, repeats. */
private data class DropSpec(
    val fromDp: Float,
    val startMs: Int = 1300,
    val fallMs: Int = 700,
    val holdMs: Int = 500,
    val riseMs: Int = 700,
    val periodMs: Int = 4200,
)

/**
 * SMS permission scene: a bank message floats in while the hand holds a coin.
 * The coin is positioned exactly like the welcome illustration (parented to the
 * hand) so both screens read as the same artwork.
 */
@Composable
fun SmsArrivalArt(modifier: Modifier = Modifier) {
    StickerScene(
        modifier = modifier,
        designWidth = 240f,
        designHeight = 232f,
        contentDescription = "A bank message arrives and turns into money in your hand.",
        parts = listOf(
            ArtPart(
                res = R.drawable.art_speechbubble,
                x = 4f, y = 0f, w = 152f, h = 130f,
                delayMs = 0,
                bobDp = 5f, bobMs = 3200,
                tiltDeg = 2f, tiltMs = 4800,
                breath = true,
            ),
            ArtPart(
                res = R.drawable.art_hand,
                x = 40f, y = 100f, w = 141f, h = 104f,
                delayMs = 260,
                bobDp = 3f, bobMs = 2800,
                tiltDeg = 1.5f, tiltMs = 4200,
            ),
            ArtPart(
                res = R.drawable.art_coin,
                x = 130f, y = 171f, w = 48f, h = 48f,
                delayMs = 520,
                bobDp = 5f, bobMs = 2200,
                tiltDeg = 9f, tiltMs = 3000,
            ),
        ),
    )
}

/**
 * Review empty state: the coin drops into the wallet and the wallet breathes.
 * The coin is listed before the wallet so it disappears behind the flap as it
 * lands; no clipping mask is needed.
 */
@Composable
fun AllClearArt(modifier: Modifier = Modifier) {
    StickerScene(
        modifier = modifier,
        designWidth = 230f,
        designHeight = 212f,
        contentDescription = "A coin drops safely into your wallet.",
        parts = listOf(
            ArtPart(
                res = R.drawable.art_coin,
                x = 98f, y = 62f, w = 44f, h = 44f,
                delayMs = 480,
                tiltDeg = 8f, tiltMs = 3000,
                drop = DropSpec(fromDp = 28f),
            ),
            ArtPart(
                res = R.drawable.art_wallet,
                x = 40f, y = 68f, w = 150f, h = 130f,
                delayMs = 80,
                bobDp = 2f, bobMs = 3600,
                tiltDeg = 1.2f, tiltMs = 5200,
                breath = true,
            ),
        ),
    )
}

@Composable
private fun StickerScene(
    parts: List<ArtPart>,
    designWidth: Float,
    designHeight: Float,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    BoxWithConstraints(
        modifier.semantics {
            if (contentDescription != null) this.contentDescription = contentDescription
        }
    ) {
        val u = maxWidth / designWidth
        Box(Modifier.fillMaxWidth().height(u * designHeight)) {
            parts.forEach { part -> ArtPartView(part, u) }
        }
    }
}

@Composable
private fun ArtPartView(part: ArtPart, u: Dp) {
    // Entrance: staggered pop with a tiny upward lift.
    val alpha = remember { Animatable(0f) }
    val scale = remember { Animatable(0.86f) }
    val lift = remember { Animatable(18f) }

    val motion = rememberInfiniteTransition(label = "sticker-art")
    val bob by motion.animateFloat(
        initialValue = -part.bobDp / 2f,
        targetValue = part.bobDp / 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(part.bobMs, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "bob",
    )
    val tilt by motion.animateFloat(
        initialValue = -part.tiltDeg / 2f,
        targetValue = part.tiltDeg / 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(part.tiltMs, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "tilt",
    )
    val breath by motion.animateFloat(
        initialValue = if (part.breath) 0.985f else 1f,
        targetValue = if (part.breath) 1.015f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween((part.bobMs * 1.4f).toInt(), easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breath",
    )

    val drop = part.drop
    var dropProgress = 1f
    if (drop != null) {
        val progress by motion.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = keyframes {
                    durationMillis = drop.periodMs
                    0f at 0
                    0f at drop.startMs
                    1f at drop.startMs + drop.fallMs
                    1f at drop.startMs + drop.fallMs + drop.holdMs
                    0f at drop.startMs + drop.fallMs + drop.holdMs + drop.riseMs
                    0f at drop.periodMs
                },
                repeatMode = RepeatMode.Restart,
            ),
            label = "drop",
        )
        dropProgress = progress
    }

    LaunchedEffect(Unit) {
        delay(part.delayMs.toLong())
        launch { alpha.animateTo(1f, tween(240)) }
        launch {
            scale.animateTo(
                1f,
                spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
            )
        }
        launch {
            lift.animateTo(
                0f,
                spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow),
            )
        }
    }

    val dropOffset = if (drop != null) -drop.fromDp * (1f - dropProgress) else 0f

    Image(
        painter = painterResource(part.res),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .offset(x = u * part.x, y = u * part.y)
            .size(width = u * part.w, height = u * part.h)
            .graphicsLayer {
                this.alpha = alpha.value
                scaleX = scale.value * breath
                scaleY = scale.value * breath
                rotationZ = tilt
                translationY = (lift.value + bob + dropOffset).dp.toPx()
            },
    )
}
