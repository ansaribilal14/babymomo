# 12 — UI/UX Specification

## Module Overview

This document specifies every screen, navigation flow, color palette, typography system, component library, and interaction pattern for the Babymomo Android app. The design is dark-first, with a deep midnight background (#0A0E27) and electric teal accent (#00E5CC). All screens are built with Jetpack Compose using Material 3 theming.

**Key Principle:** Dark-first, minimal, content-focused. Every pixel earns its place.

---

## 1. Color System

### Primary Palette

```kotlin
// Background layers
val MidnightBlack = Color(0xFF0A0E27)    // Main background
val DeepNavy = Color(0xFF111633)         // Card background
val SurfaceNavy = Color(0xFF1A1F3D)      // Elevated surfaces
val ElevatedNavy = Color(0xFF232849)      // Cards, dialogs

// Accent colors
val ElectricTeal = Color(0xFF00E5CC)      // Primary accent, buttons, links
val VividPurple = Color(0xFF7C3AED)       // Secondary accent, badges
val WarmCoral = Color(0xFFFF6B6B)         // Error, destructive actions

// Text hierarchy
val PureWhite = Color(0xFFFFFFFF)         // Primary text, titles
val MutedBlue = Color(0xFFB0B8D4)         // Secondary text, descriptions
val DimBlue = Color(0xFF6B7394)           // Tertiary text, captions

// Utility
val DividerBlue = Color(0xFF2A3050)       // Dividers, borders
val ErrorRed = Color(0xFFFF4D6A)          // Error states
val WarningAmber = Color(0xFFFFB74D)      // Warning states
```

### Memory Type Colors

```kotlin
val WorkingMemoryColor = Color(0xFFFFB74D)    // Amber
val EpisodicMemoryColor = Color(0xFF64B5F6)   // Blue
val SemanticMemoryColor = Color(0xFF00E5CC)    // Teal
val ProceduralMemoryColor = Color(0xFF7C3AED)  // Purple
```

### Chat Bubble Colors

```kotlin
val UserBubbleColor = Color(0xFF1A3A5C)       // User messages
val AiBubbleColor = Color(0xFF1A1F3D)         // AI messages
```

### Usage Rules

| Element | Color | Example |
|---------|-------|---------|
| Screen background | MidnightBlack | All screens |
| Card background | DeepNavy / ElevatedNavy | Memory cards, project cards |
| Primary buttons | ElectricTeal bg + MidnightBlack text | "Send", "Create" |
| Secondary buttons | SurfaceNavy bg + ElectricTeal text | "Cancel", "Back" |
| Text input focus | ElectricTeal border | All text fields |
| Active tab | ElectricTeal underline | Bottom nav, memory tabs |
| Memory type badge | Type color bg + MidnightBlack text | EPISODIC badge |
| Routing reason chip | VividPurple bg + PureWhite text | "PlannerAgent + WebSearch" |
| Error text | ErrorRed | Form validation |
| Success indicator | ElectricTeal | Download complete, memory promoted |

---

## 2. Typography

```kotlin
// Based on Material 3 type scale with custom font weights
val Typography = Typography(
    displayLarge = TextStyle(      // 57sp, Bold — Splash screen
        fontSize = 57.sp, fontWeight = FontWeight.Bold, color = PureWhite),
    headlineLarge = TextStyle(     // 32sp, Bold — Screen titles
        fontSize = 32.sp, fontWeight = FontWeight.Bold, color = PureWhite),
    headlineMedium = TextStyle(    // 28sp, SemiBold — Section headers
        fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = PureWhite),
    titleLarge = TextStyle(        // 22sp, Medium — Card titles
        fontSize = 22.sp, fontWeight = FontWeight.Medium, color = PureWhite),
    titleMedium = TextStyle(       // 16sp, Medium — List items
        fontSize = 16.sp, fontWeight = FontWeight.Medium, color = PureWhite),
    bodyLarge = TextStyle(         // 16sp, Regular — Primary body text
        fontSize = 16.sp, fontWeight = FontWeight.Normal, color = MutedBlue),
    bodyMedium = TextStyle(        // 14sp, Regular — Secondary text
        fontSize = 14.sp, fontWeight = FontWeight.Normal, color = MutedBlue),
    bodySmall = TextStyle(         // 12sp, Regular — Captions, metadata
        fontSize = 12.sp, fontWeight = FontWeight.Normal, color = DimBlue),
    labelLarge = TextStyle(        // 14sp, Medium — Button text
        fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MidnightBlack),
    labelSmall = TextStyle(        // 11sp, Medium — Badges, chips
        fontSize = 11.sp, fontWeight = FontWeight.Medium, color = PureWhite)
)
```

---

## 3. Navigation

### Bottom Navigation Bar

5 top-level destinations:

```
┌──────────┬──────────┬──────────┬──────────┬──────────┐
│   Chat   │  Memory  │ Projects │  Models  │ Settings │
│    💬     │    🧠    │    📋    │    📦    │    ⚙️    │
└──────────┴──────────┴──────────┴──────────┴──────────┘
```

### Navigation Routes

```kotlin
sealed class Route(val route: String) {
    object Chat : Route("chat")
    object Memory : Route("memory")
    object Projects : Route("projects")
    object Models : Route("models")
    object Settings : Route("settings")
    object Heartbeat : Route("heartbeat")
    object Terminal : Route("terminal")
    object Interactive : Route("interactive/{descriptorJson}")
    object Mcp : Route("mcp")
    object ConversationDetail : Route("conversation/{conversationId}")
    object ProjectDetail : Route("project/{projectId}")
    object MemoryDetail : Route("memory/{memoryId}")
}
```

### Navigation Graph

```
ChatScreen ──────────────────────────────────────────────►
   │ (new conversation)
   ├─► ConversationDetail
   │ (interactive screen)
   ├─► InteractiveScreen
   │ (view memories)
   └─► MemoryScreen

MemoryScreen ────────────────────────────────────────────►
   │ (tap memory)
   └─► MemoryDetail

ProjectsScreen ──────────────────────────────────────────►
   │ (tap project)
   └─► ProjectDetail

ModelsScreen (self-contained)

SettingsScreen ──────────────────────────────────────────►
   │ (heartbeat log)
   ├─► HeartbeatScreen
   │ (terminal)
   ├─► TerminalScreen
   │ (MCP servers)
   └─► McpScreen
```

---

## 4. All Screens — Detailed Specs

### 4A. ChatScreen

```
┌─────────────────────────────────────────┐
│ ≡  Babymomo                    ⋮ (menu) │
├─────────────────────────────────────────┤
│                                         │
│  ┌─────────────────────────────────┐    │
│  │ AI bubble                       │    │
│  │ Hello! I'm Babymomo. How can    │    │
│  │ I help you today? [m_abc123]    │    │
│  │                    PlannerAgent │    │
│  └─────────────────────────────────┘    │
│                                         │
│         ┌──────────────────────────┐    │
│         │ User bubble              │    │
│         │ What's the weather?      │    │
│         └──────────────────────────┘    │
│                                         │
│  ┌─────────────────────────────────┐    │
│  │ AI bubble (streaming...)        │    │
│  │ Let me check... ● ● ●          │    │
│  └─────────────────────────────────┘    │
│                                         │
├─────────────────────────────────────────┤
│ 📎  [Type a message...        ]  🎤  ▶ │
└─────────────────────────────────────────┘
```

**Components:**
- Message list: LazyColumn, reverse-scrolled
- Each bubble: role label + content + routing reason chip
- Memory citations `[m_abc]`: tappable chips → MemoryDetail
- Streaming indicator: animated dots
- Input bar: text field + send button + image attach + mic (TTS toggle)
- Overflow menu: new conversation, view memories, export chat

### 4B. MemoryScreen

```
┌─────────────────────────────────────────┐
│  Memory                                 │
├─────────────────────────────────────────┤
│ [🔍 Search memories...               ]  │
├─────────────────────────────────────────┤
│ All │ Working │ Episodic │ Semantic │ Procedural │
│  ✓  │         │          │          │            │
├─────────────────────────────────────────┤
│  ┌─────────────────────────────────┐    │
│  │ Stats: 42 active · 89 total    │    │
│  │ 12 entities · 5 promoted       │    │
│  └─────────────────────────────────┘    │
│                                         │
│  ┌─────────────────────────────────┐    │
│  │ 🧠 User prefers Celsius        │    │
│  │ PROCEDURAL · conf: 0.95 · 5 hits│    │
│  │ ████████████████████░░░ 95%     │    │
│  │ 2 days ago                        │    │
│  └─────────────────────────────────┘    │
│                                         │
│  ┌─────────────────────────────────┐    │
│  │ 📅 User visited Japan last March│    │
│  │ EPISODIC · conf: 0.80 · 3 hits │    │
│  │ ████████████████░░░░░░ 80%      │    │
│  │ 5 days ago                        │    │
│  └─────────────────────────────────┘    │
└─────────────────────────────────────────┘
```

**Components:**
- Tab row: All / Working / Episodic / Semantic / Procedural
- Search bar
- Stats card: active, total, entities, promoted counts
- Memory cards: content, type badge (colored), confidence bar, hitCount, age
- Swipe to delete
- Tap → MemoryDetail

### 4C. ProjectsScreen

```
┌─────────────────────────────────────────┐
│  Projects                         [+]   │
├─────────────────────────────────────────┤
│  ┌─────────────────────────────────┐    │
│  │ 🏠 Home Renovation     ACTIVE  │    │
│  │ Planning kitchen remodel        │    │
│  │ 3/7 tasks complete              │    │
│  └─────────────────────────────────┘    │
│                                         │
│  ┌─────────────────────────────────┐    │
│  │ 🦀 Rust Learning       ACTIVE  │    │
│  │ Working through the book        │    │
│  │ 2/5 tasks complete              │    │
│  └─────────────────────────────────┘    │
│                                         │
│  ┌─────────────────────────────────┐    │
│  │ ✅ Tax Prep 2025      COMPLETED│    │
│  └─────────────────────────────────┘    │
└─────────────────────────────────────────┘
```

### 4D. ModelsScreen

```
┌─────────────────────────────────────────┐
│  Models                                 │
├─────────────────────────────────────────┤
│  ┌─────────────────────────────────┐    │
│  │ Gemma 2B IT          ★ ACTIVE  │    │
│  │ 1.5 GB · LiteRT                 │    │
│  │ ████████████████████ 100%       │    │
│  │ [Deactivate]                    │    │
│  └─────────────────────────────────┘    │
│                                         │
│  ┌─────────────────────────────────┐    │
│  │ Phi-3 Mini 3.8B                │    │
│  │ 2.4 GB · LiteRT                 │    │
│  │ [Download] [Activate]           │    │
│  └─────────────────────────────────┘    │
└─────────────────────────────────────────┘
```

### 4E. SettingsScreen

```
┌─────────────────────────────────────────┐
│  Settings                               │
├─────────────────────────────────────────┤
│  ── AI Providers ──                     │
│  OpenAI: sk-*** · gpt-4o-mini          │
│  NVIDIA NIM: Not configured             │
│  OpenRouter: Not configured             │
│  Provider priority: OpenAI → NIM → OR   │
├─────────────────────────────────────────┤
│  ── On-Device ──                        │
│  Active model: Gemma 2B IT             │
│  [Manage Downloads]                     │
├─────────────────────────────────────────┤
│  ── Privacy ──                          │
│  Internet access: [OFF by default]      │
│  Encrypted storage: Active              │
├─────────────────────────────────────────┤
│  ── Soul ──                             │
│  [Edit system prompt...               ] │
├─────────────────────────────────────────┤
│  ── Tools ──                            │
│  MCP servers: 2 configured              │
│  Linux sandbox: [OFF]                   │
├─────────────────────────────────────────┤
│  ── Backup ──                           │
│  [Export JSON] [Import JSON]            │
├─────────────────────────────────────────┤
│  ── About ──                            │
│  Babymomo v1.0.0 · MIT License         │
└─────────────────────────────────────────┘
```

### 4F. HeartbeatScreen

```
┌─────────────────────────────────────────┐
│  ← Heartbeat Log                       │
│  [Trigger Now]                          │
├─────────────────────────────────────────┤
│  🔔 2:00 PM                            │
│  Your meeting is in 30 minutes.         │
├─────────────────────────────────────────┤
│  🔇 1:30 PM · Silent                   │
├─────────────────────────────────────────┤
│  🔇 1:00 PM · Silent                   │
├─────────────────────────────────────────┤
│  🔔 12:30 PM                           │
│  You mentioned buying groceries...      │
└─────────────────────────────────────────┘
```

### 4G. TerminalScreen

```
┌─────────────────────────────────────────┐
│  ← Terminal            [Clear] [📦 PKG] │
├─────────────────────────────────────────┤
│  $ ls -la                               │
│  total 24                               │
│  drwxr-xr-x 6 root root 4096 ...       │
│  drwxr-xr-x 2 root root 4096 bin       │
│  drwxr-xr-x 2 root root 4096 home      │
│  $ _                                    │
│                                         │
│                                         │
│                                         │
├─────────────────────────────────────────┤
│  $ [Type a command...              ] ▶  │
└─────────────────────────────────────────┘
```

---

## 5. Component Library

### Common Components

| Component | Description | Usage |
|-----------|------------|-------|
| `BabymomoCard` | ElevatedNavy bg, rounded corners, padding | Memory, project, model cards |
| `BabymomoChip` | Small colored label | Memory type, routing reason |
| `BabymomoButton` | ElectricTeal primary, SurfaceNavy secondary | All actions |
| `BabymomoSearchBar` | OutlinedTextField with search icon | Memory search, MCP search |
| `BabymomoProgressBar` | Linear progress with ElectricTeal fill | Model download, confidence |
| `BabymomoBadge` | Small colored circle/pill | Status, type indicator |
| `BabymomoDivider` | DividerBlue horizontal line | Section separators |

### Spacing System

```
4dp  — Tight spacing (icon-to-text)
8dp  — Small spacing (widget gaps)
12dp — Medium spacing (card padding)
16dp — Standard padding (screen margins)
24dp — Large spacing (section gaps)
32dp — Extra large (screen title to content)
```

### Elevation System

```
0dp  — Background (MidnightBlack)
1dp  — Cards (DeepNavy)
2dp  — Elevated cards (SurfaceNavy)
3dp  — Dialogs, bottom sheets (ElevatedNavy)
```

---

## 6. Animations

| Animation | Duration | Easing | Usage |
|-----------|----------|--------|-------|
| Streaming dots | Infinite (300ms cycle) | Linear | Chat streaming indicator |
| Page transition | 300ms | EaseInOut | Navigation |
| Card appear | 200ms | EaseOut | LazyColumn items |
| Chip press | 100ms | Linear | Memory citation tap |
| Progress fill | 500ms | Linear | Model download |

---

## 7. Accessibility

- **TalkBack**: All interactive elements have content descriptions
- **Font scaling**: Typography uses `sp` units, scales with system
- **Contrast**: All text meets WCAG AA contrast ratios
- **Touch targets**: Minimum 48dp × 48dp
- **Focus order**: Logical tab order in forms and lists

---

## 8. Test Scenarios

| Test | Description | Expected |
|------|------------|----------|
| `theme_darkColors` | Verify color constants | All colors match spec |
| `chat_messageBubbles` | Display user and AI messages | Correct colors, alignment |
| `chat_streamingIndicator` | Streaming state | Animated dots visible |
| `chat_memoryCitation` | Tap [m_abc] chip | Navigate to MemoryDetail |
| `memory_typeFilter` | Select "EPISODIC" tab | Only EPISODIC memories shown |
| `memory_searchBar` | Type "Japan" | Filtered results |
| `memory_confidenceBar` | 0.95 confidence | Bar at 95% width |
| `models_downloadProgress` | Download in progress | Progress bar animates |
| `settings_toggleInternet` | Toggle internet switch | Setting updated |
| `settings_exportImport` | Export and import settings | Data preserved |
| `heartbeat_timeline` | 10 entries | Chronological order |
| `terminal_output` | Run "echo hello" | "hello" appears in output |
| `interactive_fullscreen` | Navigate to interactive screen | Title bar + widgets |
| `interactive_inline` | Inline widgets in chat | Embedded in bubble |
| `navigation_bottomBar` | Tap Memory tab | Navigate to MemoryScreen |
