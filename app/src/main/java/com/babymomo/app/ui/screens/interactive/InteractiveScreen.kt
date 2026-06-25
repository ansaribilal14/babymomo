package com.babymomo.app.ui.screens.interactive

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.babymomo.app.core.interactive.WidgetDescriptor
import com.babymomo.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InteractiveScreen(navController: NavController) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Interactive", color = ElectricTeal) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MidnightBlack),
            navigationIcon = {
                TextButton(onClick = { navController.popBackStack() }) {
                    Text("← Back", color = ElectricTeal)
                }
            }
        )

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                Text("Interactive AI Screen", style = MaterialTheme.typography.headlineMedium, color = PureWhite)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Ask Babymomo to generate an interactive screen\n(e.g., 'Show me a quiz about space')", color = DimBlue, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun RenderWidget(widget: WidgetDescriptor, onAction: (String) -> Unit) {
    when (widget) {
        is WidgetDescriptor.BabyText -> {
            val style = when (widget.style) {
                "title" -> MaterialTheme.typography.headlineMedium
                "caption" -> MaterialTheme.typography.labelSmall
                else -> MaterialTheme.typography.bodyMedium
            }
            Text(widget.text, style = style, color = PureWhite)
        }
        is WidgetDescriptor.BabyButton -> {
            Button(onClick = { onAction(widget.actionId) }, colors = ButtonDefaults.buttonColors(containerColor = ElectricTeal, contentColor = MidnightBlack)) {
                Text(widget.label)
            }
        }
        is WidgetDescriptor.BabyList -> {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(widget.items) { item ->
                    Surface(color = ElevatedNavy, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = { item.actionId?.let { onAction(it) } }, modifier = Modifier.fillMaxWidth()) {
                            Text(item.text, color = PureWhite, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
        is WidgetDescriptor.BabyInput -> {
            var value by remember { mutableStateOf("") }
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                placeholder = { Text(widget.hint, color = DimBlue) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ElectricTeal, focusedTextColor = PureWhite, cursorColor = ElectricTeal)
            )
        }
        is WidgetDescriptor.BabyCard -> {
            Surface(color = SurfaceNavy, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(widget.title, style = MaterialTheme.typography.titleMedium, color = PureWhite)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(widget.body, style = MaterialTheme.typography.bodyMedium, color = MutedBlue)
                    widget.children.forEach { child -> RenderWidget(child, onAction) }
                }
            }
        }
        is WidgetDescriptor.BabyGrid -> {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                widget.children.forEach { child ->
                    Box(modifier = Modifier.weight(1f / widget.columns)) {
                        RenderWidget(child, onAction)
                    }
                }
            }
        }
        is WidgetDescriptor.BabyProgress -> {
            Column {
                LinearProgressIndicator(progress = { widget.value.toFloat() / widget.max.toFloat() }, modifier = Modifier.fillMaxWidth(), color = ElectricTeal, trackColor = DividerBlue)
                Spacer(modifier = Modifier.height(4.dp))
                Text(widget.label, style = MaterialTheme.typography.labelSmall, color = DimBlue)
            }
        }
        is WidgetDescriptor.BabyDivider -> {
            HorizontalDivider(color = DividerBlue, modifier = Modifier.padding(vertical = 8.dp))
        }
    }
}
