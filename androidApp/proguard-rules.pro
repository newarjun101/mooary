# Keep line numbers for readable production crash reports.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Ktor / OkHttp / Coroutines
-dontwarn org.slf4j.**
-dontwarn kotlinx.coroutines.**
-keepclassmembers class io.ktor.** { volatile <fields>; }
-keep class io.ktor.client.engine.okhttp.OkHttpEngine { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.arjun.mooary.** {
    *** Companion;
}
-keepclasseswithmembers class com.arjun.mooary.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.arjun.mooary.**$$serializer { *; }

# SQLDelight generated code
-keep class com.arjun.mooary.data.local.db.** { *; }

# Compose
-dontwarn org.jetbrains.compose.**

# Tink, reached through androidx.security:security-crypto, carries ErrorProne annotations.
# They are compile-time only, CLASS-retention, and never present at runtime, so R8 reports
# them as missing on every release build. Nothing references them at runtime, which is why
# suppressing is correct rather than papering over a real gap.
-dontwarn com.google.errorprone.annotations.**

