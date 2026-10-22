package com.stever.scribdownloader.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stever.scribdownloader.R
import com.stever.scribdownloader.ui.theme.SteVeRGradient

/** Fixed 4-step pipeline display (Task 11 spec: analyse → fetch → download → save). */
@Composable
fun StepsHeader(
    currentStep: Int,
    modifier: Modifier = Modifier,
) {
    val labels = listOf(
        R.string.step_analysis,
        R.string.step_fetch,
        R.string.step_download,
        R.string.step_save,
    )
    Column(modifier = modifier.fillMaxWidth()) {
        labels.forEachIndexed { index, label ->
            val stepNumber = index + 1
            val state = when {
                currentStep <= 0 -> StepState.PENDING
                stepNumber < currentStep -> StepState.DONE
                stepNumber == currentStep -> StepState.ACTIVE
                else -> StepState.PENDING
            }
            val done by animateColorAsState(
                targetValue = when (state) {
                    StepState.DONE -> Color(0xFF10B981)
                    StepState.ACTIVE -> Color(0xFFA855F7)
                    StepState.PENDING -> Color(0x66FFFFFF)
                },
                label = "stepColor",
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .alpha(if (state == StepState.PENDING) 0.55f else 1f),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(done),
                ) {
                    if (state == StepState.DONE) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp),
                        )
                    } else {
                        Text(
                            "$stepNumber",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                        )
                    }
                }
                Text(
                    stringResource(label),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (state == StepState.ACTIVE) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
    }
}

private enum class StepState { DONE, ACTIVE, PENDING }
