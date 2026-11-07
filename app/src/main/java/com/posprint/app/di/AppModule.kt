package com.posprint.app.di

import android.content.Context
import androidx.room.Room
import com.posprint.app.data.db.AppDatabase
import com.posprint.app.data.db.HistoryDao
import com.posprint.app.data.db.PrinterDao
import com.posprint.app.data.db.TemplateDao
import com.posprint.app.data.render.AndroidRenderAssets
import com.posprint.app.domain.render.RenderAssets
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun database(@ApplicationContext context: Context): AppDatabase =
        // v1 (Phase 1 dev build) only held re-addable printers, so a destructive step is acceptable.
        Room.databaseBuilder(context, AppDatabase::class.java, "posprint.db")
            .fallbackToDestructiveMigrationFrom(1)
            .build()

    @Provides fun printerDao(db: AppDatabase): PrinterDao = db.printerDao()
    @Provides fun templateDao(db: AppDatabase): TemplateDao = db.templateDao()
    @Provides fun historyDao(db: AppDatabase): HistoryDao = db.historyDao()

    @Provides @Singleton @ApplicationScope
    fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {
    @Binds abstract fun renderAssets(impl: AndroidRenderAssets): RenderAssets
}
