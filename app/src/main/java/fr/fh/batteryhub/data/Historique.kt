package fr.fh.batteryhub.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** Base locale des relevés. Aucune donnée ne quitte le téléphone. */
class Historique(context: Context) :
    SQLiteOpenHelper(context, "batteryhub.db", null, 2) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE releves(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "adresse TEXT NOT NULL," +
                "nom TEXT NOT NULL," +
                "type TEXT NOT NULL," +
                "niveau INTEGER NOT NULL," +
                "horodatage INTEGER NOT NULL," +
                "courant_ma INTEGER," +
                "secteur INTEGER," +
                "temperature REAL," +
                "charge_mah INTEGER)"
        )
        db.execSQL("CREATE INDEX idx_releves ON releves(adresse, horodatage)")
    }

    /**
     * Migration 1 → 2 : les colonnes de courant, secteur, température et charge
     * restante, qui servent au suivi de charge/décharge. Aucune donnée n'est
     * touchée : les anciens relevés gardent des colonnes vides, et l'intégration
     * du courant les ignore au lieu de les compter faux.
     */
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE releves ADD COLUMN courant_ma INTEGER")
            db.execSQL("ALTER TABLE releves ADD COLUMN secteur INTEGER")
            db.execSQL("ALTER TABLE releves ADD COLUMN temperature REAL")
            db.execSQL("ALTER TABLE releves ADD COLUMN charge_mah INTEGER")
        }
    }

    fun enregistrer(appareils: List<Appareil>, etat: Echantillon? = null) {
        if (appareils.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            appareils.forEach { a ->
                val estTelephone = a.adresse == Adresses.TELEPHONE
                val v = ContentValues().apply {
                    put("adresse", a.adresse)
                    put("nom", a.nom)
                    put("type", a.type.name)
                    put("niveau", a.niveau)
                    put("horodatage", a.horodatage)
                    if (estTelephone && etat != null) {
                        etat.courantMa?.let { put("courant_ma", it) }
                        put("secteur", if (etat.secteur) 1 else 0)
                        etat.temperatureC?.let { put("temperature", it) }
                        etat.chargeRestanteMah?.let { put("charge_mah", it) }
                    }
                }
                db.insert("releves", null, v)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** Points (horodatage, niveau) pour un appareil depuis [depuis]. */
    fun serie(adresse: String, depuis: Long): List<Pair<Long, Int>> {
        val out = ArrayList<Pair<Long, Int>>()
        readableDatabase.rawQuery(
            "SELECT horodatage, niveau FROM releves WHERE adresse = ? AND horodatage >= ? ORDER BY horodatage",
            arrayOf(adresse, depuis.toString())
        ).use { c ->
            while (c.moveToNext()) out.add(c.getLong(0) to c.getInt(1))
        }
        return out
    }

    /** Relevés enrichis de la batterie interne, pour les statistiques. */
    fun echantillons(depuis: Long, adresse: String = Adresses.TELEPHONE): List<Echantillon> {
        val out = ArrayList<Echantillon>()
        readableDatabase.rawQuery(
            "SELECT horodatage, niveau, courant_ma, secteur, temperature, charge_mah " +
                "FROM releves WHERE adresse = ? AND horodatage >= ? ORDER BY horodatage",
            arrayOf(adresse, depuis.toString())
        ).use { c ->
            while (c.moveToNext()) {
                out.add(
                    Echantillon(
                        horodatage = c.getLong(0),
                        niveau = c.getInt(1),
                        courantMa = if (c.isNull(2)) null else c.getInt(2),
                        secteur = !c.isNull(3) && c.getInt(3) == 1,
                        temperatureC = if (c.isNull(4)) null else c.getDouble(4),
                        chargeRestanteMah = if (c.isNull(5)) null else c.getInt(5),
                    )
                )
            }
        }
        return out
    }

    fun compter(adresse: String): Int {
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM releves WHERE adresse = ?", arrayOf(adresse)
        ).use { c -> return if (c.moveToFirst()) c.getInt(0) else 0 }
    }

    fun totalReleves(): Int {
        readableDatabase.rawQuery("SELECT COUNT(*) FROM releves", null).use { c ->
            return if (c.moveToFirst()) c.getInt(0) else 0
        }
    }

    fun purger(jours: Int = Regles.RETENTION_JOURS) {
        val limite = System.currentTimeMillis() - jours * 86_400_000L
        writableDatabase.delete("releves", "horodatage < ?", arrayOf(limite.toString()))
    }

    fun toutExporterCsv(): String {
        val sb = StringBuilder()
        sb.append('\uFEFF')
        sb.append(Regles.enteteCsv()).append("\n")
        readableDatabase.rawQuery(
            "SELECT adresse, nom, type, niveau, horodatage FROM releves ORDER BY horodatage",
            null
        ).use { c ->
            while (c.moveToNext()) {
                sb.append(
                    Regles.ligneCsv(
                        c.getString(0), c.getString(1), c.getString(2), c.getInt(3), c.getLong(4)
                    )
                ).append("\n")
            }
        }
        return sb.toString()
    }
}
