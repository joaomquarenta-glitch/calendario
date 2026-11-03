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
val keystoreFile = keystoreFile
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
        release {
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
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    val room = "2.6.1"
    implementation("androidx.room:room-runtime:$room")
    implementation("androidx.room:room-ktx:$room")
    ksp("androidx.room:room-compiler:$room")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
}
