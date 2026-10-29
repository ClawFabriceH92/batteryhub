import java.io.File
import java.util.Base64

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

fun releaseKeystore(): File? {
    System.getenv("BATTERYHUB_KEYSTORE_B64")?.let { b64 ->
        return try {
            val tmp = File(System.getenv("RUNNER_TEMP") ?: "/tmp", "batteryhub-release.keystore")
            tmp.writeBytes(Base64.getDecoder().decode(b64))
            tmp
        } catch (e: Exception) {
            null
        }
    }
    val candidats = listOf(
        File("/root/.secrets/keystores-android/batteryhub-release.keystore"),
        File(System.getProperty("user.home"), ".secrets/keystores-android/batteryhub-release.keystore"),
    )
    return candidats.firstOrNull { it.exists() }
}

android {
    namespace = "fr.fh.batteryhub"
    compileSdk = 35

    defaultConfig {
        applicationId = "fr.fh.batteryhub"
        minSdk = 26
        targetSdk = 35
        versionCode = 7
        versionName = "1.0.6"
        resourceConfigurations += listOf("fr")
    }

    val ks = releaseKeystore()
    if (ks != null) {
        signingConfigs {
            create("release") {
                storeFile = ks
                storePassword = System.getenv("BATTERYHUB_KEYSTORE_PASSWORD") ?: "batteryhub2026"
                keyAlias = System.getenv("BATTERYHUB_KEY_ALIAS") ?: "batteryhub"
                keyPassword = System.getenv("BATTERYHUB_KEY_PASSWORD") ?: "batteryhub2026"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            if (ks != null) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.5")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.5")

    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20231013")
}
