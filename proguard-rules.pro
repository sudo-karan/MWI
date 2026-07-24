# =============================================================================
# MWI keep rules
#
# IMPORTANT: R8/minify/shrink are intentionally DISABLED for release builds
# (see app/build.gradle.kts and gradle.properties). The reflection-heavy stack
# (Ktor, the vendored kGraphQL fork, kotlinx.serialization, kotlin-reflect)
# crashes when minified without a fully verified keep set. These rules are kept
# comprehensive so minification can be re-enabled later after verification.
# =============================================================================

# ---- Kotlin / coroutines / reflection ----
-keep class kotlin.Metadata { *; }
-keepclassmembers class ** { @kotlin.Metadata *; }
-dontwarn kotlin.**
-keep class kotlin.reflect.** { *; }
-keep class kotlin.coroutines.** { *; }

# ---- kotlinx.serialization ----
-keepattributes *Annotation*, InnerClasses, Signature, RuntimeVisibleAnnotations, AnnotationDefault
-keepclassmembers class ** {
    *** Companion;
}
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.ismartcoding.plain.**$$serializer { *; }
-keepclassmembers class com.ismartcoding.plain.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep @kotlinx.serialization.Serializable class com.ismartcoding.plain.** { *; }

# ---- Ktor / Netty ----
-keep class io.ktor.** { *; }
-keep class io.netty.** { *; }
-dontwarn io.ktor.**
-dontwarn io.netty.**
-dontwarn org.slf4j.**

# ---- kGraphQL (vendored) — heavy reflection over schema types ----
-keep class com.apurebase.kgraphql.** { *; }
-keep class com.ismartcoding.plain.web.** { *; }
-keepclassmembers class com.ismartcoding.plain.web.** { *; }

# ---- Room ----
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep @androidx.room.Entity class * { *; }
-dontwarn androidx.room.paging.**

# ---- Tink / BouncyCastle ----
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# ---- Compose ----
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# ---- Entities are serialized reflectively into GraphQL/WS payloads ----
-keep class com.ismartcoding.plain.db.** { *; }
