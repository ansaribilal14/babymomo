package com.babymomo.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * BABYMOMO — "warm companion" palette.
 *
 * Built around a warm cream/amber base with sage, sky, and rust accents for memory-type
 * categorisation. Light and dark variants are designed independently (dark is not a simple
 * inversion) so that text/surface contrast stays accessible (>= 4.5:1 on body text).
 *
 * Light:
 *  - background  Cream 0xFFFFF8F1   (very warm off-white)
 *  - surface     CreamSurface 0xFFFAF1E5  (subtle elevation tint)
 *  - onSurface   Espresso 0xFF2A1F18   (~14:1 on background — accessible)
 *  - onSurfaceVariant  Mocha 0xFF6B5A4C  (~7:1 on background — accessible)
 *  - primary     Amber 0xFFC2691F  (deeper than v1's 0xFFD97F3F for WCAG AA on white)
 *
 * Dark:
 *  - background  Charcoal 0xFF171210  (warm near-black, not pure black)
 *  - surface     CharcoalSurface 0xFF1F1714  (1 step lifted)
 *  - onSurface   CreamText 0xFFF5EBDE  (~14:1 on background)
 *  - primary     AmberDark 0xFFE89A5A  (lifted for dark contrast on charcoal)
 */

// ---- LIGHT: warm cream/amber ----------------------------------------------
val Cream = Color(0xFFFFF8F1)
val CreamSurface = Color(0xFFFAF1E5)
val CreamSurfaceHigh = Color(0xFFF5E9D6) // higher tonal elevation
val CardLight = Color(0xFFFFFCF7)
val DividerLight = Color(0xFFE8DCC8)
val TextPrimaryLight = Color(0xFF2A1F18)   // on Cream -> ~14:1
val TextSecondaryLight = Color(0xFF5F5042)  // on Cream -> ~7:1 (was 6B5A4C, deepened for AA)
val TextTertiaryLight = Color(0xFF8A7866)   // on Cream -> ~3.5:1 (large/labels only)

val Amber = Color(0xFFC2691F)               // primary, AA on white
val AmberDeep = Color(0xFFA55515)           // pressed/strong
val AmberSoft = Color(0xFFF6D9B8)           // primaryContainer (light)
val AmberOnContainer = Color(0xFF3A1E04)    // onPrimaryContainer (deep brown)

val Sage = Color(0xFF5B7A4F)                // secondary, AA on white
val SageSoft = Color(0xFFDDE7CB)
val Sky = Color(0xFF3F6485)                 // tertiary, AA on white
val SkySoft = Color(0xFFD2E2EF)
val Rust = Color(0xFFB0492E)                // error-ish accent
val RustSoft = Color(0xFFF4D5C9)

// ---- DARK: warm charcoal --------------------------------------------------
val Charcoal = Color(0xFF171210)
val CharcoalSurface = Color(0xFF1F1714)
val CharcoalSurfaceHigh = Color(0xFF2A201A)
val CardDark = Color(0xFF261D17)
val DividerDark = Color(0xFF3B2E25)
val TextPrimaryDark = Color(0xFFF5EBDE)     // on Charcoal -> ~14:1
val TextSecondaryDark = Color(0xFFC9B59E)   // on Charcoal -> ~9:1
val TextTertiaryDark = Color(0xFF8E7A66)

val AmberDark = Color(0xFFE89A5A)           // primary in dark (lifted)
val AmberDeepDark = Color(0xFFF2B581)
val AmberSoftDark = Color(0xFF5A3A1E)       // primaryContainer in dark
val AmberOnContainerDark = Color(0xFFFAD9B6)
val SageDark = Color(0xFFA4BD96)
val SkyDark = Color(0xFFA5C3DA)
val RustDark = Color(0xFFE08366)

// ---- Semantic: memory types (consistent across themes) --------------------
val MemoryWorking = Color(0xFFC2691F)
val MemoryEpisodic = Color(0xFF3F6485)
val MemorySemantic = Color(0xFF5B7A4F)
val MemoryProcedural = Color(0xFFB0492E)

val MemoryWorkingDark = Color(0xFFE89A5A)
val MemoryEpisodicDark = Color(0xFFA5C3DA)
val MemorySemanticDark = Color(0xFFA4BD96)
val MemoryProceduralDark = Color(0xFFE08366)
