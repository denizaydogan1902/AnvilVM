package com.anvilvm.app.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    // All dependencies use constructor injection (@Inject constructor) with @Singleton.
    // Hilt discovers them automatically — no @Provides methods needed.
}
