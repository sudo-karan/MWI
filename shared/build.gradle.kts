import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
            // The expect/actual-class pattern (Crypto, Lock, AppDatabaseConstructor) is stable for
            // our use; opt in explicitly to silence the Beta warning.
            freeCompilerArgs.add("-Xexpect-actual-classes")
        }
    }

    // iOS scaffolding is intentionally omitted for now (Android-only deliverable).
    // The KMP structure (commonMain/androidMain) keeps a future iosMain a drop-in.

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.materialIconsExtended)
                implementation(compose.ui)
                implementation(compose.components.resources)

                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.datetime)
                implementation(libs.kotlinx.collections.immutable)

                implementation(libs.androidx.room.runtime)
                implementation(libs.androidx.sqlite.bundled)
                implementation(libs.androidx.datastore.preferences.core)
                implementation(libs.androidx.datastore.core)
            }
        }

        val androidMain by getting {
            dependencies {
                implementation(libs.kotlinx.coroutines.android)
                implementation(libs.androidx.core.ktx)
                implementation(libs.androidx.activity.compose)
                implementation(libs.androidx.lifecycle.runtime.ktx)
                implementation(libs.androidx.lifecycle.service)

                // Crypto (BUILD FIRST) — pure JVM/Tink/BouncyCastle, no Android framework calls,
                // so the actuals are unit-testable on the JVM.
                implementation(libs.tink.android)
                implementation(libs.bouncycastle.prov)
                implementation(libs.bouncycastle.pkix)

                // Android File-based DataStore factory (`preferencesDataStoreFile`).
                implementation(libs.androidx.datastore.preferences)
            }
        }

        val androidUnitTest by getting {
            dependencies {
                implementation(libs.junit)
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
            }
        }
    }
}

android {
    namespace = "com.ismartcoding.plain.shared"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Unit tests exercise the crypto actuals on the JVM.
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "com.ismartcoding.plain.resources"
    generateResClass = always
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    add("kspAndroid", libs.androidx.room.compiler)
}
