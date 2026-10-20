plugins {
    // AGP 9 trae Kotlin integrado: no se necesita un plugin de Kotlin aparte
    alias(libs.plugins.android.application)
}

android {
    namespace = "cl.inacap.ti3042.cadenafrio"
    compileSdk = 35

    defaultConfig {
        applicationId = "cl.inacap.ti3042.cadenafrio"
        // 26 (Android 8): mínimo que trae PBKDF2WithHmacSHA256 para cifrar las credenciales
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Las reglas propias de R8 van en src/main/keepRules
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        // Interfaz con layouts XML: solo ViewBinding, sin Compose
        viewBinding = true
    }
}

dependencies {
    // Solo androidx + Material Components
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
