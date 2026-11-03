import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val secrets = Properties().apply {
    val f = rootProject.file("vps-secrets.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun secret(key: String, default: String = ""): String =
    (secrets.getProperty(key) ?: default).replace("\\", "\\\\").replace("\"", "\\\"")

android {
    namespace = "ru.srr.vpscockpit"
    compileSdk = 35
    buildToolsVersion = "35.0.1"

    defaultConfig {
        applicationId = "ru.srr.vpscockpit"
        minSdk = 26
        targetSdk = 35

        val appVersionName = "1.3.0"
        versionCode = 28
        versionName = appVersionName
        resValue("string", "app_name", "VPS Cockpit")
        resValue("string", "app_version", appVersionName)

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("boolean", "PUBLIC_BUILD", "false")
        buildConfigField("String", "VPS_HOST", "\"\"")
        buildConfigField("String", "VPS_USER", "\"root\"")
        buildConfigField("String", "VPS_PASSWORD", "\"\"")
        buildConfigField("int", "VPS_REMOTE_PORT", "9090")
        buildConfigField("int", "VPS_LOCAL_PORT", "19090")
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("personal") {
            dimension = "distribution"
            applicationId = "ru.srr.vpscockpit"
            resValue("string", "app_name", "VPS Cockpit")
            buildConfigField("boolean", "PUBLIC_BUILD", "false")
            buildConfigField("String", "VPS_HOST", "\"${secret("vps.host")}\"")
            buildConfigField("String", "VPS_USER", "\"${secret("vps.user", "root")}\"")
            buildConfigField("String", "VPS_PASSWORD", "\"${secret("vps.password")}\"")
            buildConfigField("int", "VPS_REMOTE_PORT", secret("vps.remote_port", "9090").ifBlank { "9090" })
            buildConfigField("int", "VPS_LOCAL_PORT", secret("vps.local_port", "19090").ifBlank { "19090" })
        }
        create("community") {
            dimension = "distribution"
            applicationId = "ru.srr.vpscockpit.community"
            resValue("string", "app_name", "VPS Cockpit Community")
            buildConfigField("boolean", "PUBLIC_BUILD", "true")
            // No personal host/user/password in this flavor
            buildConfigField("String", "VPS_HOST", "\"\"")
            buildConfigField("String", "VPS_USER", "\"\"")
            buildConfigField("String", "VPS_PASSWORD", "\"\"")
            buildConfigField("int", "VPS_REMOTE_PORT", "9090")
            buildConfigField("int", "VPS_LOCAL_PORT", "19090")
        }
    }

    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1,INDEX.LIST,DEPENDENCIES}"
            excludes += "/META-INF/*.kotlin_module"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("com.github.mwiede:jsch:0.2.21")
}
