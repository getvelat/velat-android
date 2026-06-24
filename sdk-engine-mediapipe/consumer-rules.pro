# =============================================================================
# Consumer ProGuard rules — merged into any app that depends on this library.
# =============================================================================
# Keep MediaPipe's native bridges. R8 may strip method references that are
# only called from JNI; without these rules, MediaPipe crashes at runtime
# with NoSuchMethodError when consumers ship a minified build.

-keep class com.google.mediapipe.tasks.genai.** { *; }
-keep class com.google.mediapipe.tasks.core.** { *; }

# Keep Velat's public engine API so consumers can reflectively look up
# names if needed.
-keep public class ai.velat.engine.mediapipe.** { public *; }

# Native bridges
-keepclasseswithmembernames class * {
    native <methods>;
}
