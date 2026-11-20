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
        buildConfig = true
    }

    // Üç uygulama tek projeden:
    //  video : park halinde araç ekranında (Ders İzle yöntemi)
    //  arka  : ön koltuk başlığının arkasındaki ekran; park uygulaması + "sürüşte açık kalabilir" işareti
    //  surus : arka ekran için harita tipi (navigasyon yüzeyi) uygulama; UnknownAuto ile kurulur
    flavorDimensions += "tur"
    productFlavors {
        create("video") {
            dimension = "tur"
            applicationId = "com.muratgoral.tiklatvideo"
            versionCode = 3
            versionName = "1.2"
            resValue("string", "app_name", "Tiklat Video")
            buildConfigField("boolean", "ARKA", "false")
        }
        create("arka") {
            dimension = "tur"
            applicationId = "com.muratgoral.tiklatarka"
            versionCode = 2
            versionName = "1.1"
            resValue("string", "app_name", "Tiklat Arka Koltuk")
            buildConfigField("boolean", "ARKA", "true")
        }
        create("surus") {
            dimension = "tur"
            applicationId = "com.muratgoral.tiklatarkasurus"
            versionCode = 1
            versionName = "1.0"
            resValue("string", "app_name", "Tiklat Arka Sürüş")
            buildConfigField("boolean", "ARKA", "true")
        }
    }

    // video ve arka aynı WebView kabuğunu kullanır
    sourceSets {
        getByName("video").java.srcDir("src/kabuk/java")
        getByName("arka").java.srcDir("src/kabuk/java")
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
    "surusImplementation"("androidx.car.app:app:1.4.0")
}
