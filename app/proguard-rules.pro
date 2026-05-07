# Сохраняем строки для отладки стектрейсов
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Retrofit + OkHttp
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepattributes Signature
-keepattributes Exceptions
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# Gson — сохраняем DTO-классы от обфускации
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keep class com.eva.app.data.api.** { *; }
-keepclassmembers class com.eva.app.data.api.** { *; }

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keep,includedescriptorclasses class com.eva.app.**$$serializer { *; }
-keepclassmembers class com.eva.app.** {
    *** Companion;
}

# Hilt / Dagger
-dontwarn com.google.dagger.**
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }
-keep @dagger.hilt.InstallIn class * { *; }
-keep @javax.inject.Singleton class * { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *
-dontwarn androidx.room.**

# Firebase
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# Coil
-dontwarn coil.**

# DataStore
-keep class androidx.datastore.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# Корутины
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
