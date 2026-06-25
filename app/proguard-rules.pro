# Babymomo ProGuard Rules

# Kotlin Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# SQLCipher
-keep class net.sqlcipher.** { *; }
-dontwarn net.sqlcipher.**

# LiteRT
-keep class com.google.ai.edge.litert.** { *; }
-dontwarn com.google.ai.edge.litert.**

# ONNX Runtime
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**

# Hilt
-dontwarn dagger.hilt.**

# Ktor
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Compose
-keep class androidx.compose.** { *; }
