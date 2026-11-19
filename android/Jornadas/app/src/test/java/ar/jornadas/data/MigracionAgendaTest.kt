package ar.jornadas.data

import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.DriverManager

// Corre la migración y el SQL de Mi agenda contra un SQLite real, sin emulador
class MigracionAgendaTest {
    private val db: Connection = DriverManager.getConnection("jdbc:sqlite::memory:")

    @After fun cerrar() = db.close()

    private fun sql(vararg sentencias: String) =
        db.createStatement().use { st -> sentencias.forEach { st.execute(it) } }

    private fun ids(consulta: String): Set<String> = db.createStatement().use { st ->
        st.executeQuery(consulta).use { r -> buildSet { while (r.next()) add(r.getString(1)) } }
    }

    // Columnas tal como las compara Room al abrir la base
    private fun columnas(c: Connection, tabla: String): List<String> = c.createStatement().use { st ->
        st.executeQuery("PRAGMA table_info(`$tabla`)").use { r ->
            buildList { while (r.next()) add("${r.getString("name")} ${r.getString("type")} ${r.getInt("notnull")} ${r.getInt("pk")}") }
        }
    }

    private fun marcar(id: String) = sql(SQL_MARCAR.replace(":id", "'$id'"))

    private fun sincronizar(vararg ids: String) {
        sql("DELETE FROM charlas")
        ids.forEach { sql("INSERT INTO charlas VALUES ('$it', 't', 'Datos', 1, '09:00', 's', 'p', 'r')") }
        sql(SQL_QUITAR_RETIRADAS)
    }

    // La migración real, ejecutada sobre esta conexión
    private fun migrar2a3() {
        val adaptador = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SupportSQLiteDatabase::class.java)) { _, metodo, args ->
            check(metodo.name == "execSQL" && args?.size == 1) { "No soportado: ${metodo.name}" }
            sql(args[0] as String)
            null
        } as SupportSQLiteDatabase
        MIGRACION_2_3.migrate(adaptador)
    }

    private fun baseV2ConDatos() = sql(
        // Esquema v2 generado por Room
        "CREATE TABLE `charlas` (`id` TEXT NOT NULL, `titulo` TEXT NOT NULL, `tema` TEXT NOT NULL, `dia` INTEGER NOT NULL, `hora` TEXT NOT NULL, `sala` TEXT NOT NULL, `ponente` TEXT NOT NULL, `resumen` TEXT NOT NULL, `favorita` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        "CREATE TABLE `edicion` (`id` INTEGER NOT NULL, `actualizada` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        "INSERT INTO charlas VALUES ('11', 'Compose', 'Android', 1, '09:00', 'Auditorio', 'Paz', 'r', 1)",
        "INSERT INTO charlas VALUES ('12', 'Navegación', 'Android', 1, '14:30', 'Auditorio', 'Sol', 'r', 0)",
        "INSERT INTO charlas VALUES ('24', 'Consultas', 'Datos', 2, '14:30', 'Taller', 'Paz', 'r', 1)",
        "INSERT INTO edicion VALUES (1, 0)"
    )

    @Test fun laMigracionConservaFavoritasYProgramaConElEsquemaDeRoom() {
        baseV2ConDatos()
        migrar2a3()

        assertEquals(setOf("11", "24"), ids("SELECT charlaId FROM agenda"))
        assertEquals(setOf("11", "12", "24"), ids("SELECT id FROM charlas"))
        assertEquals(setOf("1"), ids("SELECT id FROM edicion"))

        // Mismo esquema que crea Room v3 en una instalación nueva
        DriverManager.getConnection("jdbc:sqlite::memory:").use { nueva ->
            nueva.createStatement().use {
                it.execute("CREATE TABLE `charlas` (`id` TEXT NOT NULL, `titulo` TEXT NOT NULL, `tema` TEXT NOT NULL, `dia` INTEGER NOT NULL, `hora` TEXT NOT NULL, `sala` TEXT NOT NULL, `ponente` TEXT NOT NULL, `resumen` TEXT NOT NULL, PRIMARY KEY(`id`))")
                it.execute("CREATE TABLE `agenda` (`charlaId` TEXT NOT NULL, PRIMARY KEY(`charlaId`))")
            }
            assertEquals(columnas(nueva, "charlas"), columnas(db, "charlas"))
            assertEquals(columnas(nueva, "agenda"), columnas(db, "agenda"))
        }
    }

    @Test fun v3RetiraLa11YLa90EntraSinMarca() {
        baseV2ConDatos()
        migrar2a3()
        sincronizar("12", "24", "90")
        assertEquals(setOf("24"), ids("SELECT charlaId FROM agenda"))
    }

    @Test fun unaCharlaRetiradaVuelveSinMarcar() {
        baseV2ConDatos()
        migrar2a3()
        sincronizar("12", "24")
        sincronizar("11", "12", "24")
        assertEquals(setOf("24"), ids("SELECT charlaId FROM agenda"))
    }

    @Test fun soloSeMarcanCharlasDelPrograma() {
        baseV2ConDatos()
        migrar2a3()
        marcar("12")
        marcar("99")
        assertEquals(setOf("11", "12", "24"), ids("SELECT charlaId FROM agenda"))
    }
}
