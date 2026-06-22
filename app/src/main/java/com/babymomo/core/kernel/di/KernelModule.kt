package com.babymomo.core.kernel.di

import com.babymomo.core.kernel.RequestClassifier
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object KernelModule {
    @Provides @Singleton
    fun provideClassifier(): RequestClassifier = RequestClassifier()
}
