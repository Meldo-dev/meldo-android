# R8 rules for the release APK.

# Rust core: the UniFFI Kotlin bindings call into libmelde_ffi.so through JNA, which
# finds native methods, structures (field order) and callbacks by reflection.
-keep class uniffi.** { *; }
-keep class com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.** { public *; }
# JNA's desktop parts (AWT) don't exist on Android.
-dontwarn java.awt.**
-dontwarn com.sun.jna.**

# ML Kit document scanner: its components are created by reflection (no-arg constructors).
-keep class * implements com.google.firebase.components.ComponentRegistrar { <init>(); }
-keep class com.google.mlkit.** { <init>(); }
