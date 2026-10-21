# GGym — R8 rules for the release build.
#
# The app itself is reflection-free: Room generates its own DAOs, navigation uses string routes, and
# every string goes through the resource table. Two things do not survive an aggressive shrink on
# their own, and both are data-integrity problems rather than cosmetic ones.

# ------------------------------------------------------------------------------------------------
# kotlinx.serialization — the JSON backup
#
# The plugin generates a `<Type>$$serializer` class and a `serializer()` on the companion. The call
# sites are inlined at compile time, so R8's full mode cannot see a reflective use and would strip
# them. A stripped serializer turns "restore a backup" into a crash, which is the one path a user
# cannot recover from on their own.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault,InnerClasses
-keep,includedescriptorclasses class com.ggym.app.**$$serializer { *; }
-keepclassmembers class com.ggym.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.ggym.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ------------------------------------------------------------------------------------------------
# Enum constant names are persisted strings
#
# `ThemeMode` and `AppLanguage` are written to DataStore with `.name` and read back with a
# case-insensitive match against `entries`; `AlertSound` and `RingPolicy` cross the same boundary.
# R8 renaming a constant (or "enum unboxing" removing the class) would silently reset a saved
# preference, so every enum in the app is pinned by its fields and its lookup methods.
-keepclassmembers enum com.ggym.app.** {
    <fields>;
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
