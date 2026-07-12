# Add project specific ProGuard rules here.

# Reader UI contract models (io.reader.ui:reader-ui-contract via composite build).
# Keep public API so kotlinx.serialization reflection and inter-module consumers
# remain stable after minification.
-keep class io.reader.ui.** { *; }

# Keep @Serializable data class fields for kotlinx.serialization stability.
-keepattributes *Annotation*
-keepclassmembers @kotlinx.serialization.Serializable class * {
    <fields>;
}

# Keep Kotlin metadata for reflection.
-keep class kotlin.Metadata { *; }
