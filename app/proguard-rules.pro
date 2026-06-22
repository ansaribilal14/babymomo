# ProGuard rules for BABYMOMO release builds
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions, EnclosingMethod
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.lifecycle.HiltViewModel
-keepclassmembers class * { @dagger.hilt.android.lifecycle.HiltViewModel <init>(...); }
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep class androidx.room.** { *; }
-keepclassmembers class * { @com.squareup.moshi.* <methods>; }
-keep @com.squareup.moshi.JsonClass class * { *; }
-keepclassmembers class * { @com.squareup.moshi.* <fields>; }
-keep class ai.onnxruntime.** { *; }
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class com.babymomo.data.db.entity.** { *; }
-keep class com.babymomo.core.llm.** { *; }
-keep class com.babymomo.core.memory.** { *; }
-keep class com.babymomo.core.agents.** { *; }
-keep class com.babymomo.core.skills.** { *; }
-keep class com.babymomo.core.projects.** { *; }
-keep class com.babymomo.model.** { *; }

# MediaPipe GenAI — LlmInference uses reflection/JNI to load native libs and task graph classes.
# Without these keeps, R8 strips the genai classes and the runtime crashes at createFromOptions().
-keep class com.google.mediapipe.** { *; }
-keep class com.google.mediapipe.genai.** { *; }
-keep class com.google.mediapipe.tasks.genai.** { *; }
-keep class com.google.mediapipe.tasks.core.** { *; }
-keepclassmembers class com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.**
