package com.rematerial.app.feature.artisan.di

import com.rematerial.app.feature.artisan.data.MockArtisanRepository
import com.rematerial.app.feature.artisan.domain.ArtisanRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ArtisanModule {
    @Provides
    @Singleton
    fun provideArtisanRepository(): ArtisanRepository = MockArtisanRepository()
}
