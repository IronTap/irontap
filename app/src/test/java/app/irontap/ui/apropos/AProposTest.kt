package app.irontap.ui.apropos

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import app.irontap.BuildConfig
import app.irontap.logique.BlocLicence
import app.irontap.logique.lireBibliotheques
import app.irontap.ui.IronTapApp
import app.irontap.ui.theme.IronTapTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Écran À propos (10a, 10b, spec §4.6) : textes embarqués, lien du code source, navigation, captures. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h750dp-xxhdpi")
class AProposTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `chaque licence est embarquee et lisible hors ligne`() {
        ComposantLicence.entries.forEach { composant ->
            val blocs = lireLicence(context.assets, composant)
            assertTrue(composant.name, blocs.size >= 3)
            assertTrue(composant.name, blocs.first() is BlocLicence.Copyright)
        }
        assertEquals(BlocLicence.Copyright("Copyright © 2023 Phosphor Icons"), lireLicence(context.assets, ComposantLicence.Phosphor).first())
        val gpl = lireLicence(context.assets, ComposantLicence.IronTap)
        listOf("TERMS AND CONDITIONS", "Preamble", "0. Definitions.", "17. Interpretation of Sections 15 and 16.")
            .forEach { assertTrue(it, gpl.contains(BlocLicence.Titre(it))) }
        // Protobuf (BSD 3 clauses) : sa mention de copyright est reproduite.
        assertTrue(
            lireLicence(context.assets, ComposantLicence.Bibliotheques)
                .any { it is BlocLicence.Paragraphe && it.texte.startsWith("Copyright 2008 Google Inc.") && "All rights reserved." in it.texte },
        )
        assertTrue(lireLicence(context.assets, ComposantLicence.Inter).contains(BlocLicence.Titre("PREAMBLE")))
    }

    @Test
    fun `liste des bibliotheques generee depuis les dependances du build`() {
        val bibliotheques = lireBibliotheques(context.assets.open("licences/bibliotheques.txt").bufferedReader().readText())
        val modules = bibliotheques.map { it.module }
        listOf("androidx.room:room-runtime", "androidx.compose.material3:material3", "org.jetbrains.kotlin:kotlin-stdlib", "androidx.datastore:datastore-preferences")
            .forEach { assertTrue(it, it in modules) }
        // Les BOM ne fixent que des versions : elles ne sont pas listées. Aucun outil de test non plus.
        assertFalse(modules.any { it.endsWith("-bom") })
        assertFalse(modules.any { it.startsWith("junit") || it.startsWith("org.robolectric") })
        assertEquals(modules.sorted().distinct().size, modules.size)
    }

    /**
     * Garde-fou des licences : chaque bibliothèque listée l'est avec la licence que déclare son POM (dans le cache
     * Gradle, parents compris). Une nouvelle dépendance hors Apache 2.0 sous un groupe connu fait échouer ce test.
     */
    @Test
    fun `licences listees conformes aux POM`() {
        val cache = File(System.getProperty("user.home"), ".gradle/caches/modules-2/files-2.1")
        assumeTrue("Cache Gradle absent", cache.isDirectory)
        val bibliotheques = lireBibliotheques(context.assets.open("licences/bibliotheques.txt").bufferedReader().readText())
        val ecarts = bibliotheques.mapNotNull { biblio ->
            val (groupe, module) = biblio.module.split(':')
            val declaree = licencePom(cache, groupe, module, biblio.version) ?: return@mapNotNull "${biblio.module} : POM sans licence"
            "${biblio.module} : POM « $declaree », listée ${biblio.licence}".takeUnless { spdx(declaree) == biblio.licence }
        }
        assertEquals(emptyList<String>(), ecarts)
    }

    /** Nom de licence du POM de ce module, ou de ses parents. */
    private fun licencePom(cache: File, groupe: String, module: String, version: String): String? {
        val pom = File(cache, "$groupe/$module/$version").walk().firstOrNull { it.name.endsWith(".pom") }?.readText() ?: return null
        Regex("<licenses>.*?<name>(.*?)</name>", RegexOption.DOT_MATCHES_ALL).find(pom)?.let { return it.groupValues[1].trim() }
        val parent = Regex("<parent>.*?<groupId>(.*?)</groupId>.*?<artifactId>(.*?)</artifactId>.*?<version>(.*?)</version>", RegexOption.DOT_MATCHES_ALL)
            .find(pom) ?: return null
        return licencePom(cache, parent.groupValues[1], parent.groupValues[2], parent.groupValues[3])
    }

    private fun spdx(nom: String) = when {
        "Apache" in nom && "2.0" in nom -> "Apache-2.0"
        nom == "BSD-3-Clause" -> "BSD-3-Clause"
        else -> nom
    }

    @Test
    fun `aucune permission Internet`() {
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        assertFalse(info.requestedPermissions.orEmpty().contains(Manifest.permission.INTERNET))
    }

    @Test
    fun `code source ouvre le depot dans le navigateur`() {
        compose.setContent { IronTapTheme { EcranAPropos(onRetour = {}, onLicence = {}) } }
        compose.onNode(hasText("Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")).assertIsDisplayed()
        compose.onNode(hasClickAction() and hasText("Code source", substring = true)).performClick()
        val intention = shadowOf(compose.activity).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, intention.action)
        assertEquals("https://github.com/IronTap/irontap", intention.dataString)
    }

    @Test
    fun `de l'accueil a une licence, puis retour Android`() {
        compose.setContent { IronTapApp() }
        val aPropos = hasContentDescription("À propos") and hasClickAction()
        compose.waitUntil(5_000) { compose.onAllNodes(aPropos).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(aPropos).performClick()
        compose.onNode(hasClickAction() and hasText("Phosphor Icons", substring = true)).performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Copyright © 2023 Phosphor Icons")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(isHeading() and hasText("Phosphor Icons")).assertIsDisplayed()

        compose.activity.onBackPressedDispatcher.onBackPressed()
        compose.onNode(hasText("LICENCES TIERCES")).assertIsDisplayed()
        compose.activity.onBackPressedDispatcher.onBackPressed()
        compose.onNode(hasText("Entraînements")).assertIsDisplayed()
    }

    /** 10a. */
    @Test
    fun aPropos() {
        compose.setContent { IronTapTheme { ContenuAPropos("Version 1.0.0 (1)", onRetour = {}, onCodeSource = {}, onLicence = {}) } }
        compose.onNode(hasContentDescription("Retour")).assertIsDisplayed()
        compose.onRoot().captureRoboImage()
    }

    /** 10b : Inter, texte intégral défilant. */
    @Test
    fun licenceInter() {
        val blocs = lireLicence(context.assets, ComposantLicence.Inter)
        compose.setContent { IronTapTheme { ContenuLicence(ComposantLicence.Inter, blocs, onRetour = {}) } }
        compose.onRoot().captureRoboImage()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("DISCLAIMER"))
        compose.onNode(hasText("DISCLAIMER")).assertIsDisplayed()
    }
}
