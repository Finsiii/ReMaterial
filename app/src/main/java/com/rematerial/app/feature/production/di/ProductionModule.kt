package com.rematerial.app.feature.production.di

import com.rematerial.app.feature.production.data.MockProductionRepository
import com.rematerial.app.feature.production.domain.ProductionRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ProductionModule {
    @Provides
    @Singleton
    fun provideProductionRepository(): ProductionRepository = MockProductionRepository()
}
