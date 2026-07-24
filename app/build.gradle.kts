import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// Optional release signing from keystore.properties (never committed).
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}

android {
    namespace = "com.ismartcoding.plain"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.ismartcoding.plain"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 32000
        versionName = "3.2.0"

        // Default ABI per spec.
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    signingConfigs {
        if (keystoreProps.getProperty("storeFile") != null) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
        }
        release {
            // R8/minify/shrink intentionally OFF (spec §3): the reflection-heavy
            // Ktor + kGraphQL stack crashes when minified without a verified keep set.
            isMinifyEnabled = false
            isShrinkResources = false
            isDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                rootProject.file("proguard-rules.pro"),
            )
            if (keystoreProps.getProperty("storeFile") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    // Flavors on `channel`: github / google / fdroid (fdroid stubs proprietary ML).
    flavorDimensions += "channel"
    productFlavors {
        create("github") { dimension = "channel" }
        create("google") { dimension = "channel" }
        create("fdroid") { dimension = "channel" }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/versions/9/OSGI-INF/MANIFEST.MF",
                "/META-INF/INDEX.LIST",
                "/META-INF/DEPENDENCIES",
                "/META-INF/LICENSE*",
                "/META-INF/NOTICE*",
                "/META-INF/*.kotlin_module",
                "META-INF/native-image/**",
                "**/*.proto",
            )
            // Netty ships identical META-INF entries in ~17 modules; keep the first.
            pickFirsts += setOf(
                "META-INF/io.netty.versions.properties",
                "META-INF/INDEX.LIST",
                "google/protobuf/**",
            )
            merges += setOf(
                "META-INF/services/**",
            )
        }
    }

    // The compiled web SPA lives here (PWA assets served from the classpath).
    sourceSets["main"].resources.srcDir("src/main/resources")
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    debugImplementation(libs.leakcanary)
}
