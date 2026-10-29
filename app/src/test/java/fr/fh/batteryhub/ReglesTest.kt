package fr.fh.batteryhub

import fr.fh.batteryhub.data.Regles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReglesTest {

    @Test
    fun categorie_respecte_les_seuils() {
        assertEquals("ko", Regles.categorie(0))
        assertEquals("ko", Regles.categorie(15))
        assertEquals("warn", Regles.categorie(16))
        assertEquals("warn", Regles.categorie(30))
        assertEquals("ok", Regles.categorie(31))
        assertEquals("ok", Regles.categorie(100))
    }

    @Test
    fun sousSeuil_est_inclusif() {
        assertTrue(Regles.sousSeuil(20, 20))
        assertTrue(Regles.sousSeuil(12, 20))
        assertFalse(Regles.sousSeuil(21, 20))
    }

    @Test
    fun frequence_invalide_revient_au_defaut() {
        assertEquals(30, Regles.frequenceValide(7))
        assertEquals(15, Regles.frequenceValide(15))
        assertEquals(360, Regles.frequenceValide(360))
        assertEquals("30 min", Regles.libelleFrequence(30))
        assertEquals("1 h", Regles.libelleFrequence(60))
        assertEquals("6 h", Regles.libelleFrequence(360))
    }

    @Test
    fun ligneCsv_utilise_le_point_virgule_et_neutralise_le_nom() {
        val ligne = Regles.ligneCsv("AA:BB", "Casque; Pro", "ECOUTEURS", 42, 0L)
        assertEquals(5, ligne.split(";").size)
        assertTrue(ligne.startsWith("AA:BB;Casque, Pro;ECOUTEURS;42;"))
    }

    @Test
    fun nomFichierCsv_porte_la_version() {
        val nom = Regles.nomFichierCsv(0L, "1.0.0")
        assertTrue(nom.startsWith("batteryhub_"))
        assertTrue(nom.endsWith("_v1.0.0.csv"))
    }

    @Test
    fun muet_apres_trois_jours() {
        val maintenant = 10L * 86_400_000L
        assertTrue(Regles.muet(0L, maintenant))
        assertFalse(Regles.muet(maintenant - 86_400_000L, maintenant))
    }

    @Test
    fun tenue_estimee_formate_heures_et_minutes() {
        assertEquals("4 h 00", Regles.tenueEstimee(4.0, 60))
        assertEquals("30 min", Regles.tenueEstimee(1.0, 30))
    }
}
