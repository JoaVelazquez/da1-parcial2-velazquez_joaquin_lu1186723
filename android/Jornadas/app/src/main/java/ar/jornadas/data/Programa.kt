package ar.jornadas.data

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import retrofit2.http.GET
import kotlinx.coroutines.flow.Flow

data class FichaRemota(val id: String, val title: String, val track: String, val day: Int,
    val time: String, val room: String, val speaker: String, val summary: String)

interface ProgramaApi {
    @GET("programa") suspend fun consultar(): List<FichaRemota>
}

// Solo datos del servidor: se reemplaza entera en cada sincronización
@Entity(tableName = "charlas")
data class FichaGuardada(@PrimaryKey val id: String, val titulo: String, val tema: String, val dia: Int,
    val hora: String, val sala: String, val ponente: String, val resumen: String)

// Solo datos del usuario: las charlas que marcó para Mi agenda
@Entity(tableName = "agenda")
data class Marca(@PrimaryKey val charlaId: String)

// Lo que muestra la UI: la ficha y si está en Mi agenda
data class Charla(val id: String, val titulo: String, val tema: String, val dia: Int,
    val hora: String, val sala: String, val ponente: String, val resumen: String,
    val favorita: Boolean = false)

// Existe si ya se descargó alguna edición, aunque haya venido vacía
@Entity(tableName = "edicion")
data class Edicion(@PrimaryKey val id: Int = 1, val actualizada: Long)

// Constantes para usar el mismo SQL en el DAO y en los tests
internal const val SQL_QUITAR_RETIRADAS = "DELETE FROM agenda WHERE charlaId NOT IN (SELECT id FROM charlas)"
internal const val SQL_MARCAR = "INSERT OR IGNORE INTO agenda (charlaId) SELECT id FROM charlas WHERE id = :id"

@Dao
interface CarteleraDao {
    @Query("""SELECT c.*, a.charlaId IS NOT NULL AS favorita
              FROM charlas c LEFT JOIN agenda a ON a.charlaId = c.id
              ORDER BY c.dia, c.hora, c.sala""")
    fun observar(): Flow<List<Charla>>

    @Query("SELECT COUNT(*) FROM edicion")
    fun observarEdicion(): Flow<Int>

    @Query("DELETE FROM charlas")
    suspend fun borrarTodo()

    @Insert
    suspend fun insertar(fichas: List<FichaGuardada>)

    // Si una charla se retira, pierde la marca; si vuelve, vuelve sin marcar
    @Query(SQL_QUITAR_RETIRADAS)
    suspend fun quitarMarcasRetiradas()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardarEdicion(edicion: Edicion)

    // Devuelve cuántas filas borró: 0 si no estaba marcada
    @Query("DELETE FROM agenda WHERE charlaId = :id")
    suspend fun desmarcar(id: String): Int

    // Solo marca charlas que existen en el programa
    @Query(SQL_MARCAR)
    suspend fun marcar(id: String)
}

@Database(entities = [FichaGuardada::class, Marca::class, Edicion::class], version = 3, exportSchema = false)
abstract class Archivo : RoomDatabase() {
    abstract fun cartelera(): CarteleraDao
}

// v1 -> v2: suma la tabla edicion sin tocar charlas ni favoritas
val MIGRACION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `edicion` (`id` INTEGER NOT NULL, " +
            "`actualizada` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        // Si ya había programa guardado, cuenta como edición descargada
        db.execSQL("INSERT INTO edicion (id, actualizada) SELECT 1, 0 WHERE EXISTS (SELECT 1 FROM charlas)")
    }
}

// v2 -> v3: las marcas pasan a la tabla agenda y charlas pierde la columna favorita
val MIGRACION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `agenda` (`charlaId` TEXT NOT NULL, PRIMARY KEY(`charlaId`))")
        db.execSQL("INSERT INTO agenda (charlaId) SELECT id FROM charlas WHERE favorita = 1")
        // SQLite de API 26 no tiene DROP COLUMN: se copia a una tabla nueva
        db.execSQL("CREATE TABLE IF NOT EXISTS `charlas_nueva` (`id` TEXT NOT NULL, `titulo` TEXT NOT NULL, " +
            "`tema` TEXT NOT NULL, `dia` INTEGER NOT NULL, `hora` TEXT NOT NULL, `sala` TEXT NOT NULL, " +
            "`ponente` TEXT NOT NULL, `resumen` TEXT NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("INSERT INTO charlas_nueva (id, titulo, tema, dia, hora, sala, ponente, resumen) " +
            "SELECT id, titulo, tema, dia, hora, sala, ponente, resumen FROM charlas")
        db.execSQL("DROP TABLE charlas")
        db.execSQL("ALTER TABLE charlas_nueva RENAME TO charlas")
    }
}
