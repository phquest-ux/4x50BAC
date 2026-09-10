package fr.eps.relais4x50.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.eps.relais4x50.data.AppDatabase
import fr.eps.relais4x50.data.Student
import fr.eps.relais4x50.importer.CsvListeImporter
import fr.eps.relais4x50.importer.XlsxListeImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class StudentViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = AppDatabase.getInstance(application).studentDao()

    val students: StateFlow<List<Student>> =
        dao.getAll().stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, emptyList())

    private val _preview = MutableStateFlow<List<Student>>(emptyList())
    val preview: StateFlow<List<Student>> = _preview

    private val _warnings = MutableStateFlow<List<String>>(emptyList())
    val warnings: StateFlow<List<String>> = _warnings

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status

    fun pickFile(uri: Uri) {
        viewModelScope.launch {
            _status.value = "Lecture du fichier..."
            val result = withContext(Dispatchers.IO) {
                val name = getFileName(uri)
                if (name?.endsWith(".csv", ignoreCase = true) == true) {
                    CsvListeImporter.import(getApplication(), uri)
                } else {
                    XlsxListeImporter.import(getApplication(), uri)
                }
            }
            _preview.value = result.students
            _warnings.value = result.warnings
            _status.value = "${result.students.size} élève(s) prêt(s) à importer" +
                if (result.warnings.isNotEmpty()) " (${result.warnings.size} ligne(s) ignorée(s))" else ""
        }
    }

    fun confirmImport(replaceExisting: Boolean) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                if (replaceExisting) dao.deleteAll()
                dao.insertAll(_preview.value)
            }
            _status.value = "${_preview.value.size} élève(s) importé(s)."
            _preview.value = emptyList()
            _warnings.value = emptyList()
        }
    }

    fun addManual(student: Student) {
        viewModelScope.launch(Dispatchers.IO) { dao.insert(student) }
    }

    fun delete(student: Student) {
        viewModelScope.launch(Dispatchers.IO) { dao.delete(student) }
    }

    fun clearAll() {
        viewModelScope.launch(Dispatchers.IO) { dao.deleteAll() }
    }

    private fun getFileName(uri: Uri): String? {
        var name: String? = null
        getApplication<Application>().contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) name = cursor.getString(idx)
        }
        return name
    }
}
