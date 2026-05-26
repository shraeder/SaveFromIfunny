plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

import com.android.build.gradle.internal.api.ApkVariantOutputImpl

android {
    namespace = "com.ifunnysaver"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.ifunnysaver"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    applicationVariants.all {
        outputs.all {
            (this as ApkVariantOutputImpl).outputFileName = "SaveFromiFunny.apk"
        }
    }
}

dependencies {
    // No UI libraries required; this app is a share receiver.
}
