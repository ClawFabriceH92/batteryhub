package fr.fh.batteryhub

import fr.fh.batteryhub.update.UpdateChecker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    @Test
    fun comparaison_segment_par_segment() {
        assertTrue(UpdateChecker.compareVersions("0.10.0", "0.9.0") > 0)
        assertTrue(UpdateChecker.compareVersions("1.0.0", "0.9.9") > 0)
        assertTrue(UpdateChecker.compareVersions("1.0.0", "1.0.1") < 0)
        assertEquals(0, UpdateChecker.compareVersions("1.0.0", "1.0.0"))
        assertEquals(0, UpdateChecker.compareVersions("1.0", "1.0.0"))
    }

    @Test
    fun parse_prend_la_version_la_plus_haute_avec_apk() {
        val json = """
            [
              {"tag_name":"v1.0.0","draft":false,"assets":[{"name":"batteryhub-v1.0.0.apk","browser_download_url":"https://exemple/1.apk"}]},
              {"tag_name":"v1.2.0","draft":false,"assets":[{"name":"batteryhub-v1.2.0.apk","browser_download_url":"https://exemple/12.apk"}]},
              {"tag_name":"v1.3.0","draft":true,"assets":[{"name":"batteryhub-v1.3.0.apk","browser_download_url":"https://exemple/13.apk"}]}
            ]
        """.trimIndent()
        val info = UpdateChecker.parseReleases(json)
        assertNotNull(info)
        assertEquals("1.2.0", info!!.versionName)
        assertEquals("https://exemple/12.apk", info.downloadUrl)
    }

    @Test
    fun parse_ignore_les_releases_sans_apk() {
        val json = """
            [{"tag_name":"v2.0.0","draft":false,"assets":[{"name":"notes.txt","browser_download_url":"https://exemple/n.txt"}]}]
        """.trimIndent()
        assertNull(UpdateChecker.parseReleases(json))
    }

    @Test
    fun parse_json_invalide_ne_plante_pas() {
        assertNull(UpdateChecker.parseReleases("pas du json"))
    }

    @Test
    fun mise_a_jour_disponible_compare_a_la_version_installee() {
        val info = UpdateChecker.parseReleases(
            """[{"tag_name":"v1.1.0","draft":false,"assets":[{"name":"a.apk","browser_download_url":"https://exemple/a.apk"}]}]"""
        )
        assertTrue(UpdateChecker.miseAJourDisponible("1.0.0", info))
        assertTrue(!UpdateChecker.miseAJourDisponible("1.1.0", info))
    }
}
