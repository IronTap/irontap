import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
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
        // Version affichée par l'écran À propos (spec §4.6).
        buildConfig = true
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

/**
 * Textes de licence embarqués dans `assets/licences/` (écran À propos, spec §4.6), lus hors ligne : licence d'IronTap,
 * Inter (OFL), Phosphor (MIT), Apache 2.0, BSD 3 clauses (protobuf), et la liste des bibliothèques réellement
 * embarquées dans la variante avec leur licence et leur titulaire. Une bibliothèque d'un groupe inconnu arrête la
 * compilation : vérifier sa licence puis compléter [TITULAIRES] (Apache 2.0) ou [PARTICULIERES]. Le test
 * `AProposTest` compare ces licences à celles déclarées par les POM. Sortie triée : builds reproductibles.
 */
abstract class PreparerLicences : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val licenceIronTap: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val licenceInter: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val licencePhosphor: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val licenceApache: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val licenceProtobuf: RegularFileProperty

    /** Graphe des dépendances d'exécution de la variante. */
    @get:Input
    abstract val dependances: Property<ResolvedComponentResult>

    @get:OutputDirectory
    abstract val sortie: DirectoryProperty

    @TaskAction
    fun preparer() {
        val dossier = sortie.get().dir("licences").asFile
        dossier.deleteRecursively()
        dossier.mkdirs()
        licenceIronTap.get().asFile.copyTo(File(dossier, "irontap-LICENSE.txt"))
        licenceInter.get().asFile.copyTo(File(dossier, "inter-OFL.txt"))
        licencePhosphor.get().asFile.copyTo(File(dossier, "phosphor-MIT.txt"))
        licenceApache.get().asFile.copyTo(File(dossier, "apache-2.0.txt"))
        licenceProtobuf.get().asFile.copyTo(File(dossier, "protobuf-BSD-3-Clause.txt"))

        val composants = LinkedHashSet<ResolvedComponentResult>()
        fun parcourir(composant: ResolvedComponentResult) {
            if (!composants.add(composant)) return
            composant.dependencies.filterIsInstance<ResolvedDependencyResult>().forEach { parcourir(it.selected) }
        }
        parcourir(dependances.get())
        val lignes = composants
            // Les BOM (plateformes) ne sont pas embarquées : elles ne fixent que des versions.
            .filterNot { composant -> composant.variants.isNotEmpty() && composant.variants.all { categorie(it.attributes) == "platform" } }
            .mapNotNull { it.id as? ModuleComponentIdentifier }
            .map { id ->
                val (licence, titulaire) = PARTICULIERES["${id.group}:${id.module}"]
                    ?: TITULAIRES.entries.firstOrNull { (groupe, _) -> id.group == groupe || id.group.startsWith("$groupe.") }?.let { "Apache-2.0" to it.value }
                    ?: throw GradleException(
                        "Bibliothèque sans titulaire connu : ${id.displayName}. Vérifier sa licence et compléter TITULAIRES ou PARTICULIERES (app/build.gradle.kts).",
                    )
                "$licence\t$titulaire\t${id.group}:${id.module}\t${id.version}"
            }
            .sorted()
        File(dossier, "bibliotheques.txt").writeText(lignes.joinToString("\n", postfix = "\n"))
    }

    private fun categorie(attributs: AttributeContainer): String? =
        attributs.keySet().firstOrNull { it.name == "org.gradle.category" }?.let { attributs.getAttribute(it)?.toString() }

    companion object {
        /** Bibliothèques hors Apache 2.0, par module → (licence SPDX, titulaire) : prioritaires sur [TITULAIRES]. */
        val PARTICULIERES = mapOf(
            // Copie de protobuf-javalite embarquée par DataStore Preferences.
            "androidx.datastore:datastore-preferences-external-protobuf" to ("BSD-3-Clause" to "Google Inc."),
        )

        /** Groupe Maven (et ses sous-groupes) → titulaire du copyright, pour les bibliothèques sous Apache 2.0. */
        val TITULAIRES = linkedMapOf(
            "androidx" to "The Android Open Source Project",
            "org.jetbrains.kotlin" to "JetBrains s.r.o. and Kotlin Programming Language contributors",
            "org.jetbrains.kotlinx" to "JetBrains s.r.o. and contributors",
            "org.jetbrains" to "JetBrains s.r.o.",
            "com.squareup.okio" to "Square, Inc.",
            "com.google.guava" to "The Guava Authors",
            "org.jspecify" to "The JSpecify Authors",
        )
    }
}

androidComponents {
    onVariants { variant ->
        val tache = tasks.register<PreparerLicences>("preparerLicences${variant.name.replaceFirstChar { it.uppercase() }}") {
            licenceIronTap.set(rootProject.layout.projectDirectory.file("LICENSE"))
            licenceInter.set(rootProject.layout.projectDirectory.file("licences/Inter-OFL.txt"))
            licencePhosphor.set(rootProject.layout.projectDirectory.file("licences/Phosphor-MIT.txt"))
            licenceApache.set(rootProject.layout.projectDirectory.file("licences/Apache-2.0.txt"))
            licenceProtobuf.set(rootProject.layout.projectDirectory.file("licences/Protobuf-BSD-3-Clause.txt"))
            dependances.set(variant.runtimeConfiguration.incoming.resolutionResult.rootComponent)
        }
        variant.sources.assets?.addGeneratedSourceDirectory(tache, PreparerLicences::sortie)
    }
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
