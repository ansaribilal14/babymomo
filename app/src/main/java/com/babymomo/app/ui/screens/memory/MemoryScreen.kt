package com.babymomo.app.ui.screens.memory

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babymomo.app.core.memory.MemoryService
import com.babymomo.app.data.db.dao.MemoryDao
import com.babymomo.app.data.db.entities.MemoryEntity
import com.babymomo.app.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MemoryUiState(
    val memories: List<MemoryEntity> = emptyList(),
    val searchQuery: String = "",
    val selectedTab: Int = 0,
    val activeCount: Int = 0,
    val totalCount: Int = 0,
    val promotedCount: Int = 0
)

@HiltViewModel
class MemoryViewModel @Inject constructor(
    private val memoryDao: MemoryDao,
    private val memoryService: MemoryService
) : ViewModel() {
    private val _uiState = MutableStateFlow(MemoryUiState())
    val uiState: StateFlow<MemoryUiState> = _uiState.asStateFlow()

    init { loadMemories() }

    fun loadMemories() {
        viewModelScope.launch {
            memoryDao.getAllActive().collect { memories ->
                _uiState.update { it.copy(memories = memories) }
            }
        }
        loadStats()
    }

    fun onSearch(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        viewModelScope.launch {
            val results = if (query.isBlank()) {
                memoryDao.getAllActive().first()
            } else {
                memoryDao.search(query)
            }
            _uiState.update { it.copy(memories = results) }
        }
    }

    fun onTabSelected(index: Int) {
        _uiState.update { it.copy(selectedTab = index) }
        val type = when (index) {
            1 -> "WORKING"
            2 -> "EPISODIC"
            3 -> "SEMANTIC"
            4 -> "PROCEDURAL"
            else -> null
        }
        viewModelScope.launch {
            val memories = if (type == null) {
                memoryDao.getAllActive().first()
            } else {
                memoryDao.getByType(type).first()
            }
            _uiState.update { it.copy(memories = memories) }
        }
    }

    fun deleteMemory(id: String) {
        viewModelScope.launch {
            memoryService.deleteMemory(id)
        }
    }

    private fun loadStats() {
        viewModelScope.launch {
            val stats = memoryService.getStats()
            _uiState.update {
                it.copy(
                    activeCount = stats.activeCount,
                    totalCount = stats.totalCount,
                    promotedCount = stats.promotedCount
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryScreen(navController: NavController, viewModel: MemoryViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val tabs = listOf("All", "Working", "Episodic", "Semantic", "Procedural")

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Memory", color = ElectricTeal) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MidnightBlack)
        )

        // Stats card
        Surface(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            color = SurfaceNavy,
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem("Active", uiState.activeCount.toString(), ElectricTeal)
                StatItem("Total", uiState.totalCount.toString(), MutedBlue)
                StatItem("Promoted", uiState.promotedCount.toString(), VividPurple)
            }
        }

        // Search bar
        OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = viewModel::onSearch,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            placeholder = { Text("Search memories...", color = DimBlue) },
            leadingIcon = { Icon(Icons.Filled.Search, null, tint = DimBlue) },
            shape = RoundedCornerShape(24.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ElectricTeal,
                unfocusedBorderColor = DividerBlue,
                focusedTextColor = PureWhite,
                cursorColor = ElectricTeal,
                focusedContainerColor = ElevatedNavy,
                unfocusedContainerColor = ElevatedNavy
            )
        )

        // Tab row
        ScrollableTabRow(
            selectedTabIndex = uiState.selectedTab,
            containerColor = MidnightBlack,
            contentColor = ElectricTeal,
            edgePadding = 16.dp
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = uiState.selectedTab == index,
                    onClick = { viewModel.onTabSelected(index) },
                    text = { Text(title) }
                )
            }
        }

        // Memory list
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(uiState.memories, key = { it.id }) { memory ->
                MemoryCard(memory, onDelete = { viewModel.deleteMemory(memory.id) })
            }
        }
    }
}

@Composable
fun StatItem(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineMedium, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = DimBlue)
    }
}

@Composable
fun MemoryCard(memory: MemoryEntity, onDelete: () -> Unit) {
    val typeColor = when (memory.type) {
        "WORKING" -> WorkingMemoryColor
        "EPISODIC" -> EpisodicMemoryColor
        "SEMANTIC" -> SemanticMemoryColor
        "PROCEDURAL" -> ProceduralMemoryColor
        else -> MutedBlue
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = AiBubbleColor,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Surface(
                    color = typeColor.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = memory.type,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = typeColor
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = memory.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = PureWhite
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Confidence: ${(memory.confidence * 100).toInt()}% · Hits: ${memory.hitCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = DimBlue
                )
            }
            TextButton(onClick = onDelete) {
                Text("Delete", color = ErrorRed, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
