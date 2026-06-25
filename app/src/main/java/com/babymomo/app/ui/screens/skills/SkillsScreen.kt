package com.babymomo.app.ui.screens.skills

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.babymomo.app.core.skills.Skill
import com.babymomo.app.core.skills.SkillRegistry
import com.babymomo.app.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

data class SkillsUiState(val skills: List<Skill> = emptyList())

@HiltViewModel
class SkillsViewModel @Inject constructor(
    private val skillRegistry: SkillRegistry
) : ViewModel() {
    private val _uiState = MutableStateFlow(SkillsUiState(skillRegistry.getAllSkills()))
    val uiState: kotlinx.coroutines.flow.StateFlow<SkillsUiState> = _uiState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillsScreen(navController: NavController, viewModel: SkillsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Skills", color = ElectricTeal) }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MidnightBlack))

        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(uiState.skills, key = { it.name }) { skill ->
                Surface(color = SurfaceNavy, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(skill.name, style = MaterialTheme.typography.titleMedium, color = PureWhite)
                        Text("Triggers: ${skill.triggers.joinToString(", ")}", style = MaterialTheme.typography.bodySmall, color = DimBlue)
                    }
                }
            }
        }
    }
}
