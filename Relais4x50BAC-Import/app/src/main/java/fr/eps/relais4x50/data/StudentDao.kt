package fr.eps.relais4x50.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface StudentDao {

    @Query("SELECT * FROM students ORDER BY classe, nom, prenom")
    fun getAll(): Flow<List<Student>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(student: Student): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(students: List<Student>)

    @Update
    suspend fun update(student: Student)

    @Delete
    suspend fun delete(student: Student)

    @Query("DELETE FROM students")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM students")
    suspend fun count(): Int
}
