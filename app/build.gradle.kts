import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Single source of truth for the app version. The release workflow refuses
// to publish if the git tag (e.g. v1.2.0) doesn't match this.
val appVersionName = "1.0.0"

// Short fingerprint of the bundled dictionary; the app re-installs its copy whenever this changes.
val dictionaryFingerprint = file("src/main/assets/dictionary/wordnet.db").let { db ->
    MessageDigest.getInstance("SHA-256").digest(db.readBytes())
        .take(6).joinToString("") { "%02x".format(it) }
}
val appVersionCode = appVersionName.split(".").map(String::toInt).let { (major, minor, patch) ->
    major * 10_000 + minor * 100 + patch
}

android {
    namespace = "com.pranvirsingh.wordlens"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.pranvirsingh.wordlens"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
        buildConfigField("String", "DICTIONARY_FINGERPRINT", "\"$dictionaryFingerprint\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Real phones only: the text recogniser's native library is ~11 MB per
        // CPU type, and x86 builds only ever run on emulators.
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    // Release signing comes from environment variables only (set locally or
    // as GitHub Actions secrets) — the keystore itself is never committed.
    val keystorePath = System.getenv("WORDLENS_KEYSTORE_PATH")
    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("WORDLENS_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("WORDLENS_KEY_ALIAS")
                keyPassword = System.getenv("WORDLENS_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
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
        abortOnError = true
        warningsAsErrors = true
        checkDependencies = false
        disable += setOf(
            // New library/SDK versions appear constantly, and which ones lint knows about
            // depends on the machine's installed SDK. Upgrades are deliberate, tested
            // changes — not something that should fail a build on one machine but not another.
            "GradleDependency",
            "NewerVersionAvailable",
            "AndroidGradlePluginVersion",
            "OldTargetApi",
            // ARM-only on purpose (see abiFilters); ChromeOS runs ARM apps through translation.
            "ChromeOsAbiSupport",
        )
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.camera.mlkit.vision)
    implementation(libs.mlkit.text.recognition)

    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.sqlite.jdbc)
}
