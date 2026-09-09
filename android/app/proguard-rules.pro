# kotlinx.serialization: os serializers gerados sao resolvidos por reflexao a
# partir do companion/`$serializer`. Sem estas regras o release minificado
# quebra em runtime (SerializationException) sem quebrar o build.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    public static **$* Companion;
}

# Modelos de rede/persistencia: mantidos inteiros porque os nomes de campo
# fazem parte do contrato com o servidor.
-keep,includedescriptorclasses class me.zippert.dialoglite.data.remote.**$$serializer { *; }
-keep class me.zippert.dialoglite.data.remote.dto.** { *; }
-keep class me.zippert.dialoglite.data.local.** { *; }

# Retrofit
-keepattributes Signature, RuntimeVisibleAnnotations, AnnotationDefault
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# OkHttp
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
