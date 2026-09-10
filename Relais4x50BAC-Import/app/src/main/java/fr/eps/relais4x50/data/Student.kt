package fr.eps.relais4x50.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Un élève tel qu'importé depuis l'onglet "Liste" du fichier Relais-BAC_4X50.xlsx :
 * Classe | Eq | Nom | Prénom | ORDRE | Sexe | Perf 50m
 *
 * perf50Centiemes est stocké en centièmes de seconde (ex : 660 = 6''60 = 6,60 s),
 * exactement comme dans le fichier Excel (format cellule "0''00").
 */
@Entity(tableName = "students")
data class Student(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val classe: String,
    val equipe: Int? = null,
    val nom: String,
    val prenom: String,
    val ordre: Int? = null,
    val sexe: String,           // "M" ou "F"
    val perf50Centiemes: Int    // ex : 660 -> 6.60 s
) {
    val perf50Secondes: Double get() = perf50Centiemes / 100.0

    val perf50Affichee: String get() {
        val sec = perf50Centiemes / 100
        val cent = perf50Centiemes % 100
        return "%d''%02d".format(sec, cent)
    }
}
