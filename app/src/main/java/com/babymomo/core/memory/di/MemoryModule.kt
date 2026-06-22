package com.babymomo.core.memory.di

import com.babymomo.core.memory.FlatVectorIndex
import com.babymomo.core.memory.VectorIndex
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class MemoryBindModule {
    @Binds @Singleton
    abstract fun bindVectorIndex(impl: FlatVectorIndex): VectorIndex
}
