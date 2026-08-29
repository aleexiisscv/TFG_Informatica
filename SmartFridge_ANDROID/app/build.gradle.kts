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
        // Los fuentes llevan comentarios en castellano con acentos. Sin
        // fijar la codificacion, javac usa la del sistema (windows-1252
        // en Windows) y los ficheros UTF-8 se compilan con caracteres
        // corruptos o error. Fijarlo hace el build reproducible entre
        // maquinas.
        encoding = "UTF-8"
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

    // --- Fase de rediseño UI (Material 3) ---
    // Fragmentos + Navigation Component: una sola Activity contenedora
    // (MainShellActivity) con BottomNavigationView persistente, en lugar
    // de saltar entre Activities con startActivity()+finish().
    implementation(libs.fragment)
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)
    // Listas recicladas: sustituyen a las TableLayout construidas a mano.
    implementation(libs.recyclerview)
    // Gesto "deslizar para refrescar", que reemplaza el sondeo ciego.
    implementation(libs.swiperefreshlayout)
    // Necesario explicitamente por el uso de layout_behavior y de los
    // FAB anclados (material lo arrastra, pero declararlo hace explicita
    // la dependencia real del proyecto).
    implementation(libs.coordinatorlayout)
    // Avatar animado del asistente. Es la UNICA dependencia nueva que no
    // es de AndroidX/Material: se acepta porque Lottie es el estandar de
    // facto para animaciones vectoriales exportadas desde After Effects
    // y evita empaquetar un GIF o un video para el avatar.
    implementation(libs.lottie)

    // --- Fase 11: asistente conversacional (RAG) ---
    // ViewModel + LiveData. La conversacion y la peticion en vuelo viven
    // en un AsistenteViewModel, no en el Fragment: una llamada al modelo
    // tarda 15-30 s y girar el movil a mitad ya no la pierde.
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.livedata)
    // Render de Markdown. Gemini responde con negritas, listas y pasos
    // numerados; sin esto el usuario veria los asteriscos en crudo.
    // Markwon convierte a Spanned nativo: sin WebView, respetando la
    // tipografia y el color del tema (y por tanto el color dinamico).
    implementation(libs.markwon.core)

    // Dependencias para MQTT — SIN USO ACTUAL desde la app. Ver nota en
    // gradle/libs.versions.toml antes de decidir si se retiran.
    implementation(libs.org.eclipse.paho.client.mqttv3)
    implementation(libs.org.eclipse.paho.android.service)

    // para gson de google (ya se usaba; ahora tambien como converter de Retrofit)
    implementation(libs.gson)

    implementation(libs.okhttp)

    // --- Fase 5 ---
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)

    // RETIRADO: mysql-connector-java, mariadb-java-client, hikaricp,
    // jakarta-servlet-api. Ver nota completa en gradle/libs.versions.toml.
}
