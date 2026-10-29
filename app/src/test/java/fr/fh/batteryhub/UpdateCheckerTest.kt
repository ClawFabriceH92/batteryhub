package fr.fh.batteryhub

import fr.fh.batteryhub.update.UpdateChecker
import org.json.JSONArray
import org.junit.Assert.assertEquals
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
    fun version_extraite_du_titre_de_la_release_roulante() {
        assertEquals("1.2.3", UpdateChecker.extractVersion("BatteryHub v1.2.3"))
        assertNull(UpdateChecker.extractVersion("latest"))
    }

    @Test
    fun manifeste_version_superieure_donne_une_mise_a_jour() {
        val body = """{"version":"1.1.0","apk":"https://exemple/app.apk","notes":"corrections"}"""
        val resultat = UpdateChecker.parseManifest(body, "1.0.1")
        assertTrue(resultat is UpdateChecker.CheckResult.Available)
        val info = (resultat as UpdateChecker.CheckResult.Available).info
        assertEquals("1.1.0", info.version)
        assertEquals("https://exemple/app.apk", info.apkUrl)
        assertEquals("corrections", info.notes)
    }

    @Test
    fun manifeste_version_egale_ou_inferieure_donne_a_jour() {
        val body = """{"version":"1.0.1"}"""
        assertEquals(
            UpdateChecker.CheckResult.UpToDate,
            UpdateChecker.parseManifest(body, "1.0.1"),
        )
        assertEquals(
            UpdateChecker.CheckResult.UpToDate,
            UpdateChecker.parseManifest("""{"version":"1.0.0"}""", "1.0.1"),
        )
    }

    @Test
    fun manifeste_invalide_rend_null_pour_passer_a_l_api() {
        assertNull(UpdateChecker.parseManifest("pas du json", "1.0.0"))
        assertNull(UpdateChecker.parseManifest("""{"autre":1}""", "1.0.0"))
    }

    @Test
    fun api_lit_la_version_dans_le_tag_puis_dans_le_titre() {
        val body = """
            [
              {"tag_name":"v1.0.0","name":"BatteryHub 1.0.0","draft":false,
               "assets":[{"name":"batteryhub-v1.0.0.apk","browser_download_url":"https://exemple/100.apk"}]},
              {"tag_name":"latest","name":"BatteryHub v1.4.0","draft":false,
               "assets":[{"name":"app-release.apk","browser_download_url":"https://exemple/latest.apk"}]}
            ]
        """.trimIndent()
        val resultat = UpdateChecker.parseApi(body, "1.0.1")
        assertTrue(resultat is UpdateChecker.CheckResult.Available)
        assertEquals(
            "1.4.0",
            (resultat as UpdateChecker.CheckResult.Available).info.version,
        )
        assertEquals("https://exemple/latest.apk", resultat.info.apkUrl)
    }

    @Test
    fun api_ignore_les_brouillons_et_les_releases_sans_apk() {
        val body = """
            [
              {"tag_name":"v2.0.0","draft":true,
               "assets":[{"name":"a.apk","browser_download_url":"https://exemple/a.apk"}]},
              {"tag_name":"v1.9.0","draft":false,
               "assets":[{"name":"notes.txt","browser_download_url":"https://exemple/n.txt"}]}
            ]
        """.trimIndent()
        assertEquals(UpdateChecker.CheckResult.UpToDate, UpdateChecker.parseApi(body, "1.0.1"))
    }

    @Test
    fun choix_de_l_apk_privilegie_le_nom_portant_la_version() {
        val assets = JSONArray(
            """
            [{"name":"app-release.apk","browser_download_url":"https://exemple/canon.apk"},
             {"name":"batteryhub-v1.2.0.apk","browser_download_url":"https://exemple/120.apk"}]
            """.trimIndent()
        )
        assertEquals("https://exemple/120.apk", UpdateChecker.pickApk(assets, "1.2.0"))
    }

    @Test
    fun quota_epuise_est_explique() {
        assertTrue(UpdateChecker.httpReason(403, "0").contains("quota"))
        assertTrue(UpdateChecker.httpReason(404, null).contains("absente"))
    }

    @Test
    fun veredict_signale_une_reponse_de_github() {
        val verdict = UpdateChecker.failureVerdict(
            Result.failure(UpdateChecker.HttpStatusException(404, "ressource absente (HTTP 404)")),
            Result.failure(UpdateChecker.HttpStatusException(403, "accès refusé par GitHub (HTTP 403)")),
        )
        assertTrue(verdict.answered)
        assertEquals("accès refusé par GitHub (HTTP 403)", verdict.reason)
    }

    @Test
    fun veredict_distingue_une_panne_reseau() {
        val verdict = UpdateChecker.failureVerdict(
            Result.failure(java.net.UnknownHostException("api.github.com")),
            Result.failure(java.net.UnknownHostException("api.github.com")),
        )
        assertTrue(!verdict.answered)
        assertTrue(verdict.reason.contains("DNS"))
    }
}
