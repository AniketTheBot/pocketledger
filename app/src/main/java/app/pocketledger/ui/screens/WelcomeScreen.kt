package app.pocketledger.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import app.pocketledger.R
import app.pocketledger.ui.theme.ArchivoBlack
import app.pocketledger.ui.theme.Ink
import kotlinx.coroutines.delay

private val CollagePurple = Color(0xFF9B5DE5)
private val CollageBlue = Color(0xFF4A90C2)

@Composable
fun WelcomeScreen(onNext: () -> Unit) {
    val headerAlpha = remember { Animatable(0f) }
    val artAlpha = remember { Animatable(0f) }
    val artScale = remember { Animatable(0.96f) }
    val ctaAlpha = remember { Animatable(0f) }
    val ctaLift = remember { Animatable(10f) }

    LaunchedEffect(Unit) {
        headerAlpha.animateTo(1f, tween(360))
        delay(120)
        artAlpha.animateTo(1f, tween(520))
        artScale.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = 240f))
        delay(220)
        ctaAlpha.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
        ctaLift.animateTo(0f, tween(420, easing = FastOutSlowInEasing))
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = headerAlpha.value },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LogoMark()
            Spacer(Modifier.width(10.dp))
            Text("PocketLedger", fontFamily = ArchivoBlack, fontSize = 19.sp, color = Ink)
        }

        Spacer(Modifier.height(10.dp))
        BoxWithConstraints(
            Modifier.fillMaxWidth().weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            val artWidth = minOf(maxWidth, maxHeight * (310f / 480f))
            Image(
                painter = painterResource(R.drawable.welcome_illustration),
                contentDescription = null,
                modifier = Modifier
                    .width(artWidth)
                    .aspectRatio(310f / 480f)
                    .graphicsLayer {
                        alpha = artAlpha.value
                        scaleX = artScale.value
                        scaleY = artScale.value
                    },
                contentScale = ContentScale.Fit,
            )
        }

        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(58.dp)
                .graphicsLayer {
                    alpha = ctaAlpha.value
                    translationY = ctaLift.value.dp.toPx()
                }
                .clip(RoundedCornerShape(percent = 50))
                .background(CollagePurple)
                .clickable(onClick = onNext),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Let’s Get Started  →",
                fontFamily = ArchivoBlack,
                fontSize = 17.sp,
                color = Color.White,
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun LogoMark() {
    Canvas(Modifier.size(30.dp)) {
        val r = size.minDimension * 0.34f
        drawCircle(CollageBlue, r, center = androidx.compose.ui.geometry.Offset(size.width * 0.40f, size.height * 0.55f))
        drawCircle(CollagePurple, r, center = androidx.compose.ui.geometry.Offset(size.width * 0.62f, size.height * 0.45f))
    }
}
