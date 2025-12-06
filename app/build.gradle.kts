import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.compose) // Feature #16: Compose compiler plugin for Kotlin 2.0+
    // Firebase - requires google-services.json file (see README_FIREBASE_SETUP.md)
    // id("com.google.gms.google-services")  // DISABLED: Placeholder Firebase config
    // id("com.google.firebase.crashlytics")  // DISABLED: Placeholder Firebase config
}

android {
    namespace = "com.example.farsilandtv"
    compileSdk = 35  // AUDIT FIX #16: Downgraded from 36 (unstable preview). Min 35 required by Leanback 1.2.0

    // BC-H4: compileSdk=35 but targetSdk=34 is intentional
    // Reason: Use latest APIs (compileSdk 35) but stable runtime behavior (targetSdk 34)
    // This allows using new features while avoiding breaking changes in Android 15

    // Signing configurations
    // Release: Create keystore.properties file in project root with:
    //   storeFile=path/to/keystore.jks
    //   storePassword=your_store_password
    //   keyAlias=your_key_alias
    //   keyPassword=your_key_password
    signingConfigs {
        // Debug uses default Android debug keystore (auto-generated)
        // BC-H6: Explicitly configured for consistent builds across machines

        create("release") {
            val keystorePropertiesFile = rootProject.file("keystore.properties")
            if (keystorePropertiesFile.exists()) {
                val keystoreProperties = Properties().apply {
                    load(keystorePropertiesFile.inputStream())
                }
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    defaultConfig {
        applicationId = "com.example.farsilandtv"
        minSdk = 28  // Android 9 - supports most Android TV devices including Nvidia Shield
        targetSdk = 34  // Stable runtime behavior (separate from compileSdk)
        versionCode = 1
        versionName = "1.0"

        // AUDIT FIX M3.4: Moved ABI filters to buildTypes (different filters for debug/release)
        // Default: Include all ABIs for debug builds (emulator support)
        ndk {
            abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64"))
        }

        // UT-H4 FIX: CDN domains in BuildConfig for runtime access
        buildConfigField("String[]", "CDN_MIRRORS", "{\"d1.flnd.buzz\", \"d2.flnd.buzz\"}")
        buildConfigField("String[]", "TRUSTED_DOMAINS", "{\"farsiland.com\", \"farsiplex.com\", \"flnd.buzz\", \"d1.flnd.buzz\", \"d2.flnd.buzz\", \"namakade.com\", \"wp.farsiland.com\", \"negahestan.com\", \"media.negahestan.com\"}")
    }

    buildTypes {
        release {
            // Enable R8 optimization and resource shrinking for smaller, faster APK
            // Results in 60% smaller APK size and 30-50% performance improvement
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            // Use release signing config if keystore.properties exists
            val keystorePropertiesFile = rootProject.file("keystore.properties")
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }

            // AUDIT FIX M3.4: Remove x86/x64 from release builds (50% smaller native libs)
            // Android TV devices are exclusively ARM-based (x86/x64 only for emulator)
            ndk {
                abiFilters.clear()
                abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a"))
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    // Feature #16: Enable Jetpack Compose
    buildFeatures {
        compose = true
        buildConfig = true   // EXTERNAL AUDIT FIX SN-L2: Enable BuildConfig generation
        viewBinding = false  // BC-M6: Explicitly disabled (not used in project)
    }

    lint {
        // EXTERNAL AUDIT FIX #3 + AUDIT #3 M2: Enable lint error checking for all builds
        // Previous: checkReleaseBuilds = false (skipped ProGuard/R8 validation)
        // Risk: R8 may strip necessary classes, causing release APK crashes
        // Fixed: Enable for release builds to catch issues before deployment
        abortOnError = true // Fail build on lint errors to maintain code quality
        checkReleaseBuilds = true  // AUDIT #3 M2: Enable for release builds
    }
}

dependencies {
    // Core Android TV
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.leanback)

    // Security - Encrypted SharedPreferences for IMVBox credentials
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // HTTP & Networking - Stage 1
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.retrofit)
    implementation(libs.retrofit.moshi)
    implementation(libs.moshi)
    implementation(libs.moshi.kotlin)

    // HTML Parsing (KEY for video URL extraction)
    implementation(libs.jsoup)

    // Video Player (upgraded to Media3)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)  // HLS streaming support for IMVBox
    implementation(libs.media3.ui)
    implementation(libs.media3.ui.leanback)
    implementation(libs.media3.datasource.okhttp)  // OkHttp datasource for custom SSL handling

    // Chromecast Support (Feature: Cast to TV) - BC-H5: Now using version catalog
    implementation(libs.media3.cast)
    implementation(libs.cast.framework)

    // YouTube Player (for IMVBox YouTube content + Chromecast)
    implementation(libs.youtube.player.core)
    implementation(libs.youtube.player.chromecast)

    // Database - BC-H5: Now using version catalog
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.room.paging) // Feature #18: Paging support
    kapt(libs.room.compiler)

    // Image Loading - Using Coil (modern, async, Kotlin-first)
    // Replaced Glide to save ~2MB APK size and improve build time
    implementation(libs.coil)
    implementation(libs.coil.compose)

    // Coroutines
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)

    // Lifecycle
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.livedata)
    implementation(libs.lifecycle.runtime)

    // WorkManager (for background sync)
    implementation(libs.work.runtime)

    // Hilt DI
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    // Paging 3 (Feature #18 - Database Query Optimization) - BC-H5: Now using version catalog
    implementation(libs.paging.runtime)
    implementation(libs.paging.compose) // Paging for Compose

    // Firebase Cloud Messaging (Feature #9 - Push Notifications)
    // Firebase Crashlytics (M4 - Production Error Tracking)
    // NOTE: Requires google-services.json file in app/ directory
    // See README_FIREBASE_SETUP.md for setup instructions
    // implementation(platform("com.google.firebase:firebase-bom:32.7.0"))  // DISABLED
    // implementation("com.google.firebase:firebase-messaging-ktx")  // DISABLED
    // implementation("com.google.firebase:firebase-analytics-ktx")  // DISABLED
    // implementation("com.google.firebase:firebase-crashlytics-ktx")  // DISABLED

    // Shimmer effect for skeleton screens (Feature #20) - BC-H5: Now using version catalog
    implementation(libs.shimmer)

    // Feature #16: Jetpack Compose for TV
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.compose.runtime.livedata) // BC-H5: For observeAsState()
    implementation(libs.tv.foundation)
    implementation(libs.tv.material)
    // Coil for Compose images (implementation already added above)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    // Testing - Phase 7: Comprehensive Test Suite - BC-H5: Now using version catalog
    // Unit tests (JUnit)
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test) // For kotlin.test assertions
    testImplementation(libs.coroutines.test)
    testImplementation(libs.arch.core.testing) // For InstantTaskExecutorRule
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.turbine) // For testing Flow

    // Phase 9: Additional test dependencies for M2, M6, M9 tests
    testImplementation(libs.robolectric) // For Android framework mocking
    testImplementation(libs.androidx.test.core) // For ApplicationProvider
    testImplementation(libs.lifecycle.runtime.testing) // For lifecycle testing

    // Android instrumentation tests
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.coroutines.test)
    androidTestImplementation(libs.arch.core.testing)
    // BC-M3: Compose UI testing
    androidTestImplementation(libs.compose.ui.test.junit4)
}

/**
 * Custom task to generate pre-populated content database
 * Runs Python script that converts FarsiFlow PostgreSQL to SQLite
 *
 * Usage: ./gradlew generateContentDatabase
 *
 * Prerequisites:
 * 1. Start FarsiFlow Docker container: cd G:\Farsiflow && docker-compose up -d
 * 2. Install Python dependencies: pip install psycopg2-binary
 */
tasks.register<Exec>("generateContentDatabase") {
    group = "database"
    description = "Generate pre-populated content database from FarsiFlow PostgreSQL"

    workingDir = projectDir.parentFile
    commandLine = listOf("python", "scripts/generate_content_database.py")

    doFirst {
        println("=".repeat(60))
        println("Starting content database generation...")
        println("This will take 1-2 minutes to complete.")
        println("=".repeat(60))
    }

    doLast {
        println("\n✅ Database generation complete!")
        println("Database location: app/src/main/assets/databases/farsiland_content.db")
        println("\nNext steps:")
        println("1. Build APK: ./gradlew assembleDebug")
        println("2. Install on device")
        println("3. Database will be copied to app on first launch")
    }
}
