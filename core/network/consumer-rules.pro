# Retrofit: keep API interfaces and the generic signatures Retrofit reads at runtime.
-keepattributes Signature, InnerClasses, EnclosingMethod, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, AnnotationDefault
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>
-keep interface com.twilighty.yuyu.core.network.api.** { *; }

# DTOs: decoded by kotlinx.serialization through generated serializers
# (ApiEnvelope<T> is generic, so keep the model package intact).
-keep class com.twilighty.yuyu.core.network.model.** { *; }
-keep,includedescriptorclasses class com.twilighty.yuyu.core.network.model.**$$serializer { *; }

-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
