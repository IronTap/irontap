import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    alias(libs.plugins.roborazzi)
}

// Signature des versions publiées : keystore.properties à la racine, hors dépôt (cf. PUBLIER.md).
// Sans ce fichier, l'APK release n'est pas signé, comme sur le serveur de compilation d'F-Droid.
val signature = rootProject.file("keystore.properties").takeIf { it.isFile }?.let { fichier ->
    Properties().apply { fichier.inputStream().use { load(it) } }
}

android {
    namespace = "app.irontap"
    compileSdk = 37

    defaultConfig {
        // Tiré du domaine irontap.fr ; le namespace (paquets Kotlin) reste app.irontap.
        applicationId = "fr.irontap"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        if (signature != null) {
            create("release") {
                storeFile = rootProject.file(signature.getProperty("storeFile"))
                storePassword = signature.getProperty("storePassword")
                keyAlias = signature.getProperty("keyAlias")
                keyPassword = signature.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // Paquet distinct : la version de développement coexiste avec la version signée, sans partager ses données.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    // F-Droid refuse ce bloc (liste des dépendances chiffrée pour Google, ajoutée à la signature) :
    // sans lui, l'APK publié peut aussi être recompilé à l'identique (builds reproductibles).
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    androidResources {
        // Application en français uniquement : on écarte les traductions embarquées par les bibliothèques.
        localeFilters += "fr"
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        // Ressources Android (polices, drawables) disponibles dans les tests JVM Robolectric.
        unitTests.isIncludeAndroidResources = true
        // Robolectric sur JDK 17+ : accès aux internes du JDK (cf. robolectric.org/getting-started).
        unitTests.all {
            it.jvmArgs(
                "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
                "--add-opens=java.base/java.io=ALL-UNNAMED",
                "--add-opens=java.base/java.lang=ALL-UNNAMED",
            )
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.core)

    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    // Room en mémoire sur JVM (Robolectric).
    testImplementation(libs.androidx.test.core)

    // Captures d'écran sur JVM (Robolectric + Roborazzi) : pas d'émulateur dans WSL.
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
