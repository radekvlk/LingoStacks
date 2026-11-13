plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.gms.google-services")
}

android {
    namespace = "cz.slovickaspeak.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "cz.slovickaspeak.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 68
        versionName = "1.6.47"

        vectorDrawables {
            useSupportLibrary = true
        }

        resourceConfigurations += listOf("cs", "en", "es", "de", "fr", "it", "pt")
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("github") {
            dimension = "distribution"
            buildConfigField("Boolean", "IS_PLAY_STORE", "false")
            buildConfigField("Boolean", "IS_BETA", "false")
            buildConfigField("Boolean", "ENABLE_OTA_UPDATES", "true")
            buildConfigField("Boolean", "SHOW_KOFI_DONATE", "true")
        }
        create("beta") {
            dimension = "distribution"
            applicationIdSuffix = ".beta"
            versionNameSuffix = "-beta2"
            buildConfigField("Boolean", "IS_PLAY_STORE", "false")
            buildConfigField("Boolean", "IS_BETA", "true")
            buildConfigField("Boolean", "ENABLE_OTA_UPDATES", "true")
            buildConfigField("Boolean", "SHOW_KOFI_DONATE", "false")
        }
        create("playstore") {
            dimension = "distribution"
            buildConfigField("Boolean", "IS_PLAY_STORE", "true")
            buildConfigField("Boolean", "IS_BETA", "false")
            buildConfigField("Boolean", "ENABLE_OTA_UPDATES", "false")
            buildConfigField("Boolean", "SHOW_KOFI_DONATE", "false")
        }
    }

    signingConfigs {
        create("lingowords") {
            storeFile = file("lingowords.keystore")
            storePassword = project.findProperty("LINGOWORDS_STORE_PASS")?.toString() ?: "lingowords2026"
            keyAlias = "lingowords"
            keyPassword = project.findProperty("LINGOWORDS_KEY_PASS")?.toString() ?: "lingowords2026"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("lingowords")
        }
        release {
            signingConfig = signingConfigs.getByName("lingowords")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/*.version"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/LICENSE*"
            excludes += "/META-INF/NOTICE*"
            excludes += "/META-INF/*.kotlin_module"
        }
    }

    applicationVariants.all {
        val variant = this
        outputs.all {
            val output = this as? com.android.build.gradle.internal.api.BaseVariantOutputImpl
            val flavor = variant.flavorName
            when (flavor) {
                "playstore" -> output?.outputFileName = "LingoStacks_v${variant.versionName}_playstore.apk"
                "beta" -> output?.outputFileName = "LingoStacks_beta.apk"
                else -> output?.outputFileName = "LingoStacks_v${variant.versionName}.apk"
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(platform("com.google.firebase:firebase-bom:34.5.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.android.gms:play-services-auth:21.3.0")
    testImplementation("junit:junit:4.13.2")
    debugImplementation(libs.androidx.ui.tooling)
}
