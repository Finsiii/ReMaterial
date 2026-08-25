package com.rematerial.app.feature.identity.di

import com.rematerial.app.feature.identity.data.AndroidCoarseLocationResolver
import com.rematerial.app.feature.identity.data.AndroidAccountStore
import com.rematerial.app.feature.identity.data.AndroidSessionStore
import com.rematerial.app.feature.identity.data.AndroidVerificationDocumentStore
import com.rematerial.app.feature.identity.data.DemoIdentityRepository
import com.rematerial.app.feature.identity.domain.IdentityRepository
import com.rematerial.app.feature.identity.domain.AccountStore
import com.rematerial.app.feature.identity.domain.LocationResolver
import com.rematerial.app.feature.identity.domain.SessionStore
import com.rematerial.app.feature.identity.domain.VerificationDocumentStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object IdentityModule {
    @Provides
    @Singleton
    fun provideSessionStore(store: AndroidSessionStore): SessionStore = store

    @Provides
    @Singleton
    fun provideAccountStore(store: AndroidAccountStore): AccountStore = store

    @Provides
    @Singleton
    fun provideLocationResolver(resolver: AndroidCoarseLocationResolver): LocationResolver = resolver

    @Provides
    @Singleton
    fun provideDocumentStore(store: AndroidVerificationDocumentStore): VerificationDocumentStore = store

    @Provides
    @Singleton
    fun provideIdentityRepository(sessions: SessionStore, accounts: AccountStore): IdentityRepository = DemoIdentityRepository(sessions, accounts)
}
