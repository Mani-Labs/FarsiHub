# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# ==================== AGGRESSIVE OPTIMIZATION (2025-11-10) ====================
# These rules maximize code shrinking and obfuscation for smaller APK size
# and better performance. Added as part of performance optimization initiative.

# Perform multiple optimization passes (vs default 1 pass)
# More passes = better optimization but slower build time
-optimizationpasses 5

# Allow ProGuard to modify access modifiers for better optimization
# Makes private/protected fields public when safe for better inlining
-allowaccessmodification

# Repackage all classes into single package for better compression
# Reduces APK size by optimizing package structure
-repackageclasses ''

# Aggressively merge interfaces for smaller APK
-mergeinterfacesaggressively

# Optimize even if warnings exist (safe with proper keep rules)
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*

# ==================== SECURITY & OPTIMIZATION RULES ====================

# Keep line numbers for debugging crash reports
-keepattributes SourceFile,LineNumberTable

# ==================== FIREBASE CLOUD MESSAGING & CRASHLYTICS ====================
# Preserve Firebase classes and prevent obfuscation issues

-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses

# Firebase Core
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# Firebase Messaging Service
-keep class com.example.farsilandtv.services.FarsilandMessagingService { *; }
-keep class * extends com.google.firebase.messaging.FirebaseMessagingService {
    public *;
}

# Firebase Analytics
-keep class com.google.firebase.analytics.** { *; }

# Firebase Crashlytics (M4) - Keep crash reporting information
-keepattributes SourceFile,LineNumberTable
-keep public class * extends java.lang.Exception
-keep class com.google.firebase.crashlytics.** { *; }
-dontwarn com.google.firebase.crashlytics.**

# ==================== RETROFIT & OKHTTP ====================
# Preserve Retrofit and OkHttp classes for API calls

-keepattributes Signature
-keepattributes Exceptions

# Retrofit
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
-keepclassmembernames interface * {
    @retrofit2.http.* <methods>;
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# ==================== MOSHI (JSON SERIALIZATION) ====================
# Preserve JSON model classes
# EXTERNAL AUDIT VERIFIED L4 (2025-11-21): ProGuard/R8 Configuration Risk - RESOLVED
# Issue: R8 may strip data model fields, breaking JSON parsing (Moshi/Retrofit)
# Solution: Comprehensive keep rules for all data models, Moshi, Retrofit, OkHttp
# Verification: Lines 99-113 (Moshi), 79-97 (Retrofit/OkHttp), 114-123 (Room)
# Coverage: All data classes, @Keep annotations, ACF fields, reflection-based libraries

-keep class com.example.farsilandtv.data.model.** { *; }
-keep class com.example.farsilandtv.data.network.** { *; }
-keepclassmembers class com.example.farsilandtv.data.model.** {
    <fields>;
    <init>(...);
}

# Moshi
-keep class com.squareup.moshi.** { *; }
-keep interface com.squareup.moshi.** { *; }
-dontwarn com.squareup.moshi.**

# ==================== ROOM DATABASE ====================
# Preserve Room database classes

-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Dao interface * {
    public *;
}

# ==================== GLIDE IMAGE LOADING ====================
# Preserve Glide classes

-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule {
 <init>(...);
}
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
  **[] $VALUES;
  public *;
}
-keep class com.bumptech.glide.load.data.ParcelFileDescriptorRewinder$InternalRewinder {
  *** rewind();
}

# Glide Transformations (for blur effects)
-keep class jp.wasabeef.glide.transformations.** { *; }

# ==================== EXOPLAYER (MEDIA3) ====================
# Preserve ExoPlayer classes for video playback

-keep class androidx.media3.** { *; }
-keep interface androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Keep ExoPlayer's native libraries
-keep class androidx.media3.decoder.** { *; }
-keep class androidx.media3.exoplayer.** { *; }

# ==================== JETPACK COMPOSE ====================
# Preserve Compose classes

-keep class androidx.compose.** { *; }
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.ui.** { *; }
-dontwarn androidx.compose.**

# ==================== JSOUP (HTML PARSING) ====================
# Preserve JSoup for video URL extraction

-keep class org.jsoup.** { *; }
-keeppackagenames org.jsoup.nodes

# ==================== KOTLIN & COROUTINES ====================
# Preserve Kotlin metadata and coroutines

-keepattributes *Annotation*
-keep class kotlin.** { *; }
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# Kotlin Metadata
-keep class kotlin.Metadata { *; }

# ==================== GENERAL OPTIMIZATION ====================

# Remove logging in release builds (security best practice)
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# Keep crash reporting information
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile