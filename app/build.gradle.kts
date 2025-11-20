plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.kotlin.compose) // Feature #16: Compose compiler plugin for Kotlin 2.0+
    // Firebase - requires google-services.json file (see README_FIREBASE_SETUP.md)
    // id("com.google.gms.google-services")  // DISABLED: Placeholder Firebase config
    // id("com.google.firebase.crashlytics")  // DISABLED: Placeholder Firebase config
}

android {
    namespace = "com.example.farsilandtv"
    compileSdk = 35  // AUDIT FIX #16: Downgraded from 36 (unstable preview). Min 35 required by Leanback 1.2.0

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
    implementation(libs.media3.ui)
    implementation(libs.media3.ui.leanback)

    // Database
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation("androidx.room:room-paging:2.6.1") // Feature #18: Paging support
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

    // Paging 3 (Feature #18 - Database Query Optimization)
    implementation("androidx.paging:paging-runtime-ktx:3.2.1")
    implementation("androidx.paging:paging-compose:3.2.1") // Paging for Compose

    // Firebase Cloud Messaging (Feature #9 - Push Notifications)
    // Firebase Crashlytics (M4 - Production Error Tracking)
    // NOTE: Requires google-services.json file in app/ directory
    // See README_FIREBASE_SETUP.md for setup instructions
    // implementation(platform("com.google.firebase:firebase-bom:32.7.0"))  // DISABLED
    // implementation("com.google.firebase:firebase-messaging-ktx")  // DISABLED
    // implementation("com.google.firebase:firebase-analytics-ktx")  // DISABLED
    // implementation("com.google.firebase:firebase-crashlytics-ktx")  // DISABLED

    // Shimmer effect for skeleton screens (Feature #20)
    implementation("com.facebook.shimmer:shimmer:0.5.0")

    // Feature #16: Jetpack Compose for TV
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation("androidx.compose.runtime:runtime-livedata:1.5.4") // For observeAsState()
    implementation(libs.tv.foundation)
    implementation(libs.tv.material)
    // Coil for Compose images (implementation already added above)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    // Testing - Phase 7: Comprehensive Test Suite
    // Unit tests (JUnit)
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test:1.9.22") // For kotlin.test assertions
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("androidx.arch.core:core-testing:2.2.0") // For InstantTaskExecutorRule
    testImplementation("org.mockito:mockito-core:5.7.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.1.0")
    testImplementation("app.cash.turbine:turbine:1.0.0") // For testing Flow

    // Phase 9: Additional test dependencies for M2, M6, M9 tests
    testImplementation("org.robolectric:robolectric:4.11.1") // For Android framework mocking
    testImplementation("androidx.test:core:1.5.0") // For ApplicationProvider
    testImplementation("androidx.lifecycle:lifecycle-runtime-testing:2.6.2") // For lifecycle testing

    // Android instrumentation tests
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test:rules:1.5.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.room:room-testing:2.6.1")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    androidTestImplementation("androidx.arch.core:core-testing:2.2.0")
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
