package fr.fh.batteryhub

import fr.fh.batteryhub.data.Appareil
import fr.fh.batteryhub.data.TypeAppareil
import fr.fh.batteryhub.widget.Instantane
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InstantaneTest {

    @Test
    fun aller_retour_json_conserve_les_champs() {
        val appareils = listOf(
            Appareil("AA:BB", "Casque", TypeAppareil.ECOUTEURS, true, 74, "Connecté", 1234L, true),
            Appareil("CC:DD", "Souris", TypeAppareil.SOURIS, false, 12, "Hors de portée", 5678L, false),
        )
        val relu = Instantane.depuisJson(Instantane.versJson(appareils))
        assertEquals(2, relu.size)
        assertEquals("Casque", relu[0].nom)
        assertEquals(74, relu[0].niveau)
        assertEquals(TypeAppareil.SOURIS, relu[1].type)
        assertEquals(false, relu[1].connecte)
        assertEquals(false, relu[1].lectureAuto)
    }

    @Test
    fun json_illisible_donne_une_liste_vide() {
        assertTrue(Instantane.depuisJson("pas du json").isEmpty())
        assertTrue(Instantane.depuisJson("[]").isEmpty())
    }
}
