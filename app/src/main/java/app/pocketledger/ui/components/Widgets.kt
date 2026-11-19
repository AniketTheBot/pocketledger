package app.pocketledger.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import app.pocketledger.money.Money
import app.pocketledger.ui.theme.CardCream
import app.pocketledger.ui.theme.Cream
import app.pocketledger.ui.theme.Green
import app.pocketledger.ui.theme.Ink
import app.pocketledger.ui.theme.Mustard
import app.pocketledger.ui.theme.MutedInk
import app.pocketledger.ui.theme.Pink
import app.pocketledger.ui.theme.SoftLine
import kotlin.math.cos
import kotlin.math.sin

/** Cream card with 2.5dp ink border and a solid 4dp hard shadow. */
@Composable
fun StickerCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    color: Color = CardCream,
    padding: PaddingValues = PaddingValues(14.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    Box(modifier.padding(end = 4.dp, bottom = 4.dp)) {
        Box(
            Modifier
                .matchParentSize()
                .offset(4.dp, 4.dp)
                .clip(shape)
                .background(Ink)
        )
        Column(
            Modifier
                .clip(shape)
                .background(color)
                .border(2.5.dp, Ink, shape)
                .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
                .padding(padding),
            content = content,
        )
    }
}

/** Mustard (or custom) pill with hard shadow. */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    bg: Color = Mustard,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(percent = 50)
    Box(modifier.padding(end = 3.dp, bottom = 3.dp)) {
        Box(
            Modifier
                .matchParentSize()
                .offset(3.dp, 3.dp)
                .clip(shape)
                .background(Ink)
        )
        Row(
            Modifier
                .fillMaxWidth()
                .height(46.dp)
                .clip(shape)
                .background(if (enabled) bg else bg.copy(alpha = 0.45f))
                .border(2.5.dp, Ink, shape)
                .clickable(enabled = enabled) { onClick() }
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(text, fontWeight = FontWeight.ExtraBold, fontSize = 13.5.sp, color = Ink)
        }
    }
}

@Composable
fun CandyChip(
    text: String,
    modifier: Modifier = Modifier,
    bg: Color = Green,
    textColor: Color = Ink,
    pill: Boolean = false,
) {
    val shape = if (pill) CircleShape else RoundedCornerShape(9.dp)
    Box(
        modifier
            .clip(shape)
            .background(bg)
            .border(2.dp, Ink, shape)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(text, fontWeight = FontWeight.ExtraBold, fontSize = 10.5.sp, color = textColor)
    }
}

@Composable
fun FilterChipSticker(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CandyChip(
        text = text,
        modifier = modifier.clickable { onClick() },
        bg = if (selected) Mustard else CardCream,
        pill = true,
    )
}

@Composable
fun MoneyKeypad(onKey: (String) -> Unit, modifier: Modifier = Modifier) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf(".", "0", "⌫"),
    )
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { key ->
                    StickerKey(key, Modifier.weight(1f)) { onKey(key) }
                }
            }
        }
    }
}

@Composable
private fun StickerKey(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Box(modifier.padding(end = 3.dp, bottom = 3.dp)) {
        Box(
            Modifier
                .matchParentSize()
                .offset(3.dp, 3.dp)
                .clip(shape)
                .background(Ink)
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(46.dp)
                .clip(shape)
                .background(CardCream)
                .border(2.5.dp, Ink, shape)
                .clickable { onClick() },
            contentAlignment = Alignment.Center,
        ) {
            Text(label, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = Ink)
        }
    }
}

/** Scalloped flower sticker drawn with Canvas (design 5 signature). */
@Composable
fun FlowerSticker(
    label: String,
    color: Color,
    size: Dp,
    modifier: Modifier = Modifier,
    petals: Int = 11,
    depth: Float = 7f,
) {
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val scale = this.size.minDimension / 100f
            val cx = this.size.width / 2f
            val cy = this.size.height / 2f
            val steps = petals * 10
            val points = ArrayList<Offset>(steps + 1)
            for (i in 0..steps) {
                val t = i.toFloat() / steps * (2f * Math.PI.toFloat())
                val r = (40f + depth * sin(petals * t)) * scale
                points.add(Offset(cx + r * cos(t), cy + r * sin(t)))
            }
            // Smooth the scallop with quadratic segments through midpoints so the
            // sticker reads as a rounded flower, not a jagged star.
            val path = Path()
            path.moveTo(points[0].x, points[0].y)
            var i = 1
            while (i < points.size - 1) {
                val midX = (points[i].x + points[i + 1].x) / 2f
                val midY = (points[i].y + points[i + 1].y) / 2f
                path.quadraticBezierTo(points[i].x, points[i].y, midX, midY)
                i++
            }
            path.quadraticBezierTo(points.last().x, points.last().y, points[0].x, points[0].y)
            path.close()
            drawPath(path, color = color)
            drawPath(path, color = Ink, style = Stroke(width = 2.6f * scale))
        }
        if (label.isNotBlank()) {
            Text(
                text = label,
                modifier = Modifier.padding(horizontal = size * 0.16f),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.ExtraBold,
                fontSize = (size.value * 0.095f).sp,
                lineHeight = (size.value * 0.115f).sp,
                color = Ink,
            )
        }
    }
}

data class NavDest(val key: String, val label: String, val icon: ImageVector)

val NavDestinations = listOf(
    NavDest("home", "Home", Icons.Filled.Home),
    NavDest("accounts", "Accounts", Icons.Filled.AccountBalanceWallet),
    NavDest("review", "Review", Icons.Filled.ReceiptLong),
    NavDest("settings", "Settings", Icons.Filled.Settings),
)

@Composable
fun BottomPillNav(
    selected: String,
    onSelect: (String) -> Unit,
    reviewCount: Int,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .animateContentSize(spring(dampingRatio = 0.82f, stiffness = 420f))
                .clip(RoundedCornerShape(percent = 50))
                .background(Ink)
                .padding(horizontal = 6.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            NavDestinations.forEach { dest ->
                NavPillItem(
                    dest = dest,
                    active = dest.key == selected,
                    badgeCount = if (dest.key == "review") reviewCount else 0,
                    onClick = { onSelect(dest.key) },
                )
            }
        }
    }
}

@Composable
private fun NavPillItem(
    dest: NavDest,
    active: Boolean,
    badgeCount: Int,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(percent = 50)
    val bg by animateColorAsState(
        targetValue = if (active) Mustard else Color.Transparent,
        animationSpec = tween(180, easing = FastOutSlowInEasing),
        label = "navBg",
    )
    val contentColor by animateColorAsState(
        targetValue = if (active) Ink else Cream,
        animationSpec = tween(180),
        label = "navContent",
    )
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val iconScale by animateFloatAsState(
        targetValue = when {
            pressed -> 0.85f
            active -> 1.08f
            else -> 0.95f
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "navIconScale",
    )
    val badgeScale by animateFloatAsState(
        targetValue = if (badgeCount > 0) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 500f),
        label = "navBadge",
    )

    Row(
        Modifier
            .animateContentSize(spring(dampingRatio = 0.8f, stiffness = 380f))
            .clip(shape)
            .background(bg)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .heightIn(min = 42.dp)
            .padding(horizontal = if (active) 14.dp else 11.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            Icon(
                dest.icon,
                contentDescription = dest.label,
                tint = contentColor,
                modifier = Modifier
                    .size(21.dp)
                    .scale(iconScale),
            )
            if (badgeCount > 0) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-3).dp)
                        .scale(badgeScale)
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(Pink)
                        .border(1.5.dp, Ink, CircleShape)
                )
            }
        }
        AnimatedVisibility(
            visible = active,
            enter = fadeIn(tween(170)) +
                expandHorizontally(spring(dampingRatio = 0.7f, stiffness = 420f), expandFrom = Alignment.Start),
            exit = fadeOut(tween(90)) + shrinkHorizontally(tween(110), shrinkTowards = Alignment.Start),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(6.dp))
                Text(
                    dest.label,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 12.sp,
                    color = contentColor,
                )
            }
        }
    }
}

@Composable
fun Avatar(initial: String, bg: Color, size: Dp = 40.dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(bg)
            .border(2.5.dp, Ink, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initial.take(1).uppercase(),
            fontWeight = FontWeight.ExtraBold,
            fontSize = (size.value * 0.36f).sp,
            color = Ink,
        )
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier.padding(horizontal = 2.dp, vertical = 2.dp),
        fontWeight = FontWeight.ExtraBold,
        fontSize = 10.sp,
        letterSpacing = 1.sp,
        color = MutedInk,
    )
}

@Composable
fun AmountChip(direction: String, amountMinor: Long, currency: String, modifier: Modifier = Modifier) {
    val credit = direction == "CREDIT"
    CandyChip(
        text = (if (credit) "+" else "−") + Money.format(kotlin.math.abs(amountMinor), currency),
        modifier = modifier,
        bg = if (credit) Green else Pink,
    )
}

@Composable
fun StickerTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    numeric: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = MutedInk, fontSize = 13.sp) },
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
        shape = RoundedCornerShape(14.dp),
        keyboardOptions = KeyboardOptions(
            keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Ink,
            unfocusedBorderColor = Ink,
            cursorColor = Ink,
            focusedTextColor = Ink,
            unfocusedTextColor = Ink,
            focusedContainerColor = CardCream,
            unfocusedContainerColor = CardCream,
        ),
    )
}

@Composable
fun InlineDivider(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(SoftLine)
    )
}
