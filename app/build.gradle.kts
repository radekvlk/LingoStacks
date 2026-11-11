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
        versionCode = 60
        versionName = "1.6.40"

        vectorDrawables {
            useSupportLibrary = true
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

    applicationVariants.all {
        val variant = this
        outputs.all {
            val output = this as? com.android.build.gradle.internal.api.BaseVariantOutputImpl
            output?.outputFileName = "LingoWords_v${variant.versionName}.apk"
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
