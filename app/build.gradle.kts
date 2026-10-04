plugins {
    id("com.android.application")
}

android {
    namespace = "com.example.stargatewebview"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.stargatewebview"
        minSdk = 23
        targetSdk = 35
        versionCode = 10
        versionName = "1.9-final"
        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}
