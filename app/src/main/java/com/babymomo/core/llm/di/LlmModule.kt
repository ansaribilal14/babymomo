package com.babymomo.core.llm.di

import com.babymomo.core.llm.LlmProvider
import com.babymomo.core.llm.LlmProviderChain
import com.babymomo.core.llm.LocalLlmProvider
import com.babymomo.core.llm.MockLlmProvider
import com.babymomo.core.llm.RemoteLlmProvider
import com.squareup.moshi.Moshi
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier @Retention(AnnotationRetention.BINARY) annotation class DefaultLlm
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class LocalLlm
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class RemoteLlm
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class MockLlm

@Module
@InstallIn(SingletonComponent::class)
object LlmModule {
    @Provides @Singleton
    fun provideOkHttp(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        .build()

    @Provides @Singleton
    fun provideMoshi(): Moshi = Moshi.Builder().build()

    @Provides @Singleton @DefaultLlm
    fun provideDefaultProvider(chain: LlmProviderChain): LlmProvider = chain

    @Provides @Singleton @LocalLlm
    fun provideLocalProvider(p: LocalLlmProvider): LlmProvider = p

    @Provides @Singleton @RemoteLlm
    fun provideRemoteProvider(p: RemoteLlmProvider): LlmProvider = p

    @Provides @Singleton @MockLlm
    fun provideMockProvider(p: MockLlmProvider): LlmProvider = p
}

@Module
@InstallIn(SingletonComponent::class)
abstract class LlmBindModule {
    @Binds @Singleton
    abstract fun bindChain(chain: LlmProviderChain): LlmProvider
}
