package com.stever.scribdownloader.ui

import android.annotation.SuppressLint
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stever.scribdownloader.R
import com.stever.scribdownloader.download.FileActions
import com.stever.scribdownloader.engine.AutopilotEngine
import com.stever.scribdownloader.engine.EngineEvent
import com.stever.scribdownloader.ui.components.EngineCard
import com.stever.scribdownloader.ui.components.ErrorCard
import com.stever.scribdownloader.ui.components.SavedCard
import com.stever.scribdownloader.ui.components.StepsHeader
import com.stever.scribdownloader.ui.theme.SteVeRGradient
import kotlinx.coroutines.flow.MutableSharedFlow

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun DownloadScreen(
    viewModel: DownloadViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    // One WebView (and therefore one engine + one event pipe) per screen session:
    // no leak when the engine card shows/hides, no event loss before binding.
    val engineEvents = remember { MutableSharedFlow<EngineEvent>(extraBufferCapacity = 16) }
    val webView = remember { WebView(context).apply { isVerticalScrollBarEnabled = false } }
    val engine = remember { AutopilotEngine(webView, engineEvents) }
    DisposableEffect(Unit) {
        onDispose {
            // Tab switch / navigation away mid-flow must not leave a phantom
            // "still running" state on return; downloads (phase 3) survive.
            viewModel.onScreenLost()
            engine.cancel()
            webView.destroy()
        }
    }

    LaunchedBind(engine, viewModel)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        BrandHeader()

        // ── URL field ────────────────────────────────────────────────────────
        OutlinedTextField(
            value = state.inputText,
            onValueChange = viewModel::onUrlChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.input_hint), maxLines = 1) },
            leadingIcon = { Icon(Icons.Filled.Link, contentDescription = null) },
            trailingIcon = {
                IconButton(onClick = {
                    clipboard.getText()?.let { viewModel.onUrlChange(it.toString()) }
                }) { Icon(Icons.Filled.ContentPaste, contentDescription = stringResource(R.string.paste)) }
            },
            shape = RoundedCornerShape(14.dp),
            singleLine = true,
        )
        when {
            state.analyzedDocId != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = Color(0xFF10B981),
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    stringResource(R.string.url_recognized, state.analyzedDocId ?: 0L),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
            state.inputText.isNotBlank() -> Text(
                stringResource(R.string.invalid_url),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        // ── Download button — the ONLY manual action after typing (spec §1) ──
        val enabled = state.analyzedDocId != null && !state.engineRunning
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .alpha(if (enabled) 1f else 0.45f)
                .clip(RoundedCornerShape(16.dp))
                .background(SteVeRGradient),
        ) {
            Button(
                onClick = { viewModel.submit() },
                enabled = enabled,
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    contentColor = Color.White,
                ),
            ) {
                Text(stringResource(R.string.download_btn), style = MaterialTheme.typography.labelLarge)
            }
        }

        // ── Error / Saved ────────────────────────────────────────────────────
        if (state.phase == Phase.ERROR) {
            ErrorCard(
                errorKey = state.errorKey,
                retryable = state.retryable,
                onRetry = viewModel::retry,
            )
        }
        if (state.phase == Phase.SAVED) {
            state.lastSavedEntry?.let { entry ->
                SavedCard(
                    entry = entry,
                    onOpen = { FileActions.open(context, entry) },
                    onShare = { FileActions.share(context, entry) },
                )
            }
        }

        // ── Pipeline / engine card ───────────────────────────────────────────
        if (state.phase != Phase.IDLE && state.phase != Phase.ERROR) {
            PipelineCard(
                state = state,
                webView = webView,
                engineRunning = state.engineRunning,
                onCancel = viewModel::cancel,
                onCancelDownload = viewModel::cancelDownload,
            )
        }

        Text(
            stringResource(R.string.credit),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}

@Composable
private fun LaunchedBind(engine: AutopilotEngine, viewModel: DownloadViewModel) {
    androidx.compose.runtime.LaunchedEffect(engine) { viewModel.bindEngine(engine) }
}

@Composable
private fun PipelineCard(
    state: DownloadUiState,
    webView: WebView,
    engineRunning: Boolean,
    onCancel: () -> Unit,
    onCancelDownload: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
    ) {
        Column(Modifier.padding(16.dp)) {
            val current = when (state.phase) {
                Phase.ANALYZE -> 1
                Phase.TRANSFER -> state.engineStep
                Phase.DOWNLOAD -> 3
                Phase.SAVED -> 4
                else -> 1
            }
            StepsHeader(currentStep = current)
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(statusStringRes(state.statusKey)),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(10.dp))
            val total = state.progressTotal
            if (state.phase == Phase.DOWNLOAD && total != null && total > 0) {
                LinearProgressIndicator(
                    progress = { ((state.progressBytes ?: 0L).toFloat() / total).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    formatBytes(state.progressBytes ?: 0) + " / " + formatBytes(total),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            if (state.phase == Phase.TRANSFER) {
                Text(
                    stringResource(R.string.keep_foreground),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (state.phase == Phase.DOWNLOAD) {
                TextButton(onClick = onCancelDownload, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    }
    if (engineRunning) {
        EngineCard(
            engineStep = state.engineStep,
            captchaVisible = state.captchaVisible,
            webView = webView,
            onCancel = onCancel,
        )
    }
}

@Composable
private fun BrandHeader() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(SteVeRGradient),
        ) {
            Text("S", style = MaterialTheme.typography.headlineSmall, color = Color.White)
        }
        Column(Modifier.padding(start = 12.dp)) {
            Text(stringResource(R.string.app_title_big), style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(R.string.app_title_small),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatBytes(bytes: Long): String {
    val kb = bytes / 1024.0
    return when {
        bytes >= 1_000_000 -> String.format("%.1f MB", kb / 1024.0)
        bytes >= 1024 -> String.format("%.0f KB", kb)
        else -> "$bytes B"
    }
}
