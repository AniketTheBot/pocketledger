package app.pocketledger.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pocketledger.R
import app.pocketledger.ui.theme.ArchivoBlack
import kotlinx.coroutines.delay

private val CollagePurple = Color(0xFF9B5DE5)

@Composable
fun WelcomeScreen(onNext: () -> Unit) {
    val artAlpha = remember { Animatable(0f) }
    val artScale = remember { Animatable(1.04f) }

    LaunchedEffect(Unit) {
        artAlpha.animateTo(1f, tween(520))
        artScale.animateTo(1f, tween(760, easing = FastOutSlowInEasing))
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(R.drawable.welcome_illustration),
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(271f / 502f)
                .graphicsLayer {
                    alpha = artAlpha.value
                    scaleX = artScale.value
                    scaleY = artScale.value
                },
            contentScale = ContentScale.Fit,
        )

        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 26.dp)
                .height(58.dp)
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
    }
}
