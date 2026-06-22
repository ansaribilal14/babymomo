package com.babymomo.core.skills.di

import com.babymomo.core.skills.AnalyzePdfSkill
import com.babymomo.core.skills.PlanProjectSkill
import com.babymomo.core.skills.Skill
import com.babymomo.core.skills.StudyAssistantSkill
import com.babymomo.core.skills.SummarizeSkill
import com.babymomo.core.skills.WriteArticleSkill
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.ElementsIntoSet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SkillsModule {
    @Provides @Singleton @ElementsIntoSet
    fun provideSkillSet(
        writeArticle: WriteArticleSkill,
        summarize: SummarizeSkill,
        studyAssistant: StudyAssistantSkill,
        planProject: PlanProjectSkill,
        analyzePdf: AnalyzePdfSkill
    ): Set<Skill> = setOf(writeArticle, summarize, studyAssistant, planProject, analyzePdf)
}
