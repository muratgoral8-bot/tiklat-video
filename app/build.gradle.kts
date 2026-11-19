plugins {
    id("com.android.application")
}

android {
    namespace = "com.muratgoral.tiklatvideo"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.muratgoral.tiklatvideo"
        minSdk = 24
        targetSdk = 35
        versionCode = 3
        versionName = "1.2"
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

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

// Bağımlılık yok: yalnızca Android'in kendi WebView'i.
