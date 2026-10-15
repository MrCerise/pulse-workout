# PULSE wear — release ProGuard/R8 rules.
# Mirrors the phone app's rules for the pieces this APK contains.

# kotlinx.serialization: keep generated serializers for our model types.
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

# Engine and sync model classes are serialized by name across the wire.
-keep class com.pulse.engine.** { *; }
-keep class com.pulse.sync.** { *; }

# Google Play services clients are bound through Play services.
-keep class com.google.android.gms.wearable.** { *; }

-dontwarn org.jetbrains.annotations.**
