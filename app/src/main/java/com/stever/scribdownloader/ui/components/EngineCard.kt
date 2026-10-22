package com.stever.scribdownloader.ui.components

import android.annotation.SuppressLint
import android.webkit.WebView
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.stever.scribdownloader.R

/**
 * The visible engine window (design §5): compact while automatic, expanded
 * when a turnstile challenge needs one manual tap. keepScreenOn keeps the
 * JS timers alive while the service prepares the document (Task 9).
 * The WebView instance is created once per screen and reused (no leak).
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun EngineCard(
    engineStep: Int,
    captchaVisible: Boolean,
    webView: WebView,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
    ) {
        Box(Modifier.keepScreenOn(true).animateContentSize()) {
            Column {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Text(
                        stringResource(R.string.engine_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (engineStep > 0) {
                        Text(
                            stringResource(R.string.engine_step, engineStep),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (captchaVisible) {
                        Text(
                            stringResource(R.string.engine_captcha),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(if (captchaVisible) 380.dp else 180.dp),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    AndroidView(factory = { webView })
                }
                TextButton(onClick = onCancel, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    }
}
