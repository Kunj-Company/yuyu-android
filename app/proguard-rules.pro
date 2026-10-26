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

# ML Kit (barcode scanning) builds its components from registrar classes named in
# the manifest and creates them by reflection. The library rule keeps the classes
# but, under R8 full mode, not their no-arg constructors, so ML Kit never
# registered and BarcodeScanning.getClient() threw a NullPointerException the
# moment the check-in camera opened.
-keep class * implements com.google.firebase.components.ComponentRegistrar { <init>(); }

# Google Identity (Credential Manager custom credential types).
-keep class com.google.android.libraries.identity.googleid.** { *; }
-dontwarn com.google.android.libraries.identity.googleid.**
