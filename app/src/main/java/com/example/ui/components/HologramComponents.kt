package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import kotlin.math.sin

@Composable
fun HolographicOrb(
    modifier: Modifier = Modifier,
    isThinking: Boolean = false,
    isActiveListening: Boolean = false
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orb_rotation")
    
    // Scale Breathe loop
    val breatheScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteTransitionSpec(duration = if (isThinking) 1200 else 2800),
        label = "orb_scale"
    )

    // Inner core rotation angles
    val angle1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteTransitionSpec(duration = 4500),
        label = "orb_angle1"
    )

    val angle2 by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteTransitionSpec(duration = 6000),
        label = "orb_angle2"
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Neon Background dispersion glow
        Box(
            modifier = Modifier
                .size(160.dp)
                .blur(50.dp)
                .clip(RoundedCornerShape(80.dp))
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            if (isActiveListening) ActiveGreen.copy(alpha = 0.5f)
                            else if (isThinking) GlowingPurple.copy(alpha = 0.5f)
                            else NeonCyan.copy(alpha = 0.35f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Custom layered Canvas core drawings
        Canvas(
            modifier = Modifier
                .size(140.dp * breatheScale)
        ) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val center = Offset(canvasWidth / 2, canvasHeight / 2)
            val baseRadius = size.minDimension / 2.5f

            // Outer Core
            rotate(angle1, center) {
                drawCircle(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            NeonCyan.copy(alpha = 0.15f),
                            GlowingPurple.copy(alpha = 0.8f),
                            NeonCyan.copy(alpha = 0.15f)
                        ),
                        center = center
                    ),
                    radius = baseRadius,
                    style = Stroke(width = 3.dp.toPx())
                )
            }

            // Inner oscillating Core rings
            rotate(angle2, center) {
                drawCircle(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            if (isActiveListening) ActiveGreen else NeonCyan,
                            GlowingPurple
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(canvasWidth, canvasHeight)
                    ),
                    radius = baseRadius * 0.7f,
                    style = Stroke(
                        width = 4.dp.toPx(),
                        pathEffect = android.graphics.DashPathEffect(floatArrayOf(50f, 30f), 0f).let {
                            androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(50f, 30f), 0f)
                        }
                    )
                )
            }

            // Center singularity dot
            drawCircle(
                color = if (isActiveListening) ActiveGreen else NeonCyan,
                radius = baseRadius * 0.25f + if (isThinking) 8f else 0f
            )
        }
    }
}

private fun infiniteTransitionSpec(duration: Int): InfiniteRepeatableSpec<Float> {
    return infiniteRepeatable(
        animation = tween(durationMillis = duration, easing = LinearEasing),
        repeatMode = RepeatMode.Reverse
    )
}

@Composable
fun InfiniteVoiceWave(
    modifier: Modifier = Modifier,
    isActive: Boolean = false
) {
    val transition = rememberInfiniteTransition(label = "wave_anim")
    val phaseOffset by transition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val amplitude = if (isActive) height * 0.35f else height * 0.08f
        val frequency = 2.5f

        val path = Path()
        path.moveTo(0f, height / 2)

        for (x in 0..width.toInt() step 5) {
            val angle = (x / width) * frequency * 2 * Math.PI.toFloat() + phaseOffset
            val y = (height / 2) + amplitude * sin(angle)
            path.lineTo(x.toFloat(), y)
        }

        drawPath(
            path = path,
            color = if (isActive) ActiveGreen else NeonCyan.copy(alpha = 0.5f),
            style = Stroke(width = 3.2.dp.toPx())
        )
        
        // Secondary mirror wave
        val path2 = Path()
        path2.moveTo(0f, height / 2)
        for (x in 0..width.toInt() step 5) {
            val angle = (x / width) * frequency * 2 * Math.PI.toFloat() - phaseOffset + Math.PI.toFloat()
            val y = (height / 2) + (amplitude * 0.65f) * sin(angle)
            path2.lineTo(x.toFloat(), y)
        }

        drawPath(
            path = path2,
            color = GlowingPurple.copy(alpha = 0.4f),
            style = Stroke(width = 2.2.dp.toPx())
        )
    }
}

@Composable
fun GlassyCard(
    modifier: Modifier = Modifier,
    borderColor: Color = NeonCyan.copy(alpha = 0.25f),
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(SurfaceGlass)
            .border(1.dp, borderColor, RoundedCornerShape(18.dp))
            .padding(16.dp),
        content = content
    )
}

@Composable
fun HolographicHeading(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: Int = 20,
    accentColor: Color = NeonCyan
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(20.dp)
                .background(accentColor, RoundedCornerShape(2.dp))
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text.uppercase(),
            fontFamily = FontFamily.Monospace,
            fontSize = fontSize.sp,
            fontWeight = FontWeight.Bold,
            color = TextLight,
            letterSpacing = 1.sp
        )
    }
}
