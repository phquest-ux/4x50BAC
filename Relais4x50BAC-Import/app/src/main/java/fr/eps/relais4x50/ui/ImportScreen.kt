package fr.eps.relais4x50.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.eps.relais4x50.data.Student

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(viewModel: StudentViewModel = viewModel()) {
    val students by viewModel.students.collectAsState()
    val preview by viewModel.preview.collectAsState()
    val warnings by viewModel.warnings.collectAsState()
    val status by viewModel.status.collectAsState()

    var showManualAdd by remember { mutableStateOf(false) }
    var replaceExisting by remember { mutableStateOf(true) }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.pickFile(it) } }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Liste de classe — Relais 4x50", style = MaterialTheme.typography.titleLarge)
        Text(
            "Importe l'onglet « Liste » du fichier Excel (Classe, Nom, Prénom, Sexe, Perf 50m) " +
                "ou un CSV équivalent.",
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = {
                filePicker.launch(
                    arrayOf(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        "text/csv",
                        "text/comma-separated-values",
                        "*/*"
                    )
                )
            }) { Text("Importer un fichier (.xlsx / .csv)") }

            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = { showManualAdd = true }) { Text("Ajouter à la main") }
        }

        status?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium)
        }

        if (preview.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("Aperçu avant import (${preview.size})", style = MaterialTheme.typography.titleMedium)

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = replaceExisting, onCheckedChange = { replaceExisting = it })
                Text("Remplacer la liste actuelle")
            }

            LazyColumn(Modifier.weight(1f, fill = false).heightIn(max = 260.dp)) {
                items(preview) { s ->
                    ListItem(
                        headlineContent = { Text("${s.nom} ${s.prenom}") },
                        supportingContent = { Text("${s.classe} · ${s.sexe} · ${s.perf50Affichee}") }
                    )
                }
            }

            if (warnings.isNotEmpty()) {
                Text(
                    "${warnings.size} ligne(s) ignorée(s), voir détails dans les logs.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Row {
                Button(onClick = { viewModel.confirmImport(replaceExisting) }) {
                    Text("Confirmer l'import")
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Divider()
        Spacer(Modifier.height(8.dp))
        Text("Élèves enregistrés (${students.size})", style = MaterialTheme.typography.titleMedium)

        LazyColumn(Modifier.weight(1f)) {
            items(students, key = { it.id }) { s ->
                ListItem(
                    headlineContent = { Text("${s.nom} ${s.prenom}") },
                    supportingContent = { Text("${s.classe} · ${s.sexe} · ${s.perf50Affichee}") },
                    trailingContent = {
                        IconButton(onClick = { viewModel.delete(s) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Supprimer")
                        }
                    }
                )
            }
        }
    }

    if (showManualAdd) {
        ManualAddDialog(
            onDismiss = { showManualAdd = false },
            onAdd = { viewModel.addManual(it); showManualAdd = false }
        )
    }
}

@Composable
private fun ManualAddDialog(onDismiss: () -> Unit, onAdd: (Student) -> Unit) {
    var nom by remember { mutableStateOf("") }
    var prenom by remember { mutableStateOf("") }
    var classe by remember { mutableStateOf("") }
    var sexe by remember { mutableStateOf("M") }
    var perf by remember { mutableStateOf("") } // en secondes, ex 6,60

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ajouter un élève") },
        text = {
            Column {
                OutlinedTextField(nom, { nom = it }, label = { Text("Nom") })
                OutlinedTextField(prenom, { prenom = it }, label = { Text("Prénom") })
                OutlinedTextField(classe, { classe = it }, label = { Text("Classe") })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Sexe : ")
                    FilterChip(selected = sexe == "M", onClick = { sexe = "M" }, label = { Text("M") })
                    Spacer(Modifier.width(4.dp))
                    FilterChip(selected = sexe == "F", onClick = { sexe = "F" }, label = { Text("F") })
                }
                OutlinedTextField(perf, { perf = it }, label = { Text("Perf 50m (secondes, ex 6,60)") })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val secs = perf.replace(",", ".").toDoubleOrNull() ?: return@TextButton
                onAdd(
                    Student(
                        classe = classe, nom = nom, prenom = prenom,
                        sexe = sexe, perf50Centiemes = Math.round(secs * 100).toInt()
                    )
                )
            }) { Text("Ajouter") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}
