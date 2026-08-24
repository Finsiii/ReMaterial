package com.rematerial.app.feature.marketplace.di

import com.rematerial.app.feature.marketplace.data.MockMarketplaceRepository
import com.rematerial.app.feature.marketplace.domain.MarketplaceRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class MarketplaceModule {
    @Binds
    @Singleton
    abstract fun bindMarketplaceRepository(repository: MockMarketplaceRepository): MarketplaceRepository
}
