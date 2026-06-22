package com.babymomo.core.skills

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SkillRegistry @Inject constructor(private val skills: Set<@JvmSuppressWildcards Skill>) {
    fun all(): List<Skill> = skills.toList()
    fun findSkillForInput(input: String): Skill? = skills.firstOrNull { it.matches(input) }
    fun byId(id: String): Skill? = skills.firstOrNull { it.id == id }
}
