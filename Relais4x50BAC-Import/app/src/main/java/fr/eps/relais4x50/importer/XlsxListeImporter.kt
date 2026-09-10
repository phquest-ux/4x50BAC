package fr.eps.relais4x50.importer

import android.content.Context
import android.net.Uri
import android.util.Xml
import fr.eps.relais4x50.data.Student
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Lit un fichier .xlsx (type Relais-BAC_4X50.xlsx) sans dépendance externe (pas de POI),
 * en dézippant l'archive OOXML et en parsant directement le XML des feuilles.
 *
 * Cherche une feuille nommée "Liste" (insensible à la casse), sinon prend la 1ère feuille.
 * Repère la ligne d'entêtes contenant "Nom" et "Prénom" puis lit les lignes suivantes en se
 * basant sur les libellés de colonnes : Classe, Eq, Nom, Prénom, ORDRE, Sexe, Perf 50m.
 */
object XlsxListeImporter {

    data class ImportResult(
        val students: List<Student>,
        val warnings: List<String>
    )

    private val HEADER_ALIASES = mapOf(
        "classe" to "classe",
        "eq" to "equipe",
        "équipe" to "equipe",
        "nom" to "nom",
        "prénom" to "prenom",
        "prenom" to "prenom",
        "ordre" to "ordre",
        "sexe" to "sexe",
        "perf 50m" to "perf50",
        "perf50m" to "perf50",
        "perf 50 m" to "perf50"
    )

    fun import(context: Context, uri: Uri): ImportResult {
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Impossible d'ouvrir le fichier" }
            return parseZip(input)
        }
    }

    private fun parseZip(input: InputStream): ImportResult {
        var sharedStrings: List<String> = emptyList()
        var workbookXml: String? = null
        var relsXml: String? = null
        val sheetXmlByPath = mutableMapOf<String, String>()

        ZipInputStream(input).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val name = entry.name
                when {
                    name == "xl/sharedStrings.xml" -> sharedStrings = parseSharedStrings(zip)
                    name == "xl/workbook.xml" -> workbookXml = zip.readBytes().toString(Charsets.UTF_8)
                    name == "xl/_rels/workbook.xml.rels" -> relsXml = zip.readBytes().toString(Charsets.UTF_8)
                    name.startsWith("xl/worksheets/") && name.endsWith(".xml") ->
                        sheetXmlByPath[name] = zip.readBytes().toString(Charsets.UTF_8)
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        val sheetPath = resolveSheetPath(workbookXml, relsXml, sheetXmlByPath.keys, "Liste")
        val sheetContent = sheetXmlByPath[sheetPath]
            ?: sheetXmlByPath.values.firstOrNull()
            ?: throw IllegalStateException("Aucune feuille trouvée dans le fichier")

        return parseSheet(sheetContent, sharedStrings)
    }

    // ---- workbook.xml / rels : retrouver le fichier XML correspondant au nom d'onglet voulu ----

    private fun resolveSheetPath(
        workbookXml: String?,
        relsXml: String?,
        availableSheetPaths: Set<String>,
        wantedSheetName: String
    ): String? {
        if (workbookXml == null || relsXml == null) return null

        // sheet name -> r:id
        var rId: String? = null
        val sheetParser = Xml.newPullParser()
        sheetParser.setInput(workbookXml.reader())
        var eventType = sheetParser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && sheetParser.name == "sheet") {
                val name = sheetParser.getAttributeValue(null, "name")
                if (name?.equals(wantedSheetName, ignoreCase = true) == true) {
                    rId = sheetParser.getAttributeValue(
                        "http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id"
                    )
                }
            }
            eventType = sheetParser.next()
        }
        if (rId == null) return null

        // r:id -> target path
        var target: String? = null
        val relParser = Xml.newPullParser()
        relParser.setInput(relsXml.reader())
        eventType = relParser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && relParser.name == "Relationship") {
                val id = relParser.getAttributeValue(null, "Id")
                if (id == rId) {
                    target = relParser.getAttributeValue(null, "Target")
                }
            }
            eventType = relParser.next()
        }
        val path = target?.let { "xl/" + it.removePrefix("/xl/").removePrefix("xl/") }
        return availableSheetPaths.firstOrNull { it == path } ?: path
    }

    private fun parseSharedStrings(input: InputStream): List<String> {
        val list = mutableListOf<String>()
        val parser = Xml.newPullParser()
        parser.setInput(input, "UTF-8")
        var eventType = parser.eventType
        val sb = StringBuilder()
        var inSi = false
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> if (parser.name == "si") { inSi = true; sb.clear() }
                XmlPullParser.TEXT -> if (inSi) sb.append(parser.text)
                XmlPullParser.END_TAG -> if (parser.name == "si") { list.add(sb.toString()); inSi = false }
            }
            eventType = parser.next()
        }
        return list
    }

    // ---- lecture de la feuille elle-même ----

    private fun parseSheet(sheetXml: String, sharedStrings: List<String>): ImportResult {
        val parser = Xml.newPullParser()
        parser.setInput(sheetXml.reader())

        data class Cell(val col: Int, val value: String)
        val rows = mutableListOf<List<Cell>>()
        var currentRow: MutableList<Cell>? = null
        var currentRef: String? = null
        var currentType: String? = null
        val textBuf = StringBuilder()
        var inValue = false

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "row" -> currentRow = mutableListOf()
                    "c" -> {
                        currentRef = parser.getAttributeValue(null, "r")
                        currentType = parser.getAttributeValue(null, "t")
                    }
                    "v", "t" -> { inValue = true; textBuf.clear() }
                }
                XmlPullParser.TEXT -> if (inValue) textBuf.append(parser.text)
                XmlPullParser.END_TAG -> when (parser.name) {
                    "v", "t" -> inValue = false
                    "c" -> {
                        val ref = currentRef
                        if (ref != null && currentRow != null) {
                            val colIndex = columnLetterToIndex(ref.takeWhile { it.isLetter() })
                            val raw = textBuf.toString()
                            val value = if (currentType == "s") {
                                raw.toIntOrNull()?.let { sharedStrings.getOrNull(it) } ?: ""
                            } else raw
                            currentRow!!.add(Cell(colIndex, value))
                        }
                        textBuf.clear()
                    }
                    "row" -> { currentRow?.let { rows.add(it) }; currentRow = null }
                }
            }
            eventType = parser.next()
        }

        // 1) trouver la ligne d'entêtes (contient à la fois "nom" et "prénom")
        var headerRowIdx = -1
        var colMap: Map<String, Int> = emptyMap()
        for ((idx, row) in rows.withIndex()) {
            val map = mutableMapOf<String, Int>()
            for (cell in row) {
                val key = HEADER_ALIASES[cell.value.trim().lowercase()]
                if (key != null) map[key] = cell.col
            }
            if (map.containsKey("nom") && map.containsKey("prenom")) {
                headerRowIdx = idx
                colMap = map
                break
            }
        }

        val warnings = mutableListOf<String>()
        if (headerRowIdx == -1) {
            warnings.add("Entêtes non trouvées (Nom / Prénom) : import impossible.")
            return ImportResult(emptyList(), warnings)
        }

        val students = mutableListOf<Student>()
        for (row in rows.drop(headerRowIdx + 1)) {
            fun cellAt(key: String): String? =
                colMap[key]?.let { col -> row.firstOrNull { it.col == col }?.value?.trim() }

            val nom = cellAt("nom").orEmpty()
            val prenom = cellAt("prenom").orEmpty()
            if (nom.isBlank() && prenom.isBlank()) continue // fin de la liste

            val classe = cellAt("classe").orEmpty()
            val sexe = cellAt("sexe").orEmpty().uppercase().ifBlank { "M" }
            val equipe = cellAt("equipe")?.toIntOrNull()
            val ordre = cellAt("ordre")?.toIntOrNull()
            val perfRaw = cellAt("perf50")

            val perfCentiemes = parsePerf50(perfRaw)
            if (perfCentiemes == null) {
                warnings.add("Ligne ignorée (perf 50m illisible) : $nom $prenom -> '$perfRaw'")
                continue
            }

            students.add(
                Student(
                    classe = classe,
                    equipe = equipe,
                    nom = nom,
                    prenom = prenom,
                    ordre = ordre,
                    sexe = if (sexe.startsWith("F")) "F" else "M",
                    perf50Centiemes = perfCentiemes
                )
            )
        }

        return ImportResult(students, warnings)
    }

    /**
     * Accepte soit un nombre brut en centièmes (ex : "660" -> 6.60 s, format Excel "0''00"),
     * soit une saisie décimale classique (ex : "6.60" ou "6,60" -> 6.60 s).
     */
    private fun parsePerf50(raw: String?): Int? {
        if (raw.isNullOrBlank()) return null
        val cleaned = raw.trim().replace(",", ".")
        val asDouble = cleaned.toDoubleOrNull() ?: return null
        return if (asDouble == asDouble.toLong().toDouble() && asDouble >= 100) {
            // valeur entière type 660 déjà en centièmes (format Excel 0''00)
            asDouble.toInt()
        } else {
            // valeur décimale type 6.60 -> secondes
            Math.round(asDouble * 100).toInt()
        }
    }

    private fun columnLetterToIndex(letters: String): Int {
        var result = 0
        for (c in letters) result = result * 26 + (c.uppercaseChar() - 'A' + 1)
        return result - 1
    }
}
