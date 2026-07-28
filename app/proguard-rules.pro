# IdeaForge AI - R8/ProGuard Rules

# Keep serialization
-keepattributes *Annotation*, InnerClasses, EnclosingMethod
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.ideaforge.ai.**$$serializer { *; }
-keepclassmembers class com.ideaforge.ai.** { *** Companion; }
-keepclasseswithmembers class com.ideaforge.ai.** { kotlinx.serialization.KSerializer serializer(...); }
-dontnote kotlinx.serialization.AnnotationsKt

# Keep Room entities
-keep class com.ideaforge.ai.core.database.** { *; }

# Keep network models
-keep class com.ideaforge.ai.core.network.** { *; }

# Keep domain models
-keep class com.ideaforge.ai.domain.model.** { *; }

# Retrofit
-keepattributes Signature
-keepattributes Exceptions
-keepattributes *Annotation*
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# Compose
-dontwarn androidx.compose.**

# WorkManager
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.ListenableWorker
-keepclassmembers class * {
    @androidx.work.* <methods>;
}

# General
-optimizationpasses 5
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
