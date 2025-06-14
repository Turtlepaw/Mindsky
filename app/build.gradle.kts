plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.devtools.ksp") version "2.1.21-2.0.1"
    id("io.objectbox")
}

android {
    namespace = "io.github.turtlepaw.mindsky"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.turtlepaw.mindsky"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.runtime.livedata)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    // ml
    implementation(libs.litert)

    // Icons
    implementation(libs.androidx.material.icons.extended)

    // Work
    implementation(libs.androidx.work.runtime.ktx)

    // compose destinations
    implementation(libs.core)
    ksp(libs.ksp)
    implementation(libs.bottom.sheet)
    implementation(files("libs/sentence_embeddings.aar"))
    implementation(files("libs/model2vec.aar"))

    implementation(libs.okhttp)
    api(libs.bluesky)
    implementation(libs.gson)
    implementation(libs.androidx.security.crypto)
    //implementation(project(":atproto-authentication"))

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.ktor.client.okhttp)

    implementation(libs.ktor.client.logging)

    implementation(libs.onnxruntime.android)

    implementation(libs.accompanist.permissions)

    implementation(libs.library)
}