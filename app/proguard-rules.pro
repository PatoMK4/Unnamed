# Keep kotlinx.serialization metadata for @Serializable models.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class com.unnamed.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
