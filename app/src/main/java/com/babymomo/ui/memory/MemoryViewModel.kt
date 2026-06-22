package com.babymomo.ui.memory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babymomo.data.db.dao.EntityDao
import com.babymomo.data.db.dao.MemoryDao
import com.babymomo.data.db.dao.RelationDao
import com.babymomo.data.db.entity.EntityEntity
import com.babymomo.data.db.entity.MemoryEntity
import com.babymomo.data.db.entity.MemoryType
import com.babymomo.data.db.entity.RelationEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class MemoryUiState(
    val memories: List<MemoryEntity> = emptyList(),
    val entities: List<EntityEntity> = emptyList(),
    val relations: List<RelationEntity> = emptyList(),
    val activeCount: Int = 0, val totalCount: Int = 0,
    val selectedType: MemoryType? = null, val searchQuery: String = ""
)

@HiltViewModel
class MemoryViewModel @Inject constructor(
    private val memoryDao: MemoryDao, private val entityDao: EntityDao, private val relationDao: RelationDao
) : ViewModel() {

    private val _selectedType = MutableStateFlow<MemoryType?>(null)
    private val _searchQuery = MutableStateFlow("")

    val state: StateFlow<MemoryUiState> = combine(
        memoryDao.recentActiveFlow("default", 200), entityDao.allFlow(), relationDao.recentFlow(200),
        memoryDao.activeCountFlow(), memoryDao.totalCountFlow(), _selectedType, _searchQuery
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val memories = values[0] as List<MemoryEntity>
        @Suppress("UNCHECKED_CAST")
        val entities = values[1] as List<EntityEntity>
        @Suppress("UNCHECKED_CAST")
        val relations = values[2] as List<RelationEntity>
        val activeCount = values[3] as Int
        val totalCount = values[4] as Int
        val selectedType = values[5] as MemoryType?
        val query = values[6] as String

        val filtered = memories.filter { m ->
            (selectedType == null || m.type == selectedType) && (query.isBlank() || m.content.contains(query, ignoreCase = true))
        }
        MemoryUiState(memories = filtered, entities = entities, relations = relations,
            activeCount = activeCount, totalCount = totalCount,
            selectedType = selectedType, searchQuery = query)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MemoryUiState())

    fun setType(t: MemoryType?) { _selectedType.value = t }
    fun setQuery(q: String) { _searchQuery.value = q }
}
