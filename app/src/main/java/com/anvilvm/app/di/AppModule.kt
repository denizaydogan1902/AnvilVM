package com.anvilvm.app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import com.anvilvm.app.engine.QemuEngine
import com.anvilvm.app.engine.PtyBridge
import com.anvilvm.app.engine.VncBridge
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideQemuEngine(): QemuEngine = QemuEngine()

    @Provides
    @Singleton
    fun providePtyBridge(): PtyBridge = PtyBridge()

    @Provides
    @Singleton
    fun provideVncBridge(): VncBridge = VncBridge()
}
