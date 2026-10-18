# Yuyu release rules. Library-specific rules travel with each module's
# consumer-rules.pro (see core/network/consumer-rules.pro).

# Keep source file/line numbers for readable crash stacks, but hide original names.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# kotlinx.serialization: navigation routes and models are @Serializable.
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions, RuntimeVisibleAnnotations, AnnotationDefault
-dontnote kotlinx.serialization.**
-keepclassmembers @kotlinx.serialization.Serializable class com.twilighty.yuyu.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.twilighty.yuyu.**$$serializer { *; }
-keepclassmembers class com.twilighty.yuyu.** {
    *** Companion;
}

# Google Identity (Credential Manager custom credential types).
-keep class com.google.android.libraries.identity.googleid.** { *; }
-dontwarn com.google.android.libraries.identity.googleid.**
