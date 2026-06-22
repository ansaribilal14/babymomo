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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
            Row {
                when (m.status) {
                    ModelStatus.NOT_DOWNLOADED -> OutlinedButton(onClick = {}, modifier = Modifier.weight(1f)) { Text("Download") }
                    ModelStatus.DOWNLOADING -> OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.weight(1f)) { Text("Downloading…") }
                    ModelStatus.READY -> if (isActive) OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.weight(1f)) { Text("Active") }
                                         else Button(onClick = { vm.activate(m.id) }, modifier = Modifier.weight(1f)) { Text("Activate") }
                    ModelStatus.ERROR -> OutlinedButton(onClick = {}, modifier = Modifier.weight(1f)) { Text("Retry") }
                    ModelStatus.OBSOLETE -> OutlinedButton(onClick = {}, modifier = Modifier.weight(1f)) { Text("Update") }
                }
            }
        }
    }
}

@Composable
private fun InfoChip(label: String, value: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small, modifier = Modifier.padding(end = 6.dp)) {
        Text("$label: $value", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
