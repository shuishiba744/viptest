plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.shuishiba744.sameviphook"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.shuishiba744.sameviphook"
        minSdk = 23
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        named("release") {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        named("debug") {
            isDebuggable = true
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

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // LSPosed 模块不需要 dex 优化，构建更快
    buildFeatures {
        buildConfig = false
    }
}

dependencies {
    // Xposed API (LSPosed 兼容，API 82+ 支持 LSPosed API 93)
    compileOnly("de.robv.android.xposed:api:82")

    // AndroidX 注解
    implementation("androidx.annotation:annotation:1.7.1")
}
