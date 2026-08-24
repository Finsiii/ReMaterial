package com.rematerial.app.feature.analysis.di

import com.rematerial.app.feature.analysis.data.MockAiAnalysisGateway
import com.rematerial.app.feature.analysis.domain.AiAnalysisGateway
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AnalysisModule {
    @Provides
    @Singleton
    fun provideAiAnalysisGateway(): AiAnalysisGateway = MockAiAnalysisGateway()
}
