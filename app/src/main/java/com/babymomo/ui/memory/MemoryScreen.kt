package com.babymomo.ui.memory

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babymomo.R
import com.babymomo.data.db.entity.MemoryEntity
import com.babymomo.data.db.entity.MemoryType
import com.babymomo.ui.theme.Amber
import com.babymomo.ui.theme.MemoryEpisodic
import com.babymomo.ui.theme.MemoryProcedural
import com.babymomo.ui.theme.MemorySemantic
import com.babymomo.ui.theme.MemoryWorking
import com.babymomo.ui.theme.Sage
import com.babymomo.ui.theme.Sky

@Composable
fun MemoryScreen(vm: MemoryViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("Active", state.activeCount.toString(), Amber, Modifier.weight(1f))
            StatCard("Total", state.totalCount.toString(), Sky, Modifier.weight(1f))
            StatCard("Entities", state.entities.size.toString(), Sage, Modifier.weight(1f))
            StatCard("Relations", state.relations.size.toString(), MemoryProcedural, Modifier.weight(1f))
        }

        OutlinedTextField(value = state.searchQuery, onValueChange = vm::setQuery,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            placeholder = { Text("Search memories…") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) }, singleLine = true)

        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = state.selectedType == null, onClick = { vm.setType(null) }, label = { Text("All") })
            MemoryType.values().forEach { t ->
                FilterChip(selected = state.selectedType == t, onClick = { vm.setType(if (state.selectedType == t) null else t) },
                    label = { Text(t.name.lowercase().replaceFirstChar { it.uppercase() }) })
            }
        }

        if (state.memories.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.memory_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.memories) { mem -> MemoryCard(mem) }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium, modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall, color = color, fontWeight = FontWeight.SemiBold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MemoryCard(mem: MemoryEntity) {
    val typeColor = when (mem.type) {
        MemoryType.WORKING -> MemoryWorking
        MemoryType.EPISODIC -> MemoryEpisodic
        MemoryType.SEMANTIC -> MemorySemantic
        MemoryType.PROCEDURAL -> MemoryProcedural
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = typeColor, shape = MaterialTheme.shapes.small, modifier = Modifier.size(10.dp)) {}
                Spacer(Modifier.width(8.dp))
                Text(mem.type.name.lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Text("conf ${(mem.confidence * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(6.dp))
            Text(mem.content, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            if (mem.tags.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text("#${mem.tags.replace(",", " #")}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
