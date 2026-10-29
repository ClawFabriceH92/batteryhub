package fr.fh.batteryhub

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Garde-fou : le bouton « Installer » ne fait rien si le dossier où l'APK de
 * mise à jour est écrit n'est pas déclaré dans res/xml/file_paths.xml, parce que
 * FileProvider.getUriForFile lève alors une exception avalée sans message.
 */
class CheminsPartagesTest {

    private val chemins: String by lazy {
        File("src/main/res/xml/file_paths.xml").readText()
    }

    @Test
    fun le_fichier_de_chemins_existe() {
        assertTrue(
            "res/xml/file_paths.xml introuvable depuis ${File(".").absolutePath}",
            File("src/main/res/xml/file_paths.xml").exists(),
        )
    }

    @Test
    fun le_dossier_des_apk_de_mise_a_jour_est_declare() {
        // UpdateManager écrit dans filesDir/updates
        assertTrue(
            "le dossier « updates » doit être exposé par un <files-path>",
            chemins.contains("files-path") && chemins.contains("path=\"updates/\""),
        )
    }

    @Test
    fun les_documents_exportes_restent_partages() {
        assertTrue(chemins.contains("path=\"Documents/\"") || chemins.contains("path=\"exports/\""))
    }
}
