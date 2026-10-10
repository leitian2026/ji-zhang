import java.text.SimpleDateFormat
import java.util.Date

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.shopledger.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.shopledger.app"
        minSdk = 26
        targetSdk = 34
        // versionCode 取"自 2026-01-01 起的秒数"，每次构建一定比上次大，才能覆盖安装
        versionCode = ((System.currentTimeMillis() / 1000) - 1767225600L).toInt()
        versionName = "1.0." + SimpleDateFormat("yyMMdd.HHmm").format(Date())
    }

    signingConfigs {
        create("release") {
            storeFile = file("../release.jks")
            storePassword = "shopledger"
            keyAlias = "shopledger"
            keyPassword = "shopledger"
        }
    }

    buildTypes {
        release {
            // R8：代码压缩、优化、混淆 + 去掉没用到的资源，安装包更小、运行更快
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
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
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
}
