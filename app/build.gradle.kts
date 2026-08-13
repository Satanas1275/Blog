plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "studio.rocknite.blog"
    compileSdk = 34

    defaultConfig {
        applicationId = "studio.rocknite.blog"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
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
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Réseau
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Stockage sécurisé du token
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Graphiques pour la page analytics
    implementation("com.patrykandpatrick.vico:compose-m3:1.14.0")

    // Aperçu des photos sélectionnées pour un post
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Icônes Material étendues (CalendarMonth, Image, Send...)
    implementation("androidx.compose.material:material-icons-extended:1.6.8")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
