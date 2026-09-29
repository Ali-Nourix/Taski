# Navigation's type-safe routes are @Serializable objects; keep their serializers.
-keepclassmembers class **.*Route* { *; }
-keep,includedescriptorclasses class io.github.alinourix.taski.**$$serializer { *; }
-keepclassmembers class io.github.alinourix.taski.** { *** Companion; }
-keepclasseswithmembers class io.github.alinourix.taski.** { kotlinx.serialization.KSerializer serializer(...); }
