plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.example.localplayer"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.localplayer"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        // Ключ для загрузки из облака (Dropbox App key). Он не секретен (вход идёт через PKCE),
        // но и не хранятся в репозитории: приходит из переменной окружения (переменная GitHub Actions).
        // Если ключ не задан, блок загрузки из облака в настройках просто не показывается.
        val dropboxKey = (System.getenv("DROPBOX_APP_KEY") ?: "").trim()
        buildConfigField("String", "DROPBOX_APP_KEY", "\"$dropboxKey\"")
        // Схема адреса, на который Dropbox возвращает в приложение после входа (см. AndroidManifest.xml)
        manifestPlaceholders["dropboxScheme"] = "db-" + dropboxKey.ifEmpty { "unconfigured" }
    }

    // Ключ подписи release хранится вне репозитория: путь и пароль приходят из переменных окружения
    // (секреты GitHub Actions). Если их нет (локальная сборка, форк), release подписывается стандартным
    // отладочным ключом Android: такое приложение устанавливается, но не обновляет официальные сборки.
    val releaseKeystore = System.getenv("KEYSTORE_PATH")
    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS") ?: "radiant"
                keyPassword = System.getenv("KEY_PASSWORD") ?: System.getenv("KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // R8: сжатие и оптимизация кода. Compose в release работает заметно плавнее, чем в debug.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    val media3 = "1.5.1"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-session:$media3")

    // FFmpeg-декодер Media3 (ALAC). AAR собирается в CI (см. .github/workflows/build.yml) и кладётся в app/libs.
    // Если файла нет (обычная локальная сборка), приложение собирается и работает без ALAC.
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))
}
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}