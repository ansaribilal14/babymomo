package com.babymomo.core.kernel

data class RoutingDecision(
    val needMemory: Boolean = true,
    val needPlanning: Boolean = false,
    val needResearch: Boolean = false,
    val needCritic: Boolean = false,
    val needTools: Boolean = false,
    val needInternet: Boolean = false,
    val complexity: Complexity = Complexity.SIMPLE,
    val stepBudget: Int = 1,
    val reason: String = ""
) {
    enum class Complexity { SIMPLE, MODERATE, COMPLEX }
}

class RequestClassifier {
    fun classify(userMessage: String): RoutingDecision {
        val text = userMessage.lowercase().trim()
        val length = text.length
        val wordCount = text.split(Regex("\\s+")).filter { it.isNotBlank() }.size
        val multiSentence = text.split(Regex("[.!?]+")).filter { it.isNotBlank() }.size > 1

        var needPlanning = false; var needResearch = false; var needCritic = false
        var needTools = false; var needInternet = false
        var complexity = RoutingDecision.Complexity.SIMPLE

        val planKeywords = listOf("plan", "roadmap", "strategy", "steps to", "how do i approach", "break down")
        val researchKeywords = listOf("research", "find out", "what's the latest", "look up", "search for", "news on")
        val criticKeywords = listOf("verify", "check if", "is it true", "correct that", "double-check", "validate")
        val toolKeywords = listOf("write", "create", "build", "make", "generate", "draft", "summarize", "analyze")
        val internetKeywords = listOf("today", "this week", "current", "latest", "news", "now", "recent")

        if (planKeywords.any { it in text }) { needPlanning = true; complexity = maxOf(complexity, RoutingDecision.Complexity.MODERATE) }
        if (researchKeywords.any { it in text }) { needResearch = true; needInternet = true; complexity = maxOf(complexity, RoutingDecision.Complexity.MODERATE) }
        if (criticKeywords.any { it in text }) needCritic = true
        if (toolKeywords.any { it in text }) needTools = true
        if (internetKeywords.any { it in text }) needInternet = true

        if (length > 200 || (multiSentence && wordCount > 30)) {
            complexity = maxOf(complexity, RoutingDecision.Complexity.MODERATE)
        }
        if (length > 500 || (multiSentence && wordCount > 80)) {
            complexity = RoutingDecision.Complexity.COMPLEX; needPlanning = true
        }

        val stepBudget = when (complexity) {
            RoutingDecision.Complexity.SIMPLE -> 1
            RoutingDecision.Complexity.MODERATE -> 3
            RoutingDecision.Complexity.COMPLEX -> 6
        }

        val parts = mutableListOf<String>()
        if (needPlanning) parts.add("planning")
        if (needResearch) parts.add("research")
        if (needCritic) parts.add("critic")
        if (needTools) parts.add("tools")
        if (needInternet) parts.add("internet")
        parts.add(complexity.name.lowercase())

        return RoutingDecision(needMemory = true, needPlanning = needPlanning, needResearch = needResearch,
            needCritic = needCritic, needTools = needTools, needInternet = needInternet,
            complexity = complexity, stepBudget = stepBudget, reason = parts.joinToString("+"))
    }
}
