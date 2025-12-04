package com.example.farsilandtv.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt module for repository dependencies
 *
 * NOTE: All repositories and helper classes now use @Inject constructor
 * and are auto-discovered by Hilt. This module is kept for potential
 * future manual bindings but currently provides nothing.
 *
 * Repositories migrated to @Inject:
 * - ContentRepository
 * - FavoritesRepository
 * - PlaybackRepository
 * - PlaylistRepository
 * - SearchRepository
 * - WatchlistRepository
 * - NotificationPreferencesRepository
 *
 * Helper classes migrated to @Inject:
 * - PrefetchManager
 * - ScraperHealthTracker
 * - DownloadManager
 */
@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule
