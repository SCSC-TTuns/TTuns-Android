plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.ttuns"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ttuns"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        // ✅ 계측 테스트 러너
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // BuildConfig 값들
        buildConfigField(
            "String",
            "TTUNS_BACKEND_BASE",
            "\"${project.findProperty("TTUNS_BACKEND_BASE") ?: "https://ttuns.vercel.app"}\""
        )
        buildConfigField("String", "SNUTT_API_BASE", "\"${project.findProperty("SNUTT_API_BASE") ?: ""}\"")
        buildConfigField("String", "SNUTT_API_KEY", "\"${project.findProperty("SNUTT_API_KEY") ?: ""}\"")
        buildConfigField("String", "SNUTT_DEFAULT_TOKEN", "\"${project.findProperty("SNUTT_DEFAULT_TOKEN") ?: ""}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug { }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    // Java/Kotlin 17 통일
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    kotlin {
        jvmToolchain(17)
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    // --- Compose BOM ---
    val composeBom = platform("androidx.compose:compose-bom:2025.01.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // Compose
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3:1.3.0")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // 네트워킹
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-moshi:2.11.0")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    implementation("androidx.webkit:webkit:1.11.0")
    implementation("com.google.android.material:material:1.12.0")

    // =========================
    // ✅ 테스트 의존성 (핵심)
    // =========================
    // 로컬 단위 테스트(JVM)
    testImplementation("junit:junit:4.13.2")
    testImplementation(kotlin("test"))

    // 계측 테스트(Android 기기/에뮬레이터)
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.test:core-ktx:1.6.1")
    // (Compose UI 테스트가 필요하면 아래도 추가)
    // androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
