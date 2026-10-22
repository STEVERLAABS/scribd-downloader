package com.stever.scribdownloader.ui

import android.content.Context
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stever.scribdownloader.R
import com.stever.scribdownloader.data.HistoryEntry
import com.stever.scribdownloader.data.HistoryStore
import com.stever.scribdownloader.download.FileActions
import com.stever.scribdownloader.ui.theme.SteVeRPurple
import kotlinx.coroutines.launch

class LibraryViewModel(app: android.app.Application) : AndroidViewModel(app) {
    private val store = HistoryStore(app)

    var entries: List<HistoryEntry> by mutableStateOf(store.all().sortedByDescending { it.addedAt })
        private set

    fun load() {
        entries = store.all().sortedByDescending { it.addedAt }
    }

    fun remove(entry: HistoryEntry, context: Context, onResult: (FileActions.DeleteOutcome) -> Unit) {
        viewModelScope.launch {
            val outcome = FileActions.delete(context, entry)
            load()
            onResult(outcome)
        }
    }
}

@Composable
fun LibraryScreen(modifier: Modifier = Modifier) {
    val vm: LibraryViewModel = viewModel()
    val context = LocalContext.current
    val entries = vm.entries
    var missingNotice by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.load() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(20.dp),
    ) {
        Text(stringResource(R.string.tab_library), style = MaterialTheme.typography.headlineSmall)

        if (missingNotice) {
            Text(
                stringResource(R.string.delete_missing),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        if (entries.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResource(R.string.library_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(top = 12.dp),
            ) {
                items(entries, key = { it.id }) { entry ->
                    LibraryRow(
                        entry = entry,
                        onOpen = {
                            if (!FileActions.open(context, entry)) {
                                missingNotice = true
                            }
                        },
                        onShare = { FileActions.share(context, entry) },
                        onDelete = {
                            missingNotice = false
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                vm.remove(entry, context) { outcome ->
                                    missingNotice = outcome == FileActions.DeleteOutcome.MISSING_FILE
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryRow(
    entry: HistoryEntry,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .let { it },
            ) {
                Icon(
                    Icons.Filled.PictureAsPdf,
                    contentDescription = null,
                    tint = SteVeRPurple,
                    modifier = Modifier.size(26.dp),
                )
            }
            Column(
                modifier = Modifier
                    .padding(start = 12.dp)
                    .weight(1f),
            ) {
                Text(
                    entry.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                )
                Text(
                    entry.fileName + " — " + metadataLine(entry.sizeBytes, entry.addedAt, entry.completed),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            IconButton(onClick = onOpen) {
                Icon(Icons.Filled.OpenInNew, contentDescription = stringResource(R.string.open))
            }
            IconButton(onClick = onShare) {
                Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.share))
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.delete),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

private fun formatDate(addedAt: Long, completed: Boolean): String {
    val date = java.text.DateFormat.getDateInstance().format(java.util.Date(addedAt))
    return if (completed) date else date + " • ⏳"
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1_000_000 -> String.format("%.1f MB", bytes / 1_048_576.0)
    bytes > 0 -> String.format("%.0f KB", bytes / 1024.0)
    else -> ""
}

private fun metadataLine(sizeBytes: Long, addedAt: Long, completed: Boolean): String =
    listOfNotNull(
        formatSize(sizeBytes).takeIf { it.isNotEmpty() },
        formatDate(addedAt, completed),
    ).joinToString(" — ")
