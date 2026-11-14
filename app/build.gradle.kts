plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// Every build from GitHub gets a higher number, so Android installs it over the previous one.
val runNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "0").toInt()
// All versions are signed with the same key: without this Android refuses the update
// and asks to uninstall first. The key file and its password are GitHub secrets (never in the repo);
// CI writes the file to keystore/fortycalendar.p12 before building.
val keystoreFile: File = rootProject.file("keystore/fortycalendar.p12")
val keystorePassword: String? = System.getenv("FORTY_KEYSTORE_PASSWORD")
    ?.takeIf { it.isNotBlank() && keystoreFile.exists() }

android {
    namespace = "com.quarenta.calendario"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.quarenta.calendario"
        minSdk = 26
        targetSdk = 35
        versionCode = 100 + runNumber
        versionName = "0.3.$runNumber"
    }

    signingConfigs {
        if (keystorePassword != null) {
            create("forty") {
                storeFile = keystoreFile
                storeType = "PKCS12"
                storePassword = keystorePassword
                keyAlias = "fortycalendar"
                keyPassword = keystorePassword
            }
        }
    }

    buildTypes {
        debug {
            if (keystorePassword != null) signingConfig = signingConfigs.getByName("forty")
        }
        // The APK people install. Unlike debug it is not "debuggable", so Android compiles it fully
        // and Compose runs smoothly at 90/120 Hz. Same key as before, so it installs over the old one.
        release {
            signingConfig = if (keystorePassword != null) signingConfigs.getByName("forty") else signingConfigs.getByName("debug")
            // No code shrinking yet: everything stays exactly as tested.
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    // Installs the ready-made speed profiles of Compose (and our own) when the APK is installed from outside the Play Store
    implementation("androidx.profileinstaller:profileinstaller:1.3.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    // Frosted glass (real background blur on Android 12+, a soft tint before that)
    implementation("dev.chrisbanes.haze:haze:1.1.1")

    val room = "2.6.1"
    implementation("androidx.room:room-runtime:$room")
    implementation("androidx.room:room-ktx:$room")
    ksp("androidx.room:room-compiler:$room")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
}
