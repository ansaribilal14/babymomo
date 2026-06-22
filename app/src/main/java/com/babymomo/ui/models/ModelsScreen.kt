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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babymomo.R
import com.babymomo.data.db.entity.ModelEntity
import com.babymomo.data.db.entity.ModelRuntime
import com.babymomo.data.db.entity.ModelStatus

@Composable
fun ModelsScreen(vm: ModelsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    Column(modifier = Modifier.fillMaxSize()) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Downloadable models",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Pick a model that fits your device's RAM. Models download once and run entirely on-device. v0.2 will wire the inference runtime; for v0.1 the mock brain is used.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (state.models.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.models_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.models, key = { it.id }) { m ->
                    ModelCard(m, isActive = state.activeModel?.id == m.id, vm)
                }
            }
        }
    }
}

@Composable
private fun ModelCard(m: ModelEntity, isActive: Boolean, vm: ModelsViewModel) {
    val downloadState by vm.downloadStateFlow(m.id).collectAsStateWithLifecycle(initialValue = DownloadState.Idle)
    val isMediapipe = m.runtime == ModelRuntime.MEDIAPIPE_GENAI

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = (if (isMediapipe) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary)
                        .copy(alpha = 0.14f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (isMediapipe) Icons.Rounded.Bolt else Icons.Rounded.Memory,
                            contentDescription = null,
                            tint = if (isMediapipe) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        m.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        if (isMediapipe) "MediaPipe GenAI · on-device" else "GGUF · llama.cpp",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (isActive) {
                    AssistChip(
                        onClick = {},
                        label = { Text("Active", fontWeight = FontWeight.SemiBold) },
                        leadingIcon = { Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            leadingIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }

            if (m.description.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    m.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                InfoChip(Icons.Rounded.Download, vm.formatSize(m.sizeBytes))
                InfoChip(Icons.Rounded.Memory, m.quantization)
                InfoChip(Icons.Rounded.Smartphone, "${m.minRamMb / 1024} GB+")
                InfoChip(Icons.Rounded.Bolt, "${m.contextLength / 1000}K ctx")
            }

            Spacer(Modifier.height(12.dp))
            when (m.status) {
                ModelStatus.NOT_DOWNLOADED -> {
                    OutlinedButton(
                        onClick = { vm.downloadModel(m.id) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Download") }
                }
                ModelStatus.DOWNLOADING -> DownloadingRow(m, downloadState, vm)
                ModelStatus.READY -> if (isActive) {
                    OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                        Text("Active")
                    }
                } else {
                    Button(
                        onClick = { vm.activate(m.id) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) { Text("Activate") }
                }
                ModelStatus.ERROR -> ErrorRow(downloadState) {
                    FilledTonalButton(
                        onClick = { vm.downloadModel(m.id) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Retry")
                    }
                }
                ModelStatus.OBSOLETE -> {
                    FilledTonalButton(
                        onClick = { vm.downloadModel(m.id) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Update")
                    }
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
            LinearProgressIndicator(
                progress = { if (determinate) fraction else 0f },
                modifier = Modifier.weight(1f).height(6.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = { vm.cancelDownload(m.id) }) {
                Icon(Icons.Rounded.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Cancel")
            }
        }
        Spacer(Modifier.height(6.dp))
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            val msg = (state as? DownloadState.Failed)?.message
            Text(
                if (!msg.isNullOrBlank()) "Download failed: $msg" else "Download failed",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        Spacer(Modifier.height(8.dp))
        retryButton()
    }
}

@Composable
private fun InfoChip(icon: ImageVector, value: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                value,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
