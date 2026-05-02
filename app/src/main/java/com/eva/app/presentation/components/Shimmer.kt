package com.eva.app.presentation.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedCard
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun AnimatedListItem(
    index: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!visible) {
            delay(minOf(index * 50L, 250L))
            visible = true
        }
    }
    AnimatedVisibility(
        visible  = visible,
        enter    = fadeIn(tween(220)) + slideInVertically(tween(260)) { it / 3 },
        modifier = modifier
    ) {
        content()
    }
}

@Composable
fun Modifier.scaledClick(onClick: () -> Unit): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue   = if (isPressed) 0.97f else 1f,
        animationSpec = tween(100),
        label         = "scale"
    )
    return this
        .scale(scale)
        .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
}

@Composable
private fun shimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val x by transition.animateFloat(
        initialValue = 0f,
        targetValue  = 800f,
        animationSpec = infiniteRepeatable(
            animation  = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_x"
    )
    return Brush.linearGradient(
        colors = listOf(Color(0xFFE0E0E0), Color(0xFFF5F5F5), Color(0xFFE0E0E0)),
        start  = Offset(x - 200f, 0f),
        end    = Offset(x + 200f, 0f)
    )
}

private fun Modifier.shimmerBlock(brush: Brush, radius: Int = 6) =
    this.clip(RoundedCornerShape(radius.dp)).background(brush)

@Composable
fun DoctorDetailSkeleton() {
    val brush = shimmerBrush()
    Column(
        modifier            = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Card(shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(0.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE0E0E0)),
            modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.padding(16.dp)) {
                Box(Modifier.size(72.dp).shimmerBlock(brush, 18))
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Spacer(Modifier.height(2.dp))
                    Box(Modifier.fillMaxWidth(0.6f).height(16.dp).shimmerBlock(brush))
                    Box(Modifier.fillMaxWidth(0.35f).height(20.dp).shimmerBlock(brush, 6))
                    Box(Modifier.fillMaxWidth(0.5f).height(11.dp).shimmerBlock(brush))
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).padding(horizontal = 16.dp).background(Color(0xFFE0E0E0)))
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceAround) {
                repeat(3) {
                    Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Box(Modifier.size(44.dp).shimmerBlock(brush, 12))
                        Box(Modifier.width(30.dp).height(13.dp).shimmerBlock(brush))
                        Box(Modifier.width(48.dp).height(10.dp).shimmerBlock(brush))
                    }
                }
            }
        }
        Card(shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(0.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE0E0E0)),
            modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.width(60.dp).height(13.dp).shimmerBlock(brush))
                Box(Modifier.fillMaxWidth(0.75f).height(12.dp).shimmerBlock(brush))
                Box(Modifier.fillMaxWidth(0.5f).height(12.dp).shimmerBlock(brush))
            }
        }
    }
}

@Composable
fun DoctorCardSkeleton() {
    val brush = shimmerBrush()
    Row(
        modifier          = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(48.dp).shimmerBlock(brush, 12))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.fillMaxWidth(0.55f).height(14.dp).shimmerBlock(brush))
            Box(Modifier.fillMaxWidth(0.4f).height(11.dp).shimmerBlock(brush))
        }
    }
    HorizontalDivider(modifier = Modifier.padding(start = 78.dp))
}

@Composable
fun AppointmentCardSkeleton() {
    val brush = shimmerBrush()
    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.fillMaxWidth(0.55f).height(15.dp).shimmerBlock(brush))
                    Box(Modifier.fillMaxWidth(0.4f).height(11.dp).shimmerBlock(brush))
                }
                Box(Modifier.width(72.dp).height(24.dp).shimmerBlock(brush, 20))
            }
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFE0E0E0)))
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                repeat(3) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.width(36.dp).height(10.dp).shimmerBlock(brush))
                        Box(Modifier.width(56.dp).height(12.dp).shimmerBlock(brush))
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Box(Modifier.size(20.dp).align(Alignment.CenterHorizontally).shimmerBlock(brush, 10))
        }
    }
}

@Composable
fun ProfileNameSkeleton() {
    val brush = shimmerBrush()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(Modifier.width(140.dp).height(20.dp).shimmerBlock(brush))
        Box(Modifier.width(100.dp).height(14.dp).shimmerBlock(brush))
    }
}

@Composable
fun MedCardItemSkeleton() {
    val brush = shimmerBrush()
    OutlinedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.size(48.dp).shimmerBlock(brush, 12))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Spacer(Modifier.height(2.dp))
                Box(Modifier.fillMaxWidth(0.55f).height(13.dp).shimmerBlock(brush))
                Box(Modifier.fillMaxWidth(0.4f).height(11.dp).shimmerBlock(brush))
                Box(Modifier.fillMaxWidth(0.65f).height(10.dp).shimmerBlock(brush))
                Box(Modifier.fillMaxWidth(0.35f).height(10.dp).shimmerBlock(brush))
            }
        }
    }
}

@Composable
fun ClinicCardSkeleton() {
    val brush = shimmerBrush()
    Row(
        modifier          = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(48.dp).shimmerBlock(brush, 12))
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.fillMaxWidth(0.6f).height(13.dp).shimmerBlock(brush))
            Box(Modifier.fillMaxWidth(0.75f).height(11.dp).shimmerBlock(brush))
        }
    }
    HorizontalDivider(modifier = Modifier.padding(start = 78.dp))
}

@Composable
fun SpecCardSkeleton() {
    val brush = shimmerBrush()
    Row(
        modifier          = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(48.dp).shimmerBlock(brush, 12))
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.fillMaxWidth(0.5f).height(13.dp).shimmerBlock(brush))
            Box(Modifier.fillMaxWidth(0.75f).height(11.dp).shimmerBlock(brush))
        }
    }
    HorizontalDivider(modifier = Modifier.padding(start = 78.dp))
}

@Composable
fun NotifItemSkeleton() {
    val brush = shimmerBrush()
    Row(
        modifier          = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(Modifier.size(44.dp).shimmerBlock(brush, 12))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Spacer(Modifier.height(1.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Box(Modifier.fillMaxWidth(0.5f).height(13.dp).shimmerBlock(brush))
                Box(Modifier.width(44.dp).height(10.dp).shimmerBlock(brush))
            }
            Box(Modifier.fillMaxWidth(0.85f).height(11.dp).shimmerBlock(brush))
            Box(Modifier.fillMaxWidth(0.6f).height(11.dp).shimmerBlock(brush))
        }
    }
}
