plugins {
    id("com.android.application")
}

android {
    namespace = "com.muratgoral.tiklatvideo"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
        targetSdk = 35
    }

    buildFeatures {
        resValues = true
    }

    // İki uygulama tek projeden:
    //  video : park halinde araç ekranında (Ders İzle yöntemi)
    //  arka  : ön koltuk başlığının arkasındaki ekran, araç giderken de oynar
    flavorDimensions += "tur"
    productFlavors {
        create("video") {
            dimension = "tur"
            applicationId = "com.muratgoral.tiklatvideo"
            versionCode = 3
            versionName = "1.2"
            resValue("string", "app_name", "Tiklat Video")
        }
        create("arka") {
            dimension = "tur"
            applicationId = "com.muratgoral.tiklatarka"
            versionCode = 1
            versionName = "1.0"
            resValue("string", "app_name", "Tiklat Arka Koltuk")
        }
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

dependencies {
    "arkaImplementation"("androidx.car.app:app:1.4.0")
}
