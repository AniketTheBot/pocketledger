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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
) {
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val petals = 11
            val scale = this.size.minDimension / 100f
            val cx = this.size.width / 2f
            val cy = this.size.height / 2f
            val path = Path()
            val steps = petals * 14
            for (i in 0..steps) {
                val t = i.toFloat() / steps * (2f * Math.PI.toFloat())
                val r = (40f + 7f * sin(petals * t)) * scale
                val x = cx + r * cos(t)
                val y = cy + r * sin(t)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            drawPath(path, color = color)
            drawPath(path, color = Ink, style = Stroke(width = 2.6f * scale))
        }
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
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(Ink)
            .padding(7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        NavDestinations.forEach { dest ->
            val active = dest.key == selected
            if (active) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(percent = 50))
                        .background(Mustard)
                        .border(2.dp, Ink, RoundedCornerShape(percent = 50))
                        .clickable { onSelect(dest.key) }
                        .padding(horizontal = 13.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(dest.icon, contentDescription = null, tint = Ink, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(dest.label, fontWeight = FontWeight.ExtraBold, fontSize = 11.5.sp, color = Ink)
                }
            } else {
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .clickable { onSelect(dest.key) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(dest.icon, contentDescription = dest.label, tint = Cream, modifier = Modifier.size(19.dp))
                    if (dest.key == "review" && reviewCount > 0) {
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Pink)
                        )
                    }
                }
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
