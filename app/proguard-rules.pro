# AnvilVM ProGuard Rules

# Keep JNI native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep Hilt generated classes
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.lifecycle.HiltViewModelFactory { *; }

# Keep engine classes referenced from native
-keep class com.anvilvm.app.engine.** { *; }
