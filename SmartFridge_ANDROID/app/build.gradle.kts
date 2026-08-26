plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.smartfridge"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.smartfridge"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    packaging {
        resources {
            excludes.add("META-INF/LGPL2.1")
            excludes.add("META-INF/AL2.0")
        }
    }
}

dependencies {

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

    // Dependencias para MQTT — de momento se dejan tal cual; revisar si
    // siguen teniendo uso una vez el móvil hable solo con la API REST,
    // o si se quieren para publicar directamente en frigorifico/modo
    // (ver TODO en DashboardActivity sobre el switch de RFID).
    implementation(libs.org.eclipse.paho.client.mqttv3)
    implementation(libs.org.eclipse.paho.android.service)

    // para gson de google (ya se usaba; ahora también como converter de Retrofit)
    implementation(libs.gson)

    implementation(libs.okhttp)

    // --- Nuevo en la Fase 5 ---
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)

    // RETIRADO: mysql-connector-java, mariadb-java-client, hikaricp,
    // jakarta-servlet-api. Ver nota completa en gradle/libs.versions.toml.
}
