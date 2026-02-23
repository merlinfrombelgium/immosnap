import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.immosnap"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.immosnap"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        val localProps = rootProject.file("local.properties")
        if (localProps.exists()) {
            val props = Properties().apply { load(localProps.inputStream()) }
            buildConfigField("String", "MAPS_API_KEY", "\"${props["MAPS_API_KEY"] ?: ""}\"")
            buildConfigField("String", "SEARCH_API_KEY", "\"${props["SEARCH_API_KEY"] ?: ""}\"")
            buildConfigField("String", "SEARCH_ENGINE_ID", "\"${props["SEARCH_ENGINE_ID"] ?: ""}\"")
            buildConfigField("String", "GEMINI_API_KEY", "\"${props["GEMINI_API_KEY"] ?: ""}\"")
        } else {
            buildConfigField("String", "MAPS_API_KEY", "\"\"")
            buildConfigField("String", "SEARCH_API_KEY", "\"\"")
            buildConfigField("String", "SEARCH_ENGINE_ID", "\"\"")
            buildConfigField("String", "GEMINI_API_KEY", "\"\"")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.01.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    implementation("androidx.camera:camera-core:1.4.1")
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.camera:camera-view:1.4.1")

    implementation("com.google.mlkit:text-recognition:16.1.0")
    implementation("com.google.android.gms:play-services-location:21.3.0")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    implementation("com.google.ai.client.generativeai:generativeai:0.9.0")
    implementation("io.coil-kt:coil-compose:2.7.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
