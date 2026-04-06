package com.kenshin.vreader2.di

import android.content.Context
import androidx.room.Room
import com.kenshin.vreader2.data.local.db.VReaderDatabase
import com.google.gson.Gson
import com.kenshin.vreader2.data.local.db.ChapterDao
import com.kenshin.vreader2.data.local.db.MangaDao
import com.kenshin.vreader2.data.repository.MangaRepository
import com.kenshin.vreader2.data.source.SourceManager
import com.kenshin.vreader2.extension.ExtensionLoader
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(
            HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
        )
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header(
                        "User-Agent",
                        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 " +
                                "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                    )
                    .build()
            )
        }
        .build()

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): VReaderDatabase =
        Room.databaseBuilder(context, VReaderDatabase::class.java, "vreader.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideMangaDao(db: VReaderDatabase) = db.mangaDao()

    @Provides
    fun provideChapterDao(db: VReaderDatabase) = db.chapterDao()

    @Provides
    fun provideHistoryDao(db: VReaderDatabase) = db.historyDao()

    @Provides
    @Singleton
    fun provideGson(): Gson = Gson()

    @Provides
    @Singleton
    fun provideSourceManager(
        client: OkHttpClient,
        extensionLoader: ExtensionLoader,
    ): SourceManager = SourceManager(client, extensionLoader)


    @Provides
    @Singleton
    fun provideMangaRepository(
        sourceManager: SourceManager,
        mangaDao: MangaDao,
        chapterDao: ChapterDao,
        gson: Gson,
    ): MangaRepository = MangaRepository(sourceManager, mangaDao, chapterDao, gson)
}

