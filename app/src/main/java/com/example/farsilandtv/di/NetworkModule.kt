package com.example.farsilandtv.di

import com.example.farsilandtv.data.api.RetrofitClient
import com.example.farsilandtv.data.api.WordPressApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

/**
 * Hilt module for network dependencies
 *
 * EXTERNAL AUDIT FIX CD-L1: Clarified hybrid DI pattern
 *
 * IMPORTANT: Hybrid Singleton Pattern Explanation
 * -----------------------------------------------
 * This module uses a hybrid approach combining legacy singletons with Hilt:
 *
 * - RetrofitClient: Legacy object singleton with complex custom initialization
 *   (cache setup, interceptors, timeout configuration)
 *
 * - NetworkModule: Provides RetrofitClient's APIs via Hilt for cleaner injection
 *
 * Migration Path:
 * - Short term: Keep existing code working via Hilt providers (current approach)
 * - Long term: Move RetrofitClient initialization into this module using @Provides
 *
 * Why This Works:
 * - Existing code using RetrofitClient.getHttpClient() continues working
 * - New code can inject OkHttpClient via Hilt constructor injection
 * - No breaking changes during gradual migration to full Hilt DI
 *
 * API Services (auto-discovered via @Inject + @Singleton):
 * - IMVBoxApiService: HTML scraping for imvbox.com movies/series/episodes
 * - NamakadeApiService: HTML scraping for Namakade content
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return RetrofitClient.getHttpClient()
    }

    @Provides
    @Singleton
    fun provideWordPressApi(): WordPressApiService {
        return RetrofitClient.wordPressApi
    }
}
