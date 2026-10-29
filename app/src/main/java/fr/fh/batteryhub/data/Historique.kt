package fr.fh.batteryhub.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** Base locale des relevés. Aucune donnée ne quitte le téléphone. */
class Historique(context: Context) :
    SQLiteOpenHelper(context, "batteryhub.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE releves(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "adresse TEXT NOT NULL," +
                "nom TEXT NOT NULL," +
                "type TEXT NOT NULL," +
                "niveau INTEGER NOT NULL," +
                "horodatage INTEGER NOT NULL)"
        )
        db.execSQL("CREATE INDEX idx_releves ON releves(adresse, horodatage)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // aucune migration destructive : la table est conservée telle quelle
    }

    fun enregistrer(appareils: List<Appareil>) {
        if (appareils.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            appareils.forEach { a ->
                val v = ContentValues().apply {
                    put("adresse", a.adresse)
                    put("nom", a.nom)
                    put("type", a.type.name)
                    put("niveau", a.niveau)
                    put("horodatage", a.horodatage)
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
