plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("kotlin-kapt")
}

android {
    namespace = "com.example.personeltracking2026"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.personeltracking2026"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "APP_VERSION", "\"1.0.0\"")

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
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
    packaging {
        resources {
            // pilih salah satu file META-INF saat ada duplikasi (lebih ringkas daripada daftarin satu-satu)
            pickFirsts += setOf(
                "META-INF/*",
                "META-INF/**"
            )
        }
    }
}

dependencies {
    // CORE ANDROID
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.activity:activity-ktx:1.8.2")

    // LIFECYCLE
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")

    // COROUTINES
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    // CARDVIEW
    implementation("androidx.cardview:cardview:1.0.0")

    // NETWORK
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // MAP
    implementation("org.maplibre.gl:android-sdk:13.0.2")

    // JWT
    implementation("com.auth0.android:jwtdecode:2.0.2")

    // LOCATION
    implementation("com.google.android.gms:play-services-location:21.3.0")

    //STREAM
    implementation("com.github.pedroSG94.RootEncoder:library:2.4.5")

    // MQTT
    implementation("com.hivemq:hivemq-mqtt-client:1.3.3")

    // --- Netty BOM: kunci versi semua modul Netty biar konsisten ---
    implementation(platform("io.netty:netty-bom:4.1.111.Final"))
    // --- Modul yang dibutuhin WebSocket HiveMQ (HTTP + handler) ---
    implementation("io.netty:netty-codec-http")
    implementation("io.netty:netty-handler")
    implementation("io.netty:netty-transport")
    implementation("io.netty:netty-buffer")

    //room database
    implementation("androidx.room:room-runtime:2.8.4")
    implementation(libs.androidx.activity)
    kapt("androidx.room:room-compiler:2.8.4")

    //WorkManager
    implementation("androidx.work:work-runtime-ktx:2.11.2")

    //circleindicator
    implementation("me.relex:circleindicator:2.1.6")

    implementation("com.github.bumptech.glide:glide:4.16.0")

    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")

    // TEST
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}