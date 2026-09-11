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

# ---------------------------------------------------------------------------
# Media Master — R8/minify keep rules.
# NOTE: isMinifyEnabled is currently false; these rules are staged for the
# release-hardening phase where shrinking + obfuscation are enabled.
# ---------------------------------------------------------------------------

# Keep source/line info for readable crash reports (obfuscated).
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod

# --- kotlinx.serialization (type-safe navigation routes + models) ---
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.example.**$$serializer { *; }
-keepclassmembers class com.example.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class com.example.** {
    @kotlinx.serialization.Serializable <methods>;
}

# --- Kotlin metadata / coroutines ---
-keepclassmembers class kotlin.Metadata { *; }
-dontwarn kotlinx.coroutines.**

# --- AndroidX Media3 (ExoPlayer / Transformer / session) ---
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# --- ML Kit (text recognition, document scanner) ---
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_** { *; }
-dontwarn com.google.mlkit.**

# --- OkHttp (WebDAV network storage; Moshi/Retrofit removed in v1.1.0, unused) ---
-dontwarn okhttp3.**
-dontwarn okio.**

# --- Coil ---
-dontwarn coil.**

# --- smbj (SMB) + BouncyCastle + slf4j (network storage) ---
-keep class com.hierynomus.** { *; }
-keep class net.engio.mbassy.** { *; }
-keep class org.bouncycastle.** { *; }
-dontwarn com.hierynomus.**
-dontwarn net.engio.mbassy.**
-dontwarn org.bouncycastle.**
-dontwarn org.slf4j.**
-dontwarn javax.annotation.**
-dontwarn com.google.errorprone.annotations.**

# --- androidx.security (EncryptedSharedPreferences / Tink) ---
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**

# --- WorkManager's internal Room database (backup scheduling) ---
# CRITICAL: without these, R8 breaks WorkManager's reflective instantiation of
# its generated Room `_Impl` class and the app crashes on *every* launch with
# "Failed to create an instance of class ...WorkDatabase..." — before any UI
# ever shows, because androidx.startup.InitializationProvider (a ContentProvider)
# runs WorkManagerInitializer at process bind time. Found via on-device testing
# of the release build (no automated test/lint catches this: it's an R8+reflection
# interaction, not a compile-time error). Keep Room-generated classes verbatim.
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Database class * { *; }
-keepclassmembers @androidx.room.Database class * { *; }
-keep class **_Impl { *; }
-keep class **_Impl$* { *; }
-keep class androidx.work.impl.** { *; }
-dontwarn androidx.room.**
-dontwarn androidx.work.**

# --- Enums used across serialization boundaries ---
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# --- commonmark (Markdown viewer parsing) ---
-dontwarn org.commonmark.**

# --- WebView JS bridge (LatexView's size-reporting @JavascriptInterface) ---
# WebView finds this method by name via reflection from JS; R8 renaming it
# would silently break the bridge (no build error) rather than crash.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# NOTE: Apache POI was evaluated for legacy .doc/.ppt viewing and rejected —
# POI core uses java.lang.invoke.MethodHandle in a way D8 refuses to dex
# below minSdk 26, which would break Android 7.0/7.1 compatibility. Legacy
# .doc/.ppt intentionally fall back to "open in another app" instead. See
# IMPLEMENTATION_AND_MAINTENANCE.md.

