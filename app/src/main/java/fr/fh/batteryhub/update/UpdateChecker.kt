package fr.fh.batteryhub.update

import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val versionName: String,
    val downloadUrl: String,
    val notes: String?,
    val publishedAt: String?,
)

/** Vérifie la dernière release GitHub portant un APK. */
object UpdateChecker {

    private const val RELEASES_URL = "https://api.github.com/repos/ClawFabriceH92/batteryhub/releases?per_page=5"

    fun latestWithApk(): UpdateInfo? {
        val conn = try {
            URL(RELEASES_URL).openConnection() as HttpURLConnection
        } catch (_: Throwable) {
            return null
        }
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.setRequestProperty("User-Agent", "BatteryHub-UpdateChecker")
        return try {
            if (conn.responseCode != 200) {
                null
            } else {
                parseReleases(conn.inputStream.bufferedReader().use { it.readText() })
            }
        } catch (_: Throwable) {
            null
        } finally {
            try { conn.disconnect() } catch (_: Throwable) {}
        }
    }

    internal fun parseReleases(jsonText: String): UpdateInfo? = try {
        val releases = JSONArray(jsonText)
        var meilleure: UpdateInfo? = null
        for (i in 0 until releases.length()) {
            val rel = releases.getJSONObject(i)
            if (rel.optBoolean("draft")) continue
            val tag = rel.optString("tag_name", "").removePrefix("v")
            if (tag.isBlank()) continue
            val assets = rel.optJSONArray("assets") ?: continue
            for (j in 0 until assets.length()) {
                val asset = assets.getJSONObject(j)
                val nom = asset.optString("name", "")
                if (!nom.endsWith(".apk")) continue
                val url = asset.optString("browser_download_url", "")
                if (url.isEmpty()) continue
                val info = UpdateInfo(
                    versionName = tag,
                    downloadUrl = url,
                    notes = rel.optString("body").takeIf { it.isNotBlank() },
                    publishedAt = rel.optString("published_at").takeIf { it.isNotBlank() },
                )
                if (meilleure == null || compareVersions(info.versionName, meilleure.versionName) > 0) {
                    meilleure = info
                }
                break
            }
        }
        meilleure
    } catch (_: Throwable) {
        null
    }

    fun compareVersions(a: String, b: String): Int {
        val sa = a.trim().split(".").map { it.toIntOrNull() ?: 0 }
        val sb = b.trim().split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(sa.size, sb.size)) {
            val cmp = sa.getOrElse(i) { 0 }.compareTo(sb.getOrElse(i) { 0 })
            if (cmp != 0) return cmp
        }
        return 0
    }

    fun miseAJourDisponible(versionInstallee: String, distante: UpdateInfo?): Boolean =
        distante != null && compareVersions(distante.versionName, versionInstallee) > 0
}
