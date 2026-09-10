plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val signingStoreFile = System.getenv("ANDROID_SIGNING_STORE_FILE")
val signingPassword = System.getenv("ANDROID_SIGNING_PASSWORD")
val hasPermanentSigning = !signingStoreFile.isNullOrBlank() && !signingPassword.isNullOrBlank()

android {
    namespace = "br.com.monitordenoticias.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "br.com.monitordenoticias.android"
        minSdk = 26
        targetSdk = 35
        versionCode = 402
        versionName = "4.0.2"
    }

    signingConfigs {
        if (hasPermanentSigning) {
            create("permanent") {
                storeFile = file(signingStoreFile!!)
                storePassword = signingPassword
                keyAlias = "monitor-noticias"
                keyPassword = signingPassword
            }
        }
    }

    buildTypes {
        debug {
            if (hasPermanentSigning) {
                signingConfig = signingConfigs.getByName("permanent")
            }
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasPermanentSigning) {
                signingConfig = signingConfigs.getByName("permanent")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf("-opt-in=androidx.compose.material3.ExperimentalMaterial3Api")
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("org.jsoup:jsoup:1.18.3")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
