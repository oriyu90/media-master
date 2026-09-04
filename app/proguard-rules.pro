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

# --- Moshi / Retrofit / OkHttp (network storage, future remote features) ---
-keepclasseswithmembers class * { @com.squareup.moshi.* <methods>; }
-keep @com.squareup.moshi.JsonQualifier @interface *
-keepclassmembers @com.squareup.moshi.JsonClass class * { <init>(...); <fields>; }
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**

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

# --- Enums used across serialization boundaries ---
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

