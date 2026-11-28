package com.example.wetzelbulk

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val CoachPink = Color(0xFFEC4899)
private val CoachRed = Color(0xFFDC2626)

/**
 * Trainer icon. Normal: plain avatar. New note: pulsing pink ring + red "!" badge.
 * No note at all for this workout: dimmed (still tappable).
 */
@Composable
fun TrainerAvatarButton(hasNewNote: Boolean, hasAnyNote: Boolean, onClick: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "coachPulse")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(650, easing = LinearEasing), RepeatMode.Reverse),
        label = "coachPulseScale"
    )

    Box(
        modifier = Modifier
            .size(36.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.coach_avatar),
            contentDescription = if (hasNewNote) "New note from your trainer" else "Trainer note",
            modifier = Modifier
                .size(32.dp)
                .scale(if (hasNewNote) pulse else 1f)
                .alpha(if (hasAnyNote) 1f else 0.45f)
                .clip(CircleShape)
                .border(
                    width = if (hasNewNote) 2.5.dp else 1.dp,
                    color = if (hasNewNote) CoachPink else Color(0xFFD1D5DB),
                    shape = CircleShape
                )
        )
        if (hasNewNote) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(13.dp)
                    .background(CoachRed, CircleShape)
                    .border(1.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("!", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

@Composable
fun CoachNoteDialog(note: CoachNote?, workoutType: String, isHistory: Boolean, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Image(
                painter = painterResource(R.drawable.coach_avatar),
                contentDescription = null,
                modifier = Modifier.size(72.dp).clip(CircleShape).border(2.dp, CoachPink, CircleShape)
            )
        },
        title = {
            Text(
                text = if (isHistory) "Coach's note for that $workoutType day" else "Coach's note · $workoutType",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                if (note == null) {
                    Text(
                        text = if (isHistory) "There was no note for this workout."
                        else "No notes from your coach for $workoutType yet.",
                        fontSize = 13.sp,
                        color = Color.Gray
                    )
                } else {
                    Text(
                        text = "Written " + SimpleDateFormat("EEE MMM d, h:mm a", Locale.getDefault()).format(Date(note.createdAtMillis)),
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SelectionContainer {
                        Text(text = note.text, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = CoachPink)
            ) {
                Text("Got it", fontWeight = FontWeight.Bold)
            }
        }
    )
}
