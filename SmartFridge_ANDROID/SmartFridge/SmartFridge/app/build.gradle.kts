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
            excludes.add("META-INF/LGPL2.1")  // Ignorar el archivo duplicado
            excludes.add("META-INF/AL2.0") // Ignorar el archivo duplicado
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
    // Dependencias para MQTT
    implementation(libs.org.eclipse.paho.client.mqttv3)
    implementation(libs.org.eclipse.paho.android.service)
    // Dependencias para SQL
    implementation(libs.mysql.connector.java)
    implementation(libs.mariadb.java.client)
    /*implementation(libs.jetty.server)
    implementation(libs.naming)*/
    // Para mqtt jakarta
    implementation(libs.jakarta.servlet.api)
    // para gson de google
    implementation(libs.gson)
    implementation(libs.hikaricp)
    // jndi de java
    //implementation(libs.glassy.jndi) // De momento no funciona
}