plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "ru.shiftcyber"
    compileSdk = 35

    defaultConfig {
        applicationId = "ru.shiftcyber"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "1.4.0"

        val appodealKey = System.getenv("APPODEAL_APP_KEY") ?: ""
        val yooKassaPaymentUrl = System.getenv("YOOKASSA_PAYMENT_URL") ?: ""
        val yooKassaShopId = System.getenv("YOOKASSA_SHOP_ID") ?: ""
        val yooKassaSecretKey = System.getenv("YOOKASSA_SECRET_KEY") ?: ""

        buildConfigField("String", "APPODEAL_APP_KEY", "\"$appodealKey\"")
        buildConfigField("String", "YOOKASSA_PAYMENT_URL", "\"$yooKassaPaymentUrl\"")
        buildConfigField("String", "YOOKASSA_SHOP_ID", "\"$yooKassaShopId\"")
        buildConfigField("String", "YOOKASSA_SECRET_KEY", "\"$yooKassaSecretKey\"")
    }

    val keystorePath = System.getenv("SIGNING_KEYSTORE_PATH")
    signingConfigs {
        if (!keystorePath.isNullOrBlank() && file(keystorePath).exists()) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("SIGNING_KEYSTORE_PASSWORD") ?: "shiftcyber2026"
                keyAlias = System.getenv("SIGNING_KEY_ALIAS") ?: "shiftcyber"
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD") ?: "shiftcyber2026"
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        release {
            isDebuggable = false
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfigs.findByName("release")?.let {
                signingConfig = it
            }
        }
        debug {
            isDebuggable = true
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.02.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.7")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.glance:glance-material3:1.1.1")
    implementation("com.google.android.gms:play-services-auth:21.3.0")

    // Coroutines & Gson (строгое хранение состояний монетизации/согласия/оплаты в JSON)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.google.code.gson:gson:2.10.1")

    // Appodeal Mediation SDK 3.5.0 Core + адаптеры для РФ: Яндекс РСЯ, VK Реклама (myTarget), IAB и Start.io
    implementation("com.appodeal.ads.sdk:core:3.5.0") {
        exclude(group = "com.android.billingclient", module = "billing")
    }
    implementation("com.appodeal.ads.sdk.networks:iab:3.5.0.0")
    implementation("com.appodeal.ads.sdk.networks:yandex:3.5.0.0") {
        exclude(group = "com.android.billingclient", module = "billing")
    }
    implementation("com.appodeal.ads.sdk.networks:my_target:3.5.0.0") {
        exclude(group = "com.android.billingclient", module = "billing")
    }
    implementation("com.startapp:inapp-sdk:5.0.2")

    testImplementation("junit:junit:4.13.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
