# 13 — Projects System

## Module Overview

The Projects System allows users to create named projects with descriptions and task lists. Each project is linked to a node in the knowledge graph, enabling the memory system to associate facts and entities with specific projects. Active projects are injected into the LLM's system prompt via `ProjectContextProvider`, giving Babymomo context-awareness about the user's ongoing work.

**Key Principle:** Projects are first-class citizens. They're not just labels — they're knowledge graph nodes that the memory system can connect to, and they shape every LLM response through prompt injection.

---

## 1. Architecture Overview

```
┌──────────────────────────────────────────────────────────────┐
│                     ProjectsScreen (UI)                       │
│  List of projects · Create new · Project detail · Tasks      │
├──────────────────────────────────────────────────────────────┤
│                     ProjectsViewModel                         │
│  StateFlow<List<ProjectEntity>> · CRUD operations            │
├──────────────────────────────────────────────────────────────┤
│                     ProjectService                            │
│  createProject() · updateProject() · deleteProject()         │
├──────────────────────────────────────────────────────────────┤
│                     ProjectContextProvider                     │
│  getActiveProjectsContext() → List<ProjectContext>            │
│  (consumed by WrappedLlmProvider for system prompt injection)│
├──────────────────────────────────────────────────────────────┤
│                     ProjectDao (Room)                         │
│  CRUD queries · getActive() · Flow observation               │
├──────────────────────────────────────────────────────────────┤
│                     Knowledge Graph Link                      │
│  Each project creates an EntityEntity(type="PROJECT")         │
│  Memories can be linked to projects via entity relations      │
└──────────────────────────────────────────────────────────────┘
```

---

## 2. ProjectService

### Implementation

```kotlin
@Singleton
class ProjectService @Inject constructor(
    private val projectDao: ProjectDao,
    private val memoryGraph: MemoryGraph
) {
    /**
     * Create a new project with name, description, and initial tasks.
     * Also creates a knowledge graph entity for this project.
     */
    suspend fun createProject(
        name: String,
        description: String?,
        tasks: List<String> = emptyList()
    ): ProjectEntity {
        // Create knowledge graph entity
        val graphEntity = memoryGraph.findOrCreateEntity(name, "PROJECT")

        val project = ProjectEntity(
            id = "proj_${System.currentTimeMillis()}_${name.hashCode()}",
            name = name,
            description = description,
            status = "ACTIVE",
            tasks = if (tasks.isNotEmpty()) {
                Json.encodeToString(
                    buildJsonArray { tasks.forEach { add(JsonPrimitive(it)) } }
                )
            } else null,
            graphEntityId = graphEntity.id,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        projectDao.insert(project)
        return project
    }

    /**
     * Update a project's details, tasks, or status.
     */
    suspend fun updateProject(project: ProjectEntity) {
        projectDao.update(project.copy(updatedAt = System.currentTimeMillis()))
    }

    /**
     * Mark a project as completed.
     */
    suspend fun completeProject(id: String) {
        val project = projectDao.getById(id) ?: return
        projectDao.update(project.copy(
            status = "COMPLETED",
            updatedAt = System.currentTimeMillis()
        ))
    }

    /**
     * Archive a project (removes from active context but preserves data).
     */
    suspend fun archiveProject(id: String) {
        val project = projectDao.getById(id) ?: return
        projectDao.update(project.copy(
            status = "ARCHIVED",
            updatedAt = System.currentTimeMillis()
        ))
    }

    /**
     * Permanently delete a project and its associated graph entity.
     */
    suspend fun deleteProject(id: String) {
        val project = projectDao.getById(id) ?: return
        projectDao.delete(id)
        // Note: graph entity preserved — memories may reference it
    }

    /**
     * Add a task to a project's task list.
     */
    suspend fun addTask(projectId: String, task: String) {
        val project = projectDao.getById(projectId) ?: return
        val currentTasks = parseTasks(project.tasks)
        val updatedTasks = currentTasks + task
        projectDao.update(project.copy(
            tasks = serializeTasks(updatedTasks),
            updatedAt = System.currentTimeMillis()
        ))
    }

    /**
     * Remove a task from a project's task list.
     */
    suspend fun removeTask(projectId: String, taskIndex: Int) {
        val project = projectDao.getById(projectId) ?: return
        val currentTasks = parseTasks(project.tasks)
        if (taskIndex in currentTasks.indices) {
            val updatedTasks = currentTasks.toMutableList()
            updatedTasks.removeAt(taskIndex)
            projectDao.update(project.copy(
                tasks = serializeTasks(updatedTasks),
                updatedAt = System.currentTimeMillis()
            ))
        }
    }

    private fun parseTasks(tasksJson: String?): List<String> {
        if (tasksJson == null) return emptyList()
        return try {
            Json.parseToJsonElement(tasksJson).jsonArray.map {
                it.toString().trim('"')
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun serializeTasks(tasks: List<String>): String? {
        if (tasks.isEmpty()) return null
        return Json.encodeToString(
            buildJsonArray { tasks.forEach { add(JsonPrimitive(it)) } }
        )
    }
}
```

---

## 3. ProjectContextProvider

### Implementation

```kotlin
@Singleton
class ProjectContextProvider @Inject constructor(
    private val projectDao: ProjectDao
) {
    data class ProjectContext(
        val name: String,
        val description: String?,
        val tasks: String?
    )

    /**
     * Get context for all ACTIVE projects.
     * This is injected into the LLM system prompt by WrappedLlmProvider.
     */
    suspend fun getActiveProjectsContext(): List<ProjectContext> {
        return projectDao.getActive().map { proj ->
            ProjectContext(proj.name, proj.description, proj.tasks)
        }
    }
}
```

### System Prompt Injection

When `WrappedLlmProvider.buildSystemPrompt()` runs, it includes:

```
[ACTIVE PROJECTS]
- Home Renovation: Planning kitchen remodel, need contractor quotes
  Tasks: Find contractor, Choose countertops, Pick paint colors
- Rust Learning: Working through the book, chapter 5
  Tasks: Finish chapter 5, Build CLI project, Complete exercises
```

---

## 4. Project Entity and Knowledge Graph Link

### Linkage

When a project is created, a corresponding `EntityEntity` is created in the knowledge graph:

```kotlin
val graphEntity = memoryGraph.findOrCreateEntity(name, "PROJECT")
```

This enables:
1. **Memory association**: Memories extracted during project-related conversations can be linked to the project's entity
2. **Graph traversal**: The 4-signal reranker can use graph proximity to boost memories related to the current project
3. **Entity search**: Users can search for project-related entities

### Example Knowledge Graph

```
Entity: "Home Renovation" (PROJECT)
   ├── Relation: HAS_TASK → "Find contractor" (CONCEPT)
   ├── Relation: INVOLVES → "Kitchen" (PLACE)
   └── Relation: OWNED_BY → "User" (PERSON)

Entity: "Rust Learning" (PROJECT)
   ├── Relation: HAS_TASK → "Finish chapter 5" (CONCEPT)
   └── Relation: RELATED_TO → "Programming" (CONCEPT)
```

---

## 5. Project Status Lifecycle

```
ACTIVE ──────► COMPLETED ──────► ARCHIVED
  │                                  ▲
  │         (delete)                 │
  └──────────────────────────────────┘
            (permanent removal)
```

| Status | In Active Context? | UI Display | Graph Entity |
|--------|-------------------|------------|-------------|
| ACTIVE | Yes — injected into system prompt | Green badge, full detail | Preserved |
| COMPLETED | No | Checkmark badge, dimmed | Preserved |
| ARCHIVED | No | Hidden by default, shown in archive | Preserved |

---

## 6. ProjectsScreen — UI Specification

### Project List

```
┌─────────────────────────────────────────┐
│  Projects                         [+]   │
├─────────────────────────────────────────┤
│  ┌─────────────────────────────────┐    │
│  │ 🏠 Home Renovation     ACTIVE  │    │
│  │ Planning kitchen remodel        │    │
│  │ ☐ Find contractor              │    │
│  │ ☐ Choose countertops           │    │
│  │ ☑ Pick paint colors            │    │
│  └─────────────────────────────────┘    │
│                                         │
│  ┌─────────────────────────────────┐    │
│  │ 🦀 Rust Learning       ACTIVE  │    │
│  │ Working through the book        │    │
│  │ ☐ Finish chapter 5             │    │
│  │ ☑ Build CLI project            │    │
│  └─────────────────────────────────┘    │
└─────────────────────────────────────────┘
```

### Create Project Dialog

```
┌─────────────────────────────────────────┐
│  New Project                            │
│                                         │
│  Name:    [________________]            │
│  Description: [________________]        │
│             [________________]          │
│                                         │
│  Initial Tasks:                         │
│  1. [________________]                  │
│  2. [________________]                  │
│  [+ Add Task]                           │
│                                         │
│        [Cancel]    [Create]             │
└─────────────────────────────────────────┘
```

### Project Detail

```
┌─────────────────────────────────────────┐
│  ← Home Renovation               [⋮]   │
├─────────────────────────────────────────┤
│  Status: ACTIVE        [Complete] [⬙]  │
│                                         │
│  Planning kitchen remodel               │
│                                         │
│  ── Tasks ──                            │
│  ☐ Find contractor                      │
│  ☐ Choose countertops                   │
│  ☑ Pick paint colors                    │
│  [+ Add Task]                           │
│                                         │
│  ── Linked Memories ──                  │
│  🧠 User's kitchen is 12x15 feet       │
│  🧠 User prefers granite countertops   │
│                                         │
│  ── Knowledge Graph ──                  │
│  [Kitchen] ──INVOLVES──► [Renovation]  │
│  [User] ──OWNS──► [Renovation]         │
└─────────────────────────────────────────┘
```

---

## 7. ProjectsViewModel

```kotlin
@HiltViewModel
class ProjectsViewModel @Inject constructor(
    private val projectService: ProjectService,
    private val projectDao: ProjectDao
) : ViewModel() {

    val projects: StateFlow<List<ProjectEntity>> = projectDao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _uiState = MutableStateFlow(ProjectsUiState())
    val uiState: StateFlow<ProjectsUiState> = _uiState.asStateFlow()

    fun createProject(name: String, description: String?, tasks: List<String>) {
        viewModelScope.launch {
            projectService.createProject(name, description, tasks)
        }
    }

    fun addTask(projectId: String, task: String) {
        viewModelScope.launch {
            projectService.addTask(projectId, task)
        }
    }

    fun removeTask(projectId: String, taskIndex: Int) {
        viewModelScope.launch {
            projectService.removeTask(projectId, taskIndex)
        }
    }

    fun completeProject(projectId: String) {
        viewModelScope.launch {
            projectService.completeProject(projectId)
        }
    }

    fun archiveProject(projectId: String) {
        viewModelScope.launch {
            projectService.archiveProject(projectId)
        }
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch {
            projectService.deleteProject(projectId)
        }
    }
}

data class ProjectsUiState(
    val isLoading: Boolean = false,
    val error: String? = null
)
```

---

## 8. Data Flow — Project Context in Chat

```
User: "What should I focus on for my kitchen remodel?"
   │
   ▼ MomoKernel.streamProcess()
   │
   ▼ WrappedLlmProvider.buildSystemPrompt()
   │  ├─► [CORE SOUL]
   │  ├─► [PROMOTED MEMORIES]
   │  ├─► [RECALLED MEMORIES]
   │  │      → "User's kitchen is 12x15 feet" [m_abc123]
   │  │      → "User prefers granite countertops" [m_def456]
   │  ├─► [ACTIVE PROJECTS]
   │  │      → "Home Renovation: Planning kitchen remodel
   │  │         Tasks: Find contractor, Choose countertops, Pick paint colors"
   │  └─► [CONTEXT]
   │
   ▼ LLM receives full context, generates response:
   │  "Since you've already picked paint colors [m_abc123], I'd focus on finding
   │   a contractor next. Your 12x15 kitchen is a standard size, so most
   │   contractors should be able to give you a straightforward quote."
   │
   ▼ Response includes project-aware memory citations
```

---

## 9. Error Handling

| Scenario | Recovery |
|----------|----------|
| Project name empty | Reject in UI validation |
| Project name too long (>100 chars) | Truncate in UI |
| Task text empty | Skip empty tasks during creation |
| ProjectDao write fails | Show toast "Failed to save project" |
| Knowledge graph entity creation fails | Project still created, graph link null |
| Active projects query fails | Return empty list to context provider |

---

## 10. Test Scenarios

| Test | Description | Expected |
|------|------------|----------|
| `createProject_basic` | Create "Test Project" | Project persisted with ACTIVE status |
| `createProject_withTasks` | Create project with 3 tasks | Tasks stored as JSON array |
| `createProject_graphEntity` | Create project | EntityEntity created with type=PROJECT |
| `updateProject_description` | Update description | updatedAt timestamp refreshed |
| `completeProject` | Mark project complete | Status = COMPLETED |
| `archiveProject` | Archive project | Status = ARCHIVED, not in active context |
| `deleteProject` | Delete project | Removed from DB, graph entity preserved |
| `addTask` | Add "Buy materials" | Task appended to JSON array |
| `removeTask` | Remove task at index 1 | Task removed from JSON array |
| `getActiveProjects` | 3 active, 1 completed | Only 3 returned |
| `contextProvider_injectsActiveOnly` | 2 active, 1 archived | Only 2 in system prompt |
| `contextProvider_format` | Project with tasks | Formatted as "Name: Description\nTasks: ..." |
| `projectStatus_lifecycle` | Active → Complete → Archive | Each transition works |
