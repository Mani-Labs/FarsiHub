package com.example.farsilandtv.di

import android.content.Context
import com.example.farsilandtv.data.database.AppDatabase
import com.example.farsilandtv.data.database.ContentDatabase
import com.example.farsilandtv.data.database.FavoriteDao
import com.example.farsilandtv.data.database.PlaybackPositionDao
import com.example.farsilandtv.data.database.WatchlistMovieDao
import com.example.farsilandtv.data.database.MonitoredSeriesDao
import com.example.farsilandtv.data.database.EpisodeProgressDao
import com.example.farsilandtv.data.database.SearchHistoryDao
import com.example.farsilandtv.data.database.PlaylistDao
import com.example.farsilandtv.data.database.PlaylistItemDao
import com.example.farsilandtv.data.database.NotificationPreferencesDao
import com.example.farsilandtv.data.database.CachedMovieDao
import com.example.farsilandtv.data.database.CachedSeriesDao
import com.example.farsilandtv.data.database.CachedEpisodeDao
import com.example.farsilandtv.data.database.CachedGenreDao
import com.example.farsilandtv.data.database.CachedVideoUrlDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for database dependencies
 * Provides AppDatabase, ContentDatabase, and all DAOs
 *
 * EXTERNAL AUDIT FIX CD-L2: Added DAO scope documentation
 *
 * DAO Lifecycle & Thread Safety:
 * -----------------------------
 * All DAOs provided by this module are:
 *
 * 1. Singleton-scoped: Tied to the parent database instance lifecycle
 * 2. Thread-safe: Room handles synchronization internally
 * 3. Lightweight: DAOs are interfaces, no heavy objects created
 *
 * Safe to inject anywhere because:
 * - They delegate to the singleton database instance
 * - Room ensures all database operations are thread-safe
 * - No Activity/Fragment context leaks (uses ApplicationContext)
 *
 * Usage:
 * ```kotlin
 * @AndroidEntryPoint
 * class MyActivity : AppCompatActivity() {
 *     @Inject lateinit var favoriteDao: FavoriteDao  // Safe to inject
 * }
 * ```
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    // ============ AppDatabase (User Data - Persistent) ============

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getDatabase(context)
    }

    /**
     * DAO Providers: Thread-safe, singleton-scoped
     * These are lightweight interfaces that delegate to the singleton database
     */
    @Provides
    fun provideWatchlistMovieDao(db: AppDatabase): WatchlistMovieDao = db.watchlistMovieDao()

    @Provides
    fun provideMonitoredSeriesDao(db: AppDatabase): MonitoredSeriesDao = db.monitoredSeriesDao()

    @Provides
    fun provideEpisodeProgressDao(db: AppDatabase): EpisodeProgressDao = db.episodeProgressDao()

    @Provides
    fun provideFavoriteDao(db: AppDatabase): FavoriteDao = db.favoriteDao()

    @Provides
    fun provideSearchHistoryDao(db: AppDatabase): SearchHistoryDao = db.searchHistoryDao()

    @Provides
    fun providePlaylistDao(db: AppDatabase): PlaylistDao = db.playlistDao()

    @Provides
    fun providePlaylistItemDao(db: AppDatabase): PlaylistItemDao = db.playlistItemDao()

    @Provides
    fun provideNotificationPreferencesDao(db: AppDatabase): NotificationPreferencesDao = db.notificationPreferencesDao()

    @Provides
    fun providePlaybackPositionDao(db: AppDatabase): PlaybackPositionDao = db.playbackPositionDao()

    @Provides
    fun provideDownloadDao(db: AppDatabase): com.example.farsilandtv.data.download.DownloadDao = db.downloadDao()

    // ============ ContentDatabase (Content Catalog - Ephemeral) ============

    @Provides
    @Singleton
    fun provideContentDatabase(@ApplicationContext context: Context): ContentDatabase {
        return ContentDatabase.getDatabase(context)
    }

    @Provides
    fun provideCachedMovieDao(db: ContentDatabase): CachedMovieDao = db.movieDao()

    @Provides
    fun provideCachedSeriesDao(db: ContentDatabase): CachedSeriesDao = db.seriesDao()

    @Provides
    fun provideCachedEpisodeDao(db: ContentDatabase): CachedEpisodeDao = db.episodeDao()

    @Provides
    fun provideCachedGenreDao(db: ContentDatabase): CachedGenreDao = db.genreDao()

    @Provides
    fun provideCachedVideoUrlDao(db: ContentDatabase): CachedVideoUrlDao = db.videoUrlDao()
}
