package com.babymomo.core.skills

interface Skill {
    val id: String
    val displayName: String
    val description: String
    val triggerKeywords: List<String>
    fun matches(input: String): Boolean
    suspend fun execute(input: String): SkillResult
}

data class SkillResult(val success: Boolean, val output: String, val artifacts: Map<String, String> = emptyMap())
