package com.babymomo.ui.models

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babymomo.R
import com.babymomo.data.db.entity.ModelEntity
import com.babymomo.data.db.entity.ModelStatus

@Composable
fun ModelsScreen(vm: ModelsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    Column(modifier = Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Downloadable models", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text("Pick a model that fits your device's RAM. Models download once and run entirely on-device. v0.2 will wire the inference runtime; for v0.1 the mock brain is used.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (state.models.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) { Text(stringResource(R.string.models_empty)) }
        } else {
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.models) { m -> ModelCard(m, isActive = state.activeModel?.id == m.id, vm) }
            }
        }
    }
}

@Composable
private fun ModelCard(m: ModelEntity, isActive: Boolean, vm: ModelsViewModel) {
    // Always observe this model's download state. Collecting is cheap: when no work has ever
    // been enqueued for this id the underlying WorkManager Flow emits an empty list immediately.
    val downloadState by vm.downloadStateFlow(m.id).collectAsStateWithLifecycle(initialValue = DownloadState.Idle)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(m.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (isActive) AssistChip(onClick = {}, label = { Text("Active") })
            }
            Spacer(Modifier.height(4.dp))
            Text(m.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Row {
                InfoChip("Size", vm.formatSize(m.sizeBytes))
                InfoChip("Quant", m.quantization)
                InfoChip("RAM", "${m.minRamMb / 1024} GB+")
                InfoChip("Ctx", "${m.contextLength / 1000}K")
            }
            Spacer(Modifier.height(8.dp))
            when (m.status) {
                ModelStatus.NOT_DOWNLOADED -> {
                    OutlinedButton(onClick = { vm.downloadModel(m.id) }, modifier = Modifier.fillMaxWidth()) { Text("Download") }
                }
                ModelStatus.DOWNLOADING -> DownloadingRow(m, downloadState, vm)
                ModelStatus.READY -> if (isActive) OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) { Text("Active") }
                                     else Button(onClick = { vm.activate(m.id) }, modifier = Modifier.fillMaxWidth()) { Text("Activate") }
                ModelStatus.ERROR -> ErrorRow(downloadState) {
                    OutlinedButton(onClick = { vm.downloadModel(m.id) }, modifier = Modifier.fillMaxWidth()) { Text("Retry") }
                }
                ModelStatus.OBSOLETE -> {
                    OutlinedButton(onClick = { vm.downloadModel(m.id) }, modifier = Modifier.fillMaxWidth()) { Text("Update") }
                }
            }
        }
    }
}

@Composable
private fun DownloadingRow(m: ModelEntity, state: DownloadState, vm: ModelsViewModel) {
    val bytes = (state as? DownloadState.Downloading)?.bytesDownloaded ?: 0L
    val total = (state as? DownloadState.Downloading)?.totalBytes?.takeIf { it > 0 } ?: m.sizeBytes
    val isVerifying = state is DownloadState.Verifying
    val determinate = total > 0
    val fraction = if (determinate) (bytes.toFloat() / total).coerceIn(0f, 1f) else 0f
    val pct = if (determinate) (bytes * 100 / total).toInt() else 0

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (determinate) {
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.weight(1f)
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = { vm.cancelDownload(m.id) }) { Text("Cancel") }
        }
        Spacer(Modifier.height(4.dp))
        val statusText = when {
            isVerifying -> "Verifying integrity…"
            !determinate -> "Connecting…"
            else -> "$pct%  ·  ${vm.formatSize(bytes)} / ${vm.formatSize(total)}"
        }
        Text(
            statusText,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ErrorRow(state: DownloadState, retryButton: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        val msg = (state as? DownloadState.Failed)?.message
        if (!msg.isNullOrBlank()) {
            Text(
                "Download failed: $msg",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.height(4.dp))
        }
        retryButton()
    }
}

@Composable
private fun InfoChip(label: String, value: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small, modifier = Modifier.padding(end = 6.dp)) {
        Text("$label: $value", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
