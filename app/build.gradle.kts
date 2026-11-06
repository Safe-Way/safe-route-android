import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Keys live in local.properties, which is never committed.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val kakaoNativeAppKey: String = localProperties.getProperty("KAKAO_NATIVE_APP_KEY", "")
// e.g. http://10.0.2.2:8080 for a server on this computer seen from the emulator. Blank = example data.
val serverBaseUrl: String = localProperties.getProperty("SERVER_BASE_URL", "")
// Gemini (free tier) for the 말로 맞추기 setup chat; empty means the app's own rules answer instead.
val geminiApiKey: String = localProperties.getProperty("GEMINI_API_KEY", "")
val geminiModel: String = localProperties.getProperty("GEMINI_MODEL", "gemini-flash-latest")

android {
    namespace = "com.deungbul.anshimgil"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.deungbul.anshimgil"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "KAKAO_NATIVE_APP_KEY", "\"$kakaoNativeAppKey\"")
        buildConfigField("String", "SERVER_BASE_URL", "\"$serverBaseUrl\"")
        buildConfigField("String", "GEMINI_API_KEY", "\"$geminiApiKey\"")
        buildConfigField("String", "GEMINI_MODEL", "\"$geminiModel\"")
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kakao.map)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.compose.material.icons.extended)
    testImplementation(libs.junit)
    // Android's org.json is a stub in local unit tests; use the real one there.
    testImplementation(libs.org.json)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}