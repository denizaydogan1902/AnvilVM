package com.anvilvm.app.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import com.anvilvm.app.engine.QemuEngine
import com.anvilvm.app.engine.QemuRuntime
import com.anvilvm.app.engine.PtyBridge
import com.anvilvm.app.engine.VncBridge
import com.anvilvm.app.engine.RiscVSupport
import com.anvilvm.app.engine.avf.AvfDetector
import com.anvilvm.app.engine.avf.KvmAccelerator
import com.anvilvm.app.engine.profiler.PerformanceProfiler
import com.anvilvm.app.engine.profiler.TcgOptimizer
import com.anvilvm.app.engine.snapshot.SnapshotManager
import com.anvilvm.app.data.imagestore.ImageRepository
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

    @Provides
    @Singleton
    fun provideQemuRuntime(@ApplicationContext context: Context): QemuRuntime = QemuRuntime(context)

    @Provides
    @Singleton
    fun provideRiscVSupport(@ApplicationContext context: Context): RiscVSupport = RiscVSupport(context)

    @Provides
    @Singleton
    fun provideAvfDetector(): AvfDetector = AvfDetector()

    @Provides
    @Singleton
    fun provideKvmAccelerator(avfDetector: AvfDetector): KvmAccelerator = KvmAccelerator(avfDetector)

    @Provides
    @Singleton
    fun providePerformanceProfiler(): PerformanceProfiler = PerformanceProfiler()

    @Provides
    @Singleton
    fun provideTcgOptimizer(): TcgOptimizer = TcgOptimizer()

    @Provides
    @Singleton
    fun provideSnapshotManager(@ApplicationContext context: Context): SnapshotManager = SnapshotManager(context)

    @Provides
    @Singleton
    fun provideImageRepository(@ApplicationContext context: Context): ImageRepository = ImageRepository(context)
}
