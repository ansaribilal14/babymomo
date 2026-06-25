package com.babymomo.app.ui.screens.heartbeat

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
import com.babymomo.app.data.db.dao.HeartbeatLogDao
import com.babymomo.app.data.db.entities.HeartbeatLogEntity
import com.babymomo.app.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class HeartbeatUiState(val logs: List<HeartbeatLogEntity> = emptyList())

@HiltViewModel
class HeartbeatViewModel @Inject constructor(
    private val heartbeatLogDao: HeartbeatLogDao
) : ViewModel() {
    private val _uiState = MutableStateFlow(HeartbeatUiState())
    val uiState: StateFlow<HeartbeatUiState> = _uiState.asStateFlow()

    init {
        kotlinx.coroutines.MainScope().launch {
            heartbeatLogDao.getAll().collect { logs ->
                _uiState.update { it.copy(logs = logs) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeartbeatScreen(navController: NavController, viewModel: HeartbeatViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Heartbeat", color = ElectricTeal) }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MidnightBlack))

        if (uiState.logs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                    Text("No heartbeat logs yet", color = DimBlue)
                    Text("Babymomo checks on you every 30 minutes (8am-10pm)", color = DimBlue, style = MaterialTheme.typography.bodySmall)
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(uiState.logs, key = { it.id }) { log ->
                    Surface(color = SurfaceNavy, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text(java.text.SimpleDateFormat("HH:mm · MMM dd").format(log.timestamp), style = MaterialTheme.typography.labelSmall, color = DimBlue)
                                Surface(color = if (log.notified) WarningAmber.copy(alpha = 0.2f) else ElectricTeal.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                                    Text(if (log.notified) "Notified" else "Silent", modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = if (log.notified) WarningAmber else ElectricTeal)
                                }
                            }
                            if (log.message != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(log.message, style = MaterialTheme.typography.bodyMedium, color = PureWhite)
                            }
                        }
                    }
                }
            }
        }
    }
}
