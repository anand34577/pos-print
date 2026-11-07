# Hilt, Room, Compose, CameraX and kotlinx.serialization ship consumer rules.
# Keep only what reflection or persisted names need.

# Enum names are persisted in Room and in exported JSON (templates, backups).
-keepclassmembers enum com.posprint.app.domain.** { *; }

# kotlinx.serialization: keep generated serializers and companions of our @Serializable models
# (templates, label elements, printers, profile, backup file).
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.posprint.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.posprint.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.posprint.app.**$$serializer { *; }

# Bluetooth vendor stacks are touched reflectively inside the framework.
-dontwarn android.bluetooth.**

# Readable crash reports.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
