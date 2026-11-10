# Add project specific ProGuard rules here.
# http://developer.android.com/guide/developing/tools/proguard.html

# --- Room ---
-keep class * extends androidx.room.RoomDatabase
-keep class * {@androidx.room.Entity <fields>;}
-keep class * {@androidx.room.Dao <methods>;}
-keep class com.example.data.** { *; }
-dontwarn androidx.room.paging.**

# --- Hilt ---
-keep class * extends dagger.hilt.android.internal.managers.ActivityComponentManager
-keep class * extends dagger.hilt.android.internal.managers.FragmentComponentManager
-keep class * extends dagger.hilt.internal.GeneratedComponentManager
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.internal.GeneratedComponent
-keepclassmembers @dagger.hilt.android.HiltAndroidApp class * {
    dagger.hilt.internal.GeneratedComponent component();
}
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exceptions
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-dontwarn dagger.hilt.**

# --- WorkManager ---
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.CoroutineWorker
-keep class androidx.work.** { *; }
-dontwarn androidx.work.**

# --- MediaPipe ---
-keep class com.google.mediapipe.** { *; }
-keep class com.google.mediapipe.framework.** { *; }
-keep class com.google.mediapipe.tasks.** { *; }

# --- OkHttp ---
-dontwarn okhttp3.**
-keep class okhttp3.** { *; }
-keep class okio.** { *; }

# --- Compose ---
-keep class androidx.compose.** { *; }
-keepclassmembers class androidx.compose.** { *; }

# --- Kotlin Coroutines ---
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# --- Kotlin Serialization ---
-keepattributes *Annotation*
-keepattributes Signature
-keep class kotlin.reflect.jvm.internal.** { *; }

# --- Coil ---
-keep class coil.** { *; }

# --- General ---
-keep class com.example.** { *; }
-keepclassmembers class com.example.** { *; }
