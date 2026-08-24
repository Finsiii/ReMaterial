package com.rematerial.app.feature.seller.di

import com.rematerial.app.feature.seller.data.MockSellerRepository
import com.rematerial.app.feature.seller.domain.SellerRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SellerModule {
    @Binds
    @Singleton
    abstract fun bindSellerRepository(repository: MockSellerRepository): SellerRepository
}
