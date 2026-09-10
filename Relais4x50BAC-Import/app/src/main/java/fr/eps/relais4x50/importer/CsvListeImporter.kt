package fr.eps.relais4x50.importer

import android.content.Context
import android.net.Uri
import fr.eps.relais4x50.data.Student

/**
 * Import CSV de secours (séparateur ; ou ,) avec en-têtes :
 * classe;nom;prenom;sexe;perf50
 * perf50 accepte "6,60" / "6.60" (secondes) ou "660" (centièmes, format Excel).
 */
object CsvListeImporter {

    fun import(context: Context, uri: Uri): XlsxListeImporter.ImportResult {
        val lines = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readLines()
            ?: return XlsxListeImporter.ImportResult(emptyList(), listOf("Fichier illisible"))
        if (lines.isEmpty()) return XlsxListeImporter.ImportResult(emptyList(), listOf("Fichier vide"))

        val sep = if (lines[0].contains(";")) ";" else ","
        val header = lines[0].split(sep).map { it.trim().lowercase() }
        val idx = { key: String -> header.indexOfFirst { it == key || it.startsWith(key) } }
        val iClasse = idx("classe")
        val iNom = idx("nom")
        val iPrenom = idx("prenom")
        val iSexe = idx("sexe")
        val iPerf = idx("perf")

        val warnings = mutableListOf<String>()
        val students = mutableListOf<Student>()
        for (line in lines.drop(1)) {
            if (line.isBlank()) continue
            val cols = line.split(sep).map { it.trim() }
            val nom = cols.getOrNull(iNom).orEmpty()
            val prenom = cols.getOrNull(iPrenom).orEmpty()
            if (nom.isBlank() && prenom.isBlank()) continue
            val perfRaw = cols.getOrNull(iPerf)
            val perf = perfRaw?.replace(",", ".")?.toDoubleOrNull()
            if (perf == null) {
                warnings.add("Ligne ignorée (perf illisible) : $line")
                continue
            }
            val perfCentiemes = if (perf >= 100 && perf == perf.toLong().toDouble())
                perf.toInt() else Math.round(perf * 100).toInt()

            students.add(
                Student(
                    classe = cols.getOrNull(iClasse).orEmpty(),
                    nom = nom,
                    prenom = prenom,
                    sexe = cols.getOrNull(iSexe)?.uppercase()?.ifBlank { "M" }?.let {
                        if (it.startsWith("F")) "F" else "M"
                    } ?: "M",
                    perf50Centiemes = perfCentiemes
                )
            )
        }
        return XlsxListeImporter.ImportResult(students, warnings)
    }
}
