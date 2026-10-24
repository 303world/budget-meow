plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.world303.budgetmeow"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.world303.budgetmeow"
        // 液态玻璃用 AGSL RuntimeShader，miuix-blur 要求 API 33
        minSdk = 33
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        // 公开的 CI 调试签名密钥（PKCS12，口令写在 README 里）。
        // 用于让每次 CI 产出的 APK 签名一致，可覆盖安装；正式发布请换成你自己的密钥。
        create("ci") {
            storeFile = rootProject.file("keystore/budget-meow-ci.p12")
            storePassword = "budgetmeow"
            keyAlias = "budgetmeow"
            keyPassword = "budgetmeow"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("ci")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("ci")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
            )
        }
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

dependencies {
    implementation(libs.miuix.ui)
    implementation(libs.miuix.icons)
    implementation(libs.miuix.preference)
    implementation(libs.miuix.blur)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.activity.compose)
    implementation(libs.coroutines.android)
    implementation(libs.serialization.json)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
}
