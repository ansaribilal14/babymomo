package com.babymomo.app.core.skills

import com.babymomo.app.core.llm.WrappedLlmProvider
import javax.inject.Inject
import javax.inject.Singleton

interface Skill {
    val name: String
    val triggers: List<String>
    suspend fun execute(input: String): String
}

@Singleton
class WriteArticleSkill @Inject constructor(
    private val llmProvider: WrappedLlmProvider
) : Skill {
    override val name = "WriteArticle"
    override val triggers = listOf("write article", "write blog", "write about", "draft article")
    override suspend fun execute(input: String): String {
        return llmProvider.complete("Write a well-structured article about: $input")
    }
}

@Singleton
class SummarizeSkill @Inject constructor(
    private val llmProvider: WrappedLlmProvider
) : Skill {
    override val name = "Summarize"
    override val triggers = listOf("summarize", "summary", "tldr", "brief version")
    override suspend fun execute(input: String): String {
        return llmProvider.complete("Provide a concise summary of: $input")
    }
}

@Singleton
class WebSearchSkill @Inject constructor(
    private val llmProvider: WrappedLlmProvider
) : Skill {
    override val name = "WebSearch"
    override val triggers = listOf("search for", "look up", "find online", "web search")
    override suspend fun execute(input: String): String {
        return llmProvider.complete("Based on your knowledge, provide information about: $input. Note: Real web search requires configuration in Settings > Tools.")
    }
}

@Singleton
class CalendarSkill @Inject constructor(
    private val llmProvider: WrappedLlmProvider
) : Skill {
    override val name = "Calendar"
    override val triggers = listOf("calendar", "schedule", "appointment", "meeting")
    override suspend fun execute(input: String): String {
        return llmProvider.complete("Help with calendar management for: $input. Note: Calendar integration requires permissions in Settings.")
    }
}

@Singleton
class ShellSkill @Inject constructor(
    private val llmProvider: WrappedLlmProvider
) : Skill {
    override val name = "Shell"
    override val triggers = listOf("run command", "execute", "shell", "terminal")
    override suspend fun execute(input: String): String {
        return llmProvider.complete("Help with shell command: $input. Note: Linux sandbox needs to be enabled in Settings > Tools.")
    }
}

@Singleton
class PdfSkill @Inject constructor() : Skill {
    override val name = "PDF"
    override val triggers = listOf("pdf", "document analysis")
    override suspend fun execute(input: String): String {
        return "PDF analysis is coming in v1.1. For now, you can paste the text content and I'll help analyze it."
    }
}

@Singleton
class SkillRegistry @Inject constructor(
    private val writeArticle: WriteArticleSkill,
    private val summarize: SummarizeSkill,
    private val webSearch: WebSearchSkill,
    private val calendar: CalendarSkill,
    private val shell: ShellSkill,
    private val pdf: PdfSkill
) {
    private val allSkills: List<Skill> by lazy {
        listOf(writeArticle, summarize, webSearch, calendar, shell, pdf)
    }

    fun matchSkill(input: String): Skill? {
        val lower = input.lowercase()
        return allSkills.firstOrNull { skill ->
            skill.triggers.any { trigger -> lower.contains(trigger) }
        }
    }

    fun getAllSkills(): List<Skill> = allSkills
}
