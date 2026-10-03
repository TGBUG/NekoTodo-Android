plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// 发布签名:密钥路径与口令一律从环境变量读,仓库里不存任何明文。
// 四个变量缺任一就不配置签名 —— 这样本地 `assembleRelease` 依然能跑(产出未签名包),
// 只有 CI(或你自己导出了这四个变量)才会真正签名。
val releaseKeystorePath = System.getenv("NEKOTODO_KEYSTORE_PATH")
val releaseKeystorePassword = System.getenv("NEKOTODO_KEYSTORE_PASSWORD")
val releaseKeyAlias = System.getenv("NEKOTODO_KEY_ALIAS")
val releaseKeyPassword = System.getenv("NEKOTODO_KEY_PASSWORD")
val signRelease = !releaseKeystorePath.isNullOrBlank() &&
    !releaseKeystorePassword.isNullOrBlank() &&
    !releaseKeyAlias.isNullOrBlank() &&
    !releaseKeyPassword.isNullOrBlank()

android {
    namespace = "cn.tgbug.nekotodo"
    // 最新 androidx 库要求 compileSdk >= 37；targetSdk 仍留在 36，不启用 Android 17 的运行时行为变更。
    compileSdk = 37

    defaultConfig {
        applicationId = "cn.tgbug.nekotodo"
        minSdk = 26
        targetSdk = 36
        // 版本号可由 CI 覆盖:每个 release 必须有**不同**的 versionCode 才能覆盖安装。
        versionCode = System.getenv("NEKOTODO_VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = System.getenv("NEKOTODO_VERSION_NAME") ?: "0.2.0"
    }

    signingConfigs {
        if (signRelease) {
            create("release") {
                // signRelease 已保证这四个值非空
                storeFile = file(releaseKeystorePath!!)
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            // 暂不开 R8:kotlinx.serialization 依赖保留规则,首次发版不冒这个险。
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (signRelease) {
                signingConfig = signingConfigs.getByName("release")
            }
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

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(platform(libs.compose.bom))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.retrofit)
    implementation(libs.retrofit.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    implementation(libs.reorderable)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
