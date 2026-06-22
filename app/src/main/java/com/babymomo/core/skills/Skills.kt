package com.babymomo.core.skills

import com.babymomo.core.llm.LlmGenerationConfig
import com.babymomo.core.llm.LlmMessage
import com.babymomo.core.llm.LlmProvider
import com.babymomo.core.memory.MemoryService
import com.babymomo.core.projects.ProjectService
import javax.inject.Inject

class WriteArticleSkill @Inject constructor(private val llm: LlmProvider, private val memoryService: MemoryService) : Skill {
    override val id = "write_article"
    override val displayName = "Write Article"
    override val description = "Drafts an article on a given topic"
    override val triggerKeywords = listOf("write article", "write a blog", "draft article", "write about")
    override fun matches(input: String): Boolean = triggerKeywords.any { it in input.lowercase() }

    override suspend fun execute(input: String): SkillResult {
        val prompt = """
            Write a well-structured article based on this request.
            Use markdown formatting with a title, intro, 3-5 sections with H2 headers, and a conclusion.

            Request: $input
        """.trimIndent()
        val response = llm.complete(
            listOf(LlmMessage.system("You are a skilled writer. Be engaging and substantive."), LlmMessage.user(prompt)),
            LlmGenerationConfig(temperature = 0.7f, maxTokens = 1500)
        ).getOrNull() ?: return SkillResult(false, "LLM unavailable")
        memoryService.addProceduralMemory("Article draft: $input\n\n$response", confidence = 0.9f, tags = listOf("article", "draft"))
        return SkillResult(true, response.content)
    }
}

class SummarizeSkill @Inject constructor(private val llm: LlmProvider) : Skill {
    override val id = "summarize"
    override val displayName = "Summarize"
    override val description = "Condenses long text into bullet points"
    override val triggerKeywords = listOf("summarize", "summarise", "tldr", "key points of", "main points")
    override fun matches(input: String): Boolean = triggerKeywords.any { it in input.lowercase() }

    override suspend fun execute(input: String): SkillResult {
        val prompt = """
            Summarize the following text into 5-7 key bullet points. Each bullet should be a complete idea.

            Text: $input
        """.trimIndent()
        val response = llm.complete(
            listOf(LlmMessage.system("You are a precise summarizer. Be concise."), LlmMessage.user(prompt)),
            LlmGenerationConfig(temperature = 0.3f, maxTokens = 500)
        ).getOrNull() ?: return SkillResult(false, "LLM unavailable")
        return SkillResult(true, response.content)
    }
}

class StudyAssistantSkill @Inject constructor(private val llm: LlmProvider) : Skill {
    override val id = "study_assistant"
    override val displayName = "Study Assistant"
    override val description = "Turns a topic into flashcards + quiz questions"
    override val triggerKeywords = listOf("study", "flashcards", "quiz me", "learn about", "test me on")
    override fun matches(input: String): Boolean = triggerKeywords.any { it in input.lowercase() }

    override suspend fun execute(input: String): SkillResult {
        val prompt = """
            Create a study guide for this topic. Output:

            ## Flashcards (5)
            - Front: <question> | Back: <answer>

            ## Quiz (5 questions, multiple choice)
            1. <question>
               a) ...
               Answer: <letter>

            ## Key Concepts (3-5 bullet points)

            Topic: $input
        """.trimIndent()
        val response = llm.complete(
            listOf(LlmMessage.system("You are an expert tutor. Create engaging study materials."), LlmMessage.user(prompt)),
            LlmGenerationConfig(temperature = 0.5f, maxTokens = 1200)
        ).getOrNull() ?: return SkillResult(false, "LLM unavailable")
        return SkillResult(true, response.content)
    }
}

class PlanProjectSkill @Inject constructor(private val llm: LlmProvider, private val projectService: ProjectService) : Skill {
    override val id = "plan_project"
    override val displayName = "Plan Project"
    override val description = "Creates a project entity with goals + tasks"
    override val triggerKeywords = listOf("plan project", "start project", "new project", "create project")
    override fun matches(input: String): Boolean = triggerKeywords.any { it in input.lowercase() }

    override suspend fun execute(input: String): SkillResult {
        val prompt = """
            Decompose this project request into:
            1. A project name (short, 2-5 words)
            2. A one-sentence description
            3. 5 initial tasks (each a single sentence)

            Output format:
            NAME: ...
            DESC: ...
            TASKS:
            - ...

            Request: $input
        """.trimIndent()
        val response = llm.complete(
            listOf(LlmMessage.system("You are a project planner. Be concrete and actionable."), LlmMessage.user(prompt)),
            LlmGenerationConfig(temperature = 0.3f, maxTokens = 600)
        ).getOrNull() ?: return SkillResult(false, "LLM unavailable")

        val lines = response.content.lines()
        val name = lines.firstOrNull { it.startsWith("NAME:") }?.removePrefix("NAME:")?.trim() ?: "Untitled Project"
        val desc = lines.firstOrNull { it.startsWith("DESC:") }?.removePrefix("DESC:")?.trim() ?: ""
        val taskStart = lines.indexOfFirst { it.startsWith("TASKS:") }
        val tasks = if (taskStart >= 0) {
            lines.drop(taskStart + 1).takeWhile { it.isNotBlank() }
                .map { it.removePrefix("-").removePrefix("*").trim() }
                .filter { it.isNotBlank() }
        } else emptyList()

        val project = projectService.createProject(name, desc, tasks)
        return SkillResult(success = true,
            output = "Created project '${project.name}' with ${tasks.size} tasks.\n\n${response.content}",
            artifacts = mapOf("projectId" to project.id))
    }
}

class AnalyzePdfSkill @Inject constructor(private val llm: LlmProvider) : Skill {
    override val id = "analyze_pdf"
    override val displayName = "Analyze PDF"
    override val description = "Reads a PDF and extracts key points"
    override val triggerKeywords = listOf("analyze pdf", "read pdf", "summarize pdf", "extract from pdf")
    override fun matches(input: String): Boolean = triggerKeywords.any { it in input.lowercase() }

    override suspend fun execute(input: String): SkillResult {
        return SkillResult(success = true, output = "PDF analysis will be available in v0.2. (Skill matched: $id)")
    }
}
