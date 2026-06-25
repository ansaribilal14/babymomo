# 07 — Interactive UI System

## Module Overview

The Interactive UI System allows Babymomo to generate full interactive screens on demand. When a user asks for a quiz, dashboard, recipe, game, or any visual/interactive experience, the LLM produces a JSON `ScreenDescriptor` that is parsed and rendered as native Compose UI. This system works in two modes: **inline** (embedded in the chat bubble) and **fullscreen** (fills the entire viewport as a separate screen). All interactions (button taps, input submissions) are sent back to the LLM, creating fully interactive multi-turn experiences with zero additional app code per use case.

**Key Principle:** The LLM is the developer. It generates the UI, handles interactions, and updates the screen — all through structured JSON.

---

## 1. ScreenDescriptor

### Data Model

```kotlin
@Serializable
data class ScreenDescriptor(
    val mode: String = "inline",       // "inline" | "fullscreen"
    val title: String = "",            // Title bar (fullscreen only)
    val widgets: List<WidgetDescriptor> = emptyList(),
    val actions: Map<String, String> = emptyMap()  // actionId → description
)
```

### Widget Types

```kotlin
@Serializable
sealed class WidgetDescriptor {

    @Serializable
    data class BabyText(
        val text: String,
        val style: String = "body"     // "title" | "body" | "caption"
    ) : WidgetDescriptor()

    @Serializable
    data class BabyButton(
        val label: String,
        val actionId: String
    ) : WidgetDescriptor()

    @Serializable
    data class BabyList(
        val items: List<BabyListItem>
    ) : WidgetDescriptor()

    @Serializable
    data class BabyListItem(
        val text: String,
        val actionId: String? = null
    )

    @Serializable
    data class BabyInput(
        val hint: String,
        val inputId: String
    ) : WidgetDescriptor()

    @Serializable
    data class BabyCard(
        val title: String,
        val body: String,
        val children: List<WidgetDescriptor> = emptyList()
    ) : WidgetDescriptor()

    @Serializable
    data class BabyGrid(
        val columns: Int = 2,
        val children: List<WidgetDescriptor> = emptyList()
    ) : WidgetDescriptor()

    @Serializable
    data class BabyProgress(
        val value: Int,
        val max: Int,
        val label: String = ""
    ) : WidgetDescriptor()

    @Serializable
    data object BabyDivider : WidgetDescriptor()
}
```

---

## 2. JSON Schema — LLM Prompt Specification

The LLM is given this schema when generating interactive screens:

```json
{
  "mode": "inline | fullscreen",
  "title": "Screen title (fullscreen only)",
  "widgets": [
    {"type": "text", "text": "...", "style": "title|body|caption"},
    {"type": "button", "label": "...", "actionId": "..."},
    {"type": "list", "items": [{"text": "...", "actionId": "..."}]},
    {"type": "card", "title": "...", "body": "...", "children": [...]},
    {"type": "input", "hint": "...", "inputId": "..."},
    {"type": "grid", "columns": 2, "children": [...]},
    {"type": "progress", "value": 3, "max": 10, "label": "Step 3 of 10"},
    {"type": "divider"}
  ],
  "actions": {
    "action_id": "Description of what this action means"
  }
}
```

### LLM System Prompt for Interactive Generation

```
You are an interactive screen generator. When the user requests something visual
or interactive (quiz, dashboard, recipe, game, etc.), generate a ScreenDescriptor JSON.

Rules:
1. Respond ONLY with valid JSON. No markdown, no prose, no code fences.
2. Use "inline" mode for small widgets (single quiz question, progress indicator).
3. Use "fullscreen" mode for complex multi-widget screens (dashboards, games, recipes).
4. Every interactive element must have an actionId.
5. The "actions" map describes what each actionId means — you'll receive it back when triggered.
6. Use BabyCard to group related content.
7. Use BabyGrid for layout (2-3 columns).
8. Use BabyProgress for step indicators and completion.
9. For quizzes: each option is a BabyButton with actionId like "answer_a", "answer_b", etc.
10. For recipes: each step is a BabyCard with BabyProgress showing step number.
```

---

## 3. InteractiveScreenParser

### Implementation

```kotlin
class InteractiveScreenParser {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun parse(jsonString: String): ScreenDescriptor? {
        return try {
            val cleaned = jsonString.trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()
            json.decodeFromString<ScreenDescriptor>(cleaned)
        } catch (_: Exception) {
            null
        }
    }
}
```

### Error Handling

| Input | Behavior |
|-------|----------|
| Valid JSON | Parsed to ScreenDescriptor |
| JSON wrapped in code fences | Strips fences, then parses |
| Malformed JSON | Returns null |
| Missing fields | Uses defaults from data class |
| Unknown widget type | `ignoreUnknownKeys = true`, skips unknown |

---

## 4. InteractiveScreenRenderer — Compose Rendering

The renderer converts each `WidgetDescriptor` into native Compose UI components using the Babymomo theme.

```kotlin
@Composable
fun InteractiveScreenRenderer(
    descriptor: ScreenDescriptor,
    onAction: (actionId: String, inputs: Map<String, String>) -> Unit
) {
    when (descriptor.mode) {
        "fullscreen" -> {
            // Full-screen layout with title bar
            Column(modifier = Modifier.fillMaxSize()) {
                // Title bar
                if (descriptor.title.isNotEmpty()) {
                    TopAppBar(
                        title = { Text(descriptor.title) },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = DeepNavy
                        )
                    )
                }
                // Widget list
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(descriptor.widgets) { widget ->
                        RenderWidget(widget, onAction)
                    }
                }
            }
        }
        "inline" -> {
            // Inline layout — no title bar, compact
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                descriptor.widgets.forEach { widget ->
                    RenderWidget(widget, onAction)
                }
            }
        }
    }
}
```

### Widget Renderers

```kotlin
@Composable
fun RenderWidget(
    widget: WidgetDescriptor,
    onAction: (actionId: String, inputs: Map<String, String>) -> Unit
) {
    when (widget) {
        is WidgetDescriptor.BabyText -> {
            Text(
                text = widget.text,
                style = when (widget.style) {
                    "title" -> MaterialTheme.typography.headlineMedium
                    "caption" -> MaterialTheme.typography.bodySmall
                    else -> MaterialTheme.typography.bodyLarge
                },
                color = when (widget.style) {
                    "title" -> PureWhite
                    "caption" -> DimBlue
                    else -> MutedBlue
                }
            )
        }

        is WidgetDescriptor.BabyButton -> {
            Button(
                onClick = { onAction(widget.actionId, emptyMap()) },
                colors = ButtonDefaults.buttonColors(containerColor = ElectricTeal),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(widget.label, color = MidnightBlack)
            }
        }

        is WidgetDescriptor.BabyList -> {
            widget.items.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = item.actionId != null) {
                            item.actionId?.let { onAction(it, emptyMap()) }
                        }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(item.text, color = MutedBlue)
                    if (item.actionId != null) {
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = DimBlue)
                    }
                }
                if (item != widget.items.last()) {
                    HorizontalDivider(color = DividerBlue)
                }
            }
        }

        is WidgetDescriptor.BabyInput -> {
            var value by remember { mutableStateOf("") }
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(widget.hint) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ElectricTeal,
                    unfocusedBorderColor = DimBlue
                )
            )
            // Submit button
            Button(
                onClick = { onAction("submit_${widget.inputId}", mapOf(widget.inputId to value)) },
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Text("Submit")
            }
        }

        is WidgetDescriptor.BabyCard -> {
            Card(
                colors = CardDefaults.cardColors(containerColor = ElevatedNavy),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(widget.title, style = MaterialTheme.typography.titleMedium, color = PureWhite)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(widget.body, color = MutedBlue)
                    widget.children.forEach { child ->
                        Spacer(modifier = Modifier.height(8.dp))
                        RenderWidget(child, onAction)
                    }
                }
            }
        }

        is WidgetDescriptor.BabyGrid -> {
            LazyVerticalGrid(
                columns = GridCells.Fixed(widget.columns),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(widget.children) { child ->
                    RenderWidget(child, onAction)
                }
            }
        }

        is WidgetDescriptor.BabyProgress -> {
            Column {
                if (widget.label.isNotEmpty()) {
                    Text(widget.label, color = MutedBlue, style = MaterialTheme.typography.bodySmall)
                }
                LinearProgressIndicator(
                    progress = { widget.value.toFloat() / widget.max.toFloat() },
                    color = ElectricTeal,
                    trackColor = DividerBlue,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        is WidgetDescriptor.BabyDivider -> {
            HorizontalDivider(color = DividerBlue, modifier = Modifier.padding(vertical = 4.dp))
        }
    }
}
```

---

## 5. Inline vs Fullscreen Modes

### Mode Selection

| Mode | When to Use | Navigation |
|------|------------|------------|
| **Inline** | Small widgets, single quiz question, quick polls, progress indicators | Embedded in chat bubble |
| **Fullscreen** | Dashboards, games, multi-step recipes, brainstorm boards, complex forms | Navigate to InteractiveScreen |

### Mode Decision (LLM-driven)

The LLM decides the mode based on complexity:
- 1-3 widgets → inline
- 4+ widgets or grid layouts → fullscreen
- Multi-step flows → fullscreen

### Inline Rendering in Chat

```kotlin
// In ChatScreen message bubble
if (message.hasInteractiveContent) {
    val descriptor = InteractiveScreenParser().parse(message.content)
    if (descriptor != null && descriptor.mode == "inline") {
        InteractiveScreenRenderer(
            descriptor = descriptor,
            onAction = { actionId, inputs ->
                viewModel.sendInteraction(actionId, inputs)
            }
        )
    }
}
```

### Fullscreen Navigation

```kotlin
// Trigger fullscreen navigation
if (descriptor.mode == "fullscreen") {
    navController.navigate(Route.Interactive(descriptor.toJson()))
}

// InteractiveScreen composable
@Composable
fun InteractiveScreen(
    descriptorJson: String,
    onAction: (actionId: String, inputs: Map<String, String>) -> Unit,
    onBack: () -> Unit
) {
    val descriptor = remember(descriptorJson) {
        InteractiveScreenParser().parse(descriptorJson) ?: ScreenDescriptor()
    }

    InteractiveScreenRenderer(descriptor = descriptor, onAction = onAction)
}
```

---

## 6. Interactive Flow — Multi-Turn

```
User: "Give me a history quiz"
   │
   ▼ RequestClassifier → RouteType.Interactive
   │
   ▼ LLM generates ScreenDescriptor:
   │  {
   │    "mode": "inline",
   │    "widgets": [
   │      {"type": "text", "text": "History Quiz", "style": "title"},
   │      {"type": "text", "text": "In what year did WWII end?", "style": "body"},
   │      {"type": "button", "label": "1943", "actionId": "answer_1943"},
   │      {"type": "button", "label": "1945", "actionId": "answer_1945"},
   │      {"type": "button", "label": "1947", "actionId": "answer_1947"},
   │      {"type": "progress", "value": 1, "max": 5, "label": "Question 1 of 5"}
   │    ],
   │    "actions": {
   │      "answer_1943": "User selected 1943 (wrong)",
   │      "answer_1945": "User selected 1945 (correct)",
   │      "answer_1947": "User selected 1947 (wrong)"
   │    }
   │  }
   │
   ▼ User taps "1945"
   │
   ▼ onAction("answer_1945", emptyMap())
   │
   ▼ ChatViewModel sends: "User tapped action 'answer_1945' (User selected 1945 - correct)"
   │
   ▼ LLM generates next screen:
   │  {
   │    "mode": "inline",
   │    "widgets": [
   │      {"type": "text", "text": "✓ Correct! WWII ended in 1945.", "style": "body"},
   │      {"type": "text", "text": "Who was the first President of the United States?", "style": "body"},
   │      {"type": "button", "label": "Thomas Jefferson", "actionId": "answer_jefferson"},
   │      {"type": "button", "label": "George Washington", "actionId": "answer_washington"},
   │      {"type": "button", "label": "Abraham Lincoln", "actionId": "answer_lincoln"},
   │      {"type": "progress", "value": 2, "max": 5, "label": "Question 2 of 5"}
   │    ]
   │  }
   │
   ▼ (loop continues for all 5 questions)
```

---

## 7. Example Screen Descriptors

### Dashboard (Fullscreen)

```json
{
  "mode": "fullscreen",
  "title": "Weekly Dashboard",
  "widgets": [
    {"type": "text", "text": "Your Week at a Glance", "style": "title"},
    {"type": "progress", "value": 3, "max": 5, "label": "Tasks completed: 3/5"},
    {"type": "card", "title": "Upcoming Events", "body": "2 meetings today", "children": [
      {"type": "list", "items": [
        {"text": "Team standup 9:00 AM", "actionId": "event_standup"},
        {"text": "1:1 with manager 2:00 PM", "actionId": "event_1on1"}
      ]}
    ]},
    {"type": "card", "title": "Memory Highlights", "body": "You mentioned starting a new project", "children": [
      {"type": "button", "label": "View Project Details", "actionId": "view_project"}
    ]}
  ]
}
```

### Recipe Card (Inline)

```json
{
  "mode": "inline",
  "widgets": [
    {"type": "text", "text": "Pasta Carbonara", "style": "title"},
    {"type": "progress", "value": 1, "max": 4, "label": "Step 1 of 4"},
    {"type": "text", "text": "Cook spaghetti in salted boiling water until al dente.", "style": "body"},
    {"type": "button", "label": "Next Step →", "actionId": "next_step"},
    {"type": "button", "label": "View All Steps", "actionId": "view_all"}
  ]
}
```

---

## 8. Error Handling

| Scenario | Recovery |
|----------|----------|
| LLM returns invalid JSON | InteractiveScreenParser returns null, show error text |
| JSON wrapped in code fences | Parser strips fences automatically |
| Unknown widget type | Skipped (ignoreUnknownKeys=true) |
| Missing required fields | Data class defaults used |
| Fullscreen navigation fails | Fall back to inline rendering |
| Action handler fails | Show toast "Interaction failed" |
| ScreenDescriptor too large | Truncate widgets list to 50 items |

---

## 9. Test Scenarios

| Test | Description | Expected |
|------|------------|----------|
| `parse_validJson` | Valid ScreenDescriptor JSON | Parsed correctly |
| `parse_codeFencedJson` | JSON wrapped in ```json``` | Fences stripped, parsed |
| `parse_invalidJson` | Malformed JSON | Returns null |
| `parse_missingFields` | JSON with missing optional fields | Defaults applied |
| `render_allWidgets` | ScreenDescriptor with all 8 widget types | All render without crash |
| `render_buttonAction` | Tap BabyButton | onAction called with correct actionId |
| `render_listItemAction` | Tap BabyListItem with actionId | onAction called |
| `render_inputSubmit` | Type text and submit | onAction called with input values |
| `inline_inChat` | Inline descriptor in chat message | Embedded in bubble |
| `fullscreen_navigation` | Fullscreen descriptor | Navigate to InteractiveScreen |
| `multiTurn_quiz` | 5-question quiz flow | 5 sequential screens generated |
| `mode_inlineDecision` | 2-widget descriptor | mode=inline |
| `mode_fullscreenDecision` | 8-widget descriptor | mode=fullscreen |
