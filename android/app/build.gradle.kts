import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    // 固定签名：keystore 随仓库提交，保证跨 runner 的版本可覆盖安装；密码由 CI 注入。
    val keystoreFile = file("keystore.jks")
    val signingProps = Properties()
    val propFile = file("signing.properties")
    if (propFile.exists()) propFile.inputStream().use { signingProps.load(it) }
    val hasKeystore = keystoreFile.exists() && signingProps.getProperty("storePassword") != null

    signingConfigs {
        create("release") {
            storeFile = keystoreFile
            storePassword = signingProps.getProperty("storePassword")
            keyAlias = signingProps.getProperty("keyAlias")
            keyPassword = signingProps.getProperty("keyPassword")
        }
    }

    namespace = "com.xb18.webbox"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.xb18.webbox"
        minSdk = 21
        targetSdk = 35

        val shellVersionName: String by project
        val shellVersionCode: String by project
        val webUrl: String by project
        val updateRepo: String by project
        versionName = shellVersionName
        versionCode = shellVersionCode.toInt()

        // 网址写进 BuildConfig，Kotlin 侧直接引用，避免散落硬编码。
        buildConfigField("String", "WEB_URL", "\"${webUrl}\"")
        buildConfigField("String", "UPDATE_REPO", "\"${updateRepo}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            if (hasKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // 仅用于 FileProvider 安全分享 APK 给系统安装器，避免 file:// Uri 暴露异常。
    implementation("androidx.core:core-ktx:1.13.1")
}
