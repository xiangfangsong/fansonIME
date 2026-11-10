plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.fanson.ime"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.fanson.ime"
        minSdk = 31
        targetSdk = 35
        versionCode = 19
        versionName = "0.4.0"
        testInstrumentationRunner = "com.fanson.ime.SmokeInstrumentation"
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { buildConfig = true }
    lint { abortOnError = true; warningsAsErrors = false }
    testOptions { unitTests.isReturnDefaultValues = false }
}

dependencies { testImplementation("junit:junit:4.13.2") }
