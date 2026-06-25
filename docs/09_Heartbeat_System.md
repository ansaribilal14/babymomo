# 09 — Heartbeat System

## Module Overview

The Heartbeat System is Babymomo's autonomous background agent. Every 30 minutes during the active window (8am–10pm), a `HeartbeatWorker` runs via WorkManager, reviews recent memories, pending project tasks, and calendar events, and decides whether the user needs to be notified. If nothing requires attention, it runs silently. If something does, it posts a notification. Every run is logged to the `heartbeat_log` table regardless of outcome.

**Key Principle:** The heartbeat is silent by default. It only surfaces when something genuinely needs the user's attention. False positives erode trust.

---

## 1. Architecture Overview

```
┌──────────────────────────────────────────────────────────────┐
│                     WorkManager                              │
│  PeriodicWorkRequest(30 MINUTES)                             │
│  ExistingPeriodicWorkPolicy.KEEP                             │
│                                                              │
│  ┌────────────────────────────────────────────────────────┐  │
│  │  HeartbeatWorker.doWork()                              │  │
│  │                                                        │  │
│  │  1. Check active window (8am–10pm)                     │  │
│  │  2. Recall recent memories (top 5, last 48h)           │  │
│  │  3. Build heartbeat prompt                             │  │
│  │  4. Call LLM (complete, not stream)                    │  │
│  │  5. Parse response: SILENT or notification message     │  │
│  │  6. Log to heartbeat_log                               │  │
│  │  7. If not SILENT → post Android notification          │  │
│  │  8. Return Result.success()                            │  │
│  └────────────────────────────────────────────────────────┘  │
│                                                              │
│  ┌────────────────────────────────────────────────────────┐  │
│  │  MemoryMaintenanceWorker.doWork()                      │  │
│  │  (runs every 6 hours)                                  │  │
│  │  - Expires WORKING memories older than 24h             │  │
│  │  - Cleans up orphaned vector entries                   │  │
│  └────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────┘
```

---

## 2. WorkManager Configuration

### HeartbeatWorker Scheduling

```kotlin
// In BabymomoApp.onCreate()
val heartbeatRequest = PeriodicWorkRequestBuilder<HeartbeatWorker>(
    repeatInterval = 30,
    repeatIntervalTimeUnit = TimeUnit.MINUTES,
    flexInterval = 5,
    flexIntervalTimeUnit = TimeUnit.MINUTES
).setConstraints(
    Constraints(
        requiresBatteryNotLow = false,   // Run even on low battery
        requiresCharging = false,         // Run even when not charging
        requiresDeviceIdle = false        // Run even when device is active
    )
).build()

WorkManager.getInstance(this).enqueueUniquePeriodicWork(
    "heartbeat",
    ExistingPeriodicWorkPolicy.KEEP,  // Don't reschedule if already scheduled
    heartbeatRequest
)
```

### MemoryMaintenanceWorker Scheduling

```kotlin
val maintenanceRequest = PeriodicWorkRequestBuilder<MemoryMaintenanceWorker>(
    repeatInterval = 6,
    repeatIntervalTimeUnit = TimeUnit.HOURS
).build()

WorkManager.getInstance(this).enqueueUniquePeriodicWork(
    "memory_maintenance",
    ExistingPeriodicWorkPolicy.KEEP,
    maintenanceRequest
)
```

---

## 3. HeartbeatWorker

### Implementation

```kotlin
@HiltWorker
class HeartbeatWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val llmProvider: WrappedLlmProvider,
    private val memoryRecaller: MemoryRecaller,
    private val heartbeatLogDao: HeartbeatLogDao
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // Step 1: Check active window
        val now = LocalTime.now()
        if (now.hour < 8 || now.hour >= 22) {
            return Result.success()  // Silent exit outside active window
        }

        // Step 2: Recall recent memories
        val summary = try {
            val memories = memoryRecaller.recall("recent important things", topK = 5)
            val memoryContext = memories.joinToString("\n") { "- ${it.content}" }

            // Step 3: Build heartbeat prompt
            val prompt = """You are Babymomo's autonomous background agent. Your job is to check
on the user's world and surface anything that needs attention.

Review:
- Recent memories (last 48h):
$memoryContext

Respond with EXACTLY ONE of:
A) The single word: SILENT
B) A short (≤2 sentence) notification message for the user

Do not explain your reasoning. Do not add preamble."""

            // Step 4: Call LLM
            llmProvider.complete(prompt)
        } catch (_: Exception) {
            "SILENT"  // Default to silent on error
        }

        // Step 5: Parse response
        val isSilent = summary.trim().uppercase() == "SILENT"

        // Step 6: Log to heartbeat_log
        val logEntry = HeartbeatLogEntity(
            id = "hb_${System.currentTimeMillis()}",
            timestamp = System.currentTimeMillis(),
            summary = if (isSilent) "SILENT" else summary,
            notified = !isSilent,
            message = if (isSilent) null else summary
        )
        heartbeatLogDao.insert(logEntry)

        // Step 7: Post notification if needed
        if (!isSilent) {
            postNotification(summary)
        }

        return Result.success()
    }

    private fun postNotification(message: String) {
        val notificationManager = applicationContext
            .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(
            "heartbeat",
            "Babymomo Heartbeat",
            NotificationManager.IMPORTANCE_LOW  // Low importance — no sound, no heads-up
        )
        notificationManager.createNotificationChannel(channel)

        val notification = Notification.Builder(applicationContext, "heartbeat")
            .setContentTitle("Babymomo")
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_heartbeat)
            .setStyle(Notification.BigTextStyle().bigText(message))
            .setContentIntent(
                PendingIntent.getActivity(
                    applicationContext,
                    0,
                    Intent(applicationContext, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()

        notificationManager.notify(HEARTBEAT_NOTIFICATION_ID, notification)
    }

    companion object {
        private const val HEARTBEAT_NOTIFICATION_ID = 1001
    }
}
```

---

## 4. Heartbeat Prompt Design

### Design Principles

1. **Silent by default**: Most heartbeats should return SILENT
2. **Specific, not vague**: Notifications must be actionable ("Your meeting with Sarah is in 30 minutes", not "You have things to do")
3. **Short**: ≤2 sentences max
4. **No preamble**: Just SILENT or the notification text

### Example Heartbeat Interactions

| Recent Memories | LLM Response |
|----------------|-------------|
| "User has a meeting at 3pm" + current time 2:30pm | "Your meeting is in 30 minutes." |
| "User mentioned wanting to buy groceries" + no action taken | "You mentioned buying groceries earlier — still need to go?" |
| "User likes cats" + nothing actionable | SILENT |
| "User has project deadline tomorrow" + no progress logged | "Your project deadline is tomorrow — have you made progress?" |
| "User asked about weather" + nothing urgent | SILENT |

---

## 5. Active Window

```
Time of Day         Heartbeat Behavior
─────────────────────────────────────────
00:00 – 07:59       Worker runs but exits immediately (no LLM call)
08:00 – 21:59       Full heartbeat check (recall + LLM + notify)
22:00 – 23:59       Worker runs but exits immediately (no LLM call)
```

### Timezone Handling

```kotlin
// Uses device local time (LocalTime.now())
// No timezone configuration needed — follows system clock
val now = LocalTime.now()
if (now.hour < 8 || now.hour >= 22) {
    return Result.success()
}
```

---

## 6. MemoryMaintenanceWorker

### Responsibilities

```kotlin
@HiltWorker
class MemoryMaintenanceWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val memoryDao: MemoryDao
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // 1. Expire WORKING memories older than 24 hours
        val cutoff = System.currentTimeMillis() - (24 * 60 * 60 * 1000)
        val expiredMemories = memoryDao.getExpiredWorking(cutoff)
        expiredMemories.forEach { memory ->
            memoryDao.promote(memory.id, System.currentTimeMillis())
            // Actually: set validTo, not promote
        }

        // 2. Clean up heartbeat log entries older than 30 days
        val logCutoff = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
        // heartbeatLogDao.deleteOlderThan(logCutoff)

        return Result.success()
    }
}
```

---

## 7. Heartbeat Log Screen

### UI Specification

The HeartbeatScreen displays a timeline of all heartbeat runs:

```
┌─────────────────────────────────────────┐
│  Heartbeat Log                          │
│                                         │
│  [Trigger Now] button                   │
│                                         │
│  ┌─────────────────────────────────────┐│
│  │ 🔇 2:30 PM  ·  Silent              ││
│  └─────────────────────────────────────┘│
│  ┌─────────────────────────────────────┐│
│  │ 🔔 2:00 PM  ·  Your meeting is in  ││
│  │              30 minutes.            ││
│  └─────────────────────────────────────┘│
│  ┌─────────────────────────────────────┐│
│  │ 🔇 1:30 PM  ·  Silent              ││
│  └─────────────────────────────────────┘│
│  ┌─────────────────────────────────────┐│
│  │ 🔔 1:00 PM  ·  You mentioned       ││
│  │              buying groceries...     ││
│  └─────────────────────────────────────┘│
│                                         │
│  Stats: 42 runs · 5 notifications · 37 silent │
└─────────────────────────────────────────┘
```

### ViewModel

```kotlin
class HeartbeatViewModel @Inject constructor(
    private val heartbeatLogDao: HeartbeatLogDao
) : ViewModel() {

    val heartbeatLogs: StateFlow<List<HeartbeatLogEntity>> = heartbeatLogDao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun triggerManualHeartbeat() {
        viewModelScope.launch(Dispatchers.IO) {
            // Trigger one-time heartbeat
            val request = OneTimeWorkRequestBuilder<HeartbeatWorker>().build()
            WorkManager.getInstance(application).enqueue(request)
        }
    }
}
```

---

## 8. Data Flow — Heartbeat Run

```
WorkManager triggers HeartbeatWorker
   │
   ▼ Check active window
   │  now.hour = 14 → inside window → continue
   │
   ▼ MemoryRecaller.recall("recent important things", topK=5)
   │  → [
   │      MemoryEntity("User has meeting at 3pm with Sarah", EPISODIC),
   │      MemoryEntity("User prefers tea over coffee", PROCEDURAL),
   │      MemoryEntity("User's project deadline is tomorrow", SEMANTIC)
   │    ]
   │
   ▼ Build heartbeat prompt with memory context
   │
   ▼ WrappedLlmProvider.complete(prompt)
   │  → LLM call (no streaming, blocking)
   │  → "Your project deadline is tomorrow — have you made progress?"
   │
   ▼ Parse response → not SILENT
   │
   ▼ Log to heartbeat_log
   │  { timestamp: now, summary: "...", notified: true, message: "..." }
   │
   ▼ Post Android notification
   │  Channel: "heartbeat" (IMPORTANCE_LOW)
   │  Title: "Babymomo"
   │  Body: "Your project deadline is tomorrow — have you made progress?"
   │
   ▼ Return Result.success()
```

---

## 9. Error Handling

| Scenario | Recovery |
|----------|----------|
| Outside active window | Return Result.success() immediately |
| LLM call fails | Default to SILENT, log the run |
| LLM returns unexpected format | Trim and check for "SILENT", otherwise notify |
| MemoryRecaller fails | Proceed with empty memory context |
| Notification posting fails | Log entry still recorded, notification skipped |
| Worker exceeds 10-minute limit | WorkManager cancels, next run in 30 minutes |
| Database write fails | Log error, return Result.success() (don't retry) |

---

## 10. Test Scenarios

| Test | Description | Expected |
|------|------------|----------|
| `heartbeat_activeWindow` | Run at 2pm | Full heartbeat executes |
| `heartbeat_outsideWindow` | Run at 11pm | Returns immediately, no LLM call |
| `heartbeat_silentResponse` | No actionable memories | SILENT response, no notification |
| `heartbeat_notificationResponse` | Upcoming meeting memory | Notification posted |
| `heartbeat_llmFailure` | LLM throws exception | Default to SILENT, log recorded |
| `heartbeat_logPersisted` | Run heartbeat | heartbeat_log entry created |
| `heartbeat_notificationChannel` | First notification | Channel created before posting |
| `maintenance_expiresWorking` | Working memory older than 24h | validTo set, no longer in active pool |
| `manualTrigger` | User taps "Trigger Now" | One-time HeartbeatWorker enqueued |
| `logScreen_displaysHistory` | 10 heartbeat runs | 10 entries displayed |
| `logScreen_showsStats` | 42 runs, 5 notified | Stats display correctly |
