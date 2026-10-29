package fr.fh.batteryhub.data

enum class TypeAppareil { TELEPHONE, ECOUTEURS, MONTRE, SOURIS, CLAVIER, ENCEINTE, AUTRE }

/** Un appareil et son dernier niveau de batterie connu. */
data class Appareil(
    val adresse: String,
    val nom: String,
    val type: TypeAppareil,
    val connecte: Boolean,
    val niveau: Int,
    val detail: String,
    val horodatage: Long,
    val lectureAuto: Boolean = true,
)

object Adresses {
    const val TELEPHONE = "telephone"
}
