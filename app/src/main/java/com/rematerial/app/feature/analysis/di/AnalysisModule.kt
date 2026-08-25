package com.rematerial.app.feature.analysis.di

import android.content.Context
import androidx.room.Room
import com.rematerial.app.core.media.AnalysisMediaStore
import com.rematerial.app.core.media.FileAnalysisMediaStore
import com.rematerial.app.feature.analysis.data.MockAiAnalysisGateway
import com.rematerial.app.feature.analysis.data.UnconfiguredAiAnalysisGateway
import com.rematerial.app.BuildConfig
import com.rematerial.app.feature.analysis.data.AnalysisDatabase
import com.rematerial.app.feature.analysis.data.RoomAnalysisSessionRepository
import com.rematerial.app.feature.analysis.domain.AiAnalysisGateway
import com.rematerial.app.feature.analysis.domain.AnalysisSessionRepository
import com.rematerial.app.feature.identity.domain.SessionStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json

@Module
@InstallIn(SingletonComponent::class)
object AnalysisModule {
    @Provides
    @Singleton
    fun provideAiAnalysisGateway(): AiAnalysisGateway =
        if (BuildConfig.DEBUG) MockAiAnalysisGateway() else UnconfiguredAiAnalysisGateway()

    @Provides
    @Singleton
    fun provideAnalysisMediaStore(@ApplicationContext context: Context): AnalysisMediaStore =
        FileAnalysisMediaStore(context)

    @Provides
    @Singleton
    fun provideAnalysisDatabase(@ApplicationContext context: Context): AnalysisDatabase =
        Room.databaseBuilder(context, AnalysisDatabase::class.java, "analysis.db").build()

    @Provides
    @Singleton
    fun provideAnalysisSessionRepository(database: AnalysisDatabase, sessions: SessionStore): AnalysisSessionRepository =
        RoomAnalysisSessionRepository(database, Json { ignoreUnknownKeys = false; explicitNulls = false; encodeDefaults = true }, sessions)
}
