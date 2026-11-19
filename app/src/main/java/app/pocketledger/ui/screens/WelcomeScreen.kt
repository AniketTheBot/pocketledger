package app.pocketledger.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import app.pocketledger.R
import app.pocketledger.ui.components.PillButton
import app.pocketledger.ui.theme.Mustard

private val WelcomeBg = Color(0xFFF5F5F5)

@Composable
fun WelcomeScreen(onNext: () -> Unit) {
    WelcomeImmersiveMode()

    val entrance = remember { Animatable(0f) }

    val motion = rememberInfiniteTransition(label = "welcome-idle")
    val coinBob by motion.animateFloat(
        initialValue = 0f, targetValue = -6f,
        animationSpec = infiniteRepeatable(tween(1700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "coinBob",
    )
    val coinTilt by motion.animateFloat(
        initialValue = -3f, targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "coinTilt",
    )
    val handBob by motion.animateFloat(
        initialValue = -2.2f, targetValue = 2.2f,
        animationSpec = infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "handBob",
    )
    val bubbleFloat by motion.animateFloat(
        initialValue = -4f, targetValue = 4f,
        animationSpec = infiniteRepeatable(tween(3200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bubbleFloat",
    )
    val humanBob by motion.animateFloat(
        initialValue = -1.5f, targetValue = 1.5f,
        animationSpec = infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "humanBob",
    )
    val walletBreath by motion.animateFloat(
        initialValue = 0.99f, targetValue = 1.015f,
        animationSpec = infiniteRepeatable(tween(2800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "walletBreath",
    )
    val walletTilt by motion.animateFloat(
        initialValue = -1.5f, targetValue = 1.5f,
        animationSpec = infiniteRepeatable(tween(4200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "walletTilt",
    )
    val shapesDrift by motion.animateFloat(
        initialValue = -2f, targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(5600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "shapesDrift",
    )
    val topShapesDrift by motion.animateFloat(
        initialValue = -1.5f, targetValue = 1.5f,
        animationSpec = infiniteRepeatable(tween(3900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "topShapesDrift",
    )
    val bottomShapesDrift by motion.animateFloat(
        initialValue = 1.5f, targetValue = -1.5f,
        animationSpec = infiniteRepeatable(tween(4700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bottomShapesDrift",
    )

    LaunchedEffect(Unit) {
        entrance.animateTo(1f, tween(1250, easing = FastOutSlowInEasing))
    }

    val p = entrance.value
    fun seg(start: Float, end: Float) = ((p - start) / (end - start)).coerceIn(0f, 1f)
    fun ease(x: Float) = 1f - (1f - x) * (1f - x)

    val shapesIn = ease(seg(0f, 0.35f))
    val topShapesIn = ease(seg(0f, 0.32f))
    val bottomShapesIn = ease(seg(0.18f, 0.55f))
    val bubbleIn = ease(seg(0.08f, 0.42f))
    val humanIn = ease(seg(0.18f, 0.55f))
    val walletIn = ease(seg(0.28f, 0.65f))
    val handIn = ease(seg(0.40f, 0.78f))
    val coinIn = ease(seg(0.45f, 0.82f))
    val coinDrop = ease(seg(0.55f, 0.95f))
    val ctaIn = ease(seg(0.75f, 1f))

    BoxWithConstraints(Modifier.fillMaxSize().background(WelcomeBg)) {
        val u = maxWidth / 271f

        // All abstract decorations are backgrounds; the original Figma elements stay above them.
        Image(
            painter = painterResource(R.drawable.welcome_abstractshapes),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(u * 502f)
                .offset(x = 0.dp, y = 0.dp)
                .graphicsLayer {
                    alpha = shapesIn
                    val s = 0.96f + 0.04f * shapesIn
                    scaleX = s
                    scaleY = s
                    translationY = shapesDrift.dp.toPx()
                },
            contentScale = ContentScale.FillBounds,
        )
        Image(
            painter = painterResource(R.drawable.welcome_topshapes),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(u * 70f)
                .graphicsLayer {
                    alpha = topShapesIn
                    val s = 0.90f + 0.10f * topShapesIn
                    scaleX = s
                    scaleY = s
                    translationY = (-10f * (1f - topShapesIn) + topShapesDrift).dp.toPx()
                },
            contentScale = ContentScale.FillBounds,
        )
        Image(
            painter = painterResource(R.drawable.welcome_bottomshapes),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 18.dp)
                .fillMaxWidth()
                .height(u * 132f)
                .graphicsLayer {
                    alpha = bottomShapesIn
                    val s = 0.94f + 0.06f * bottomShapesIn
                    scaleX = s
                    scaleY = s
                    translationY = (18f * (1f - bottomShapesIn) + bottomShapesDrift).dp.toPx()
                },
            contentScale = ContentScale.FillBounds,
        )

        // 4-unit padding baked into each part drawable restores stroke bleed.
        fun part(x: Float, y: Float, w: Float, h: Float) =
            Modifier
                .offset(x = u * (x - 4f), y = u * (y - 4f))
                .width(u * (w + 8f))
                .height(u * (h + 8f))

        Image(
            painter = painterResource(R.drawable.welcome_human),
            contentDescription = null,
            modifier = part(170.5f, 190.5f, 100f, 89f).graphicsLayer {
                alpha = humanIn
                translationX = (52f * (1f - humanIn)).dp.toPx()
                translationY = humanBob.dp.toPx()
            },
            contentScale = ContentScale.FillBounds,
        )
        Image(
            painter = painterResource(R.drawable.welcome_wallet),
            contentDescription = null,
            modifier = part(60f, 322f, 171f, 148f).graphicsLayer {
                alpha = walletIn
                val s = (0.92f + 0.08f * walletIn) * walletBreath
                scaleX = s
                scaleY = s
                rotationZ = walletTilt
                translationY = (46f * (1f - walletIn)).dp.toPx()
            },
            contentScale = ContentScale.FillBounds,
        )
        Image(
            painter = painterResource(R.drawable.welcome_coin),
            contentDescription = null,
            modifier = part(108f, 298f, 47f, 47f).graphicsLayer {
                alpha = coinIn
                translationY = (handBob + coinBob - 120f * (1f - coinDrop)).dp.toPx()
                rotationZ = coinTilt - 28f * (1f - coinDrop)
            },
            contentScale = ContentScale.FillBounds,
        )
        Image(
            painter = painterResource(R.drawable.welcome_speechbubble),
            contentDescription = null,
            modifier = part(10f, 62.3f, 154f, 129f).graphicsLayer {
                alpha = bubbleIn
                val s = 0.70f + 0.30f * bubbleIn
                scaleX = s
                scaleY = s
                rotationZ = -5f * (1f - bubbleIn)
                translationY = (18f * (1f - bubbleIn) + bubbleFloat).dp.toPx()
            },
            contentScale = ContentScale.FillBounds,
        )
        Image(
            painter = painterResource(R.drawable.welcome_hand),
            contentDescription = null,
            modifier = part(17.5f, 226.5f, 140f, 103f).graphicsLayer {
                alpha = handIn
                translationX = (-64f * (1f - handIn)).dp.toPx()
                translationY = handBob.dp.toPx()
            },
            contentScale = ContentScale.FillBounds,
        )

        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 24.dp, top = 30.dp, end = 24.dp, bottom = 12.dp)
                .graphicsLayer {
                    alpha = ctaIn
                    translationY = (22f * (1f - ctaIn)).dp.toPx()
                },
            contentAlignment = Alignment.Center,
        ) {
            PillButton(
                text = "Let’s Get Started  →",
                onClick = onNext,
                modifier = Modifier.fillMaxWidth(),
                bg = Mustard,
                enabled = ctaIn > 0.2f,
            )
        }
    }
}

@Composable
private fun WelcomeImmersiveMode() {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    DisposableEffect(activity) {
        val window = activity?.window
        val controller = window?.let { WindowInsetsControllerCompat(it, it.decorView) }
        val originalBehavior = controller?.systemBarsBehavior
        val originalCutoutMode = window?.attributes?.layoutInDisplayCutoutMode

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && window != null) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        controller?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())

        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            if (originalBehavior != null) controller?.systemBarsBehavior = originalBehavior
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                window != null &&
                originalCutoutMode != null
            ) {
                window.attributes = window.attributes.apply {
                    layoutInDisplayCutoutMode = originalCutoutMode
                }
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
