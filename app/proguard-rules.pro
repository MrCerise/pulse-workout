# PULSE Interval Coach — release ProGuard/R8 rules.
# Room, kotlinx.serialization and Media3 all ship consumer rules, but the reflection-sensitive
# entry points used by this app are pinned explicitly so shrinking cannot break them.

# Room stores generated implementations by name.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep @androidx.room.Entity class * { *; }
-keepclassmembers class * { @androidx.room.* <methods>; }

# kotlinx.serialization: keep generated serializers for our model types (workouts, backups).
-keepclassmembers class ** {
    *** Companion;
}
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.pulse.**$$serializer { *; }
-keepclassmembers class com.pulse.** {
    *** Companion;
}

# Engine model classes are serialized by name into the database and backup files.
-keep class com.pulse.engine.** { *; }

# Foreground service and widget providers are instantiated by the framework.
-keep class com.pulse.intervalcoach.session.WorkoutService { *; }
-keep class com.pulse.intervalcoach.widget.** { *; }

# TTS callbacks are invoked through the framework.
-keep class * implements android.speech.tts.TextToSpeech$OnInitListener { *; }

-dontwarn org.jetbrains.annotations.**
