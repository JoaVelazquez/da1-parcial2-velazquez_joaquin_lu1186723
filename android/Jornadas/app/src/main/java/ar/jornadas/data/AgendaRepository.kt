package ar.jornadas.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.util.concurrent.atomic.AtomicBoolean

// Lo que hay guardado: las charlas y si alguna vez se descargó una edición
data class Cartelera(val charlas: List<Charla>, val descargada: Boolean)

class AgendaRepository(private val api: ProgramaApi, private val db: Archivo) {
    private val dao = db.cartelera()
    private val cargaAutomaticaHecha = AtomicBoolean(false)

    val cartelera: Flow<Cartelera> =
        combine(dao.observar(), dao.observarEdicion()) { charlas, ediciones -> Cartelera(charlas, ediciones > 0) }

    // true solo la primera vez en el proceso
    fun reclamarCargaAutomatica(): Boolean = !cargaAutomaticaHecha.getAndSet(true)

    // Cada respuesta es el programa completo: reemplaza todo de una vez
    suspend fun sincronizar() {
        val fichas = api.consultar().map { it.aCharla() }
        db.withTransaction {
            // Las marcas se leen recién ahora para respetar las hechas durante la descarga
            val marcadas = dao.idsFavoritas().toSet()
            dao.borrarTodo()
            dao.insertar(aplicarMarcas(fichas, marcadas))
            dao.guardarEdicion(Edicion(actualizada = System.currentTimeMillis()))
        }
    }

    suspend fun cambiarFavorita(id: String) = dao.alternar(id)
}

internal fun FichaRemota.aCharla() = Charla(id, title, track, day, time, room, speaker, summary)

// Solo conservan la marca las charlas que siguen en el programa
internal fun aplicarMarcas(fichas: List<Charla>, marcadas: Set<String>): List<Charla> =
    fichas.map { it.copy(favorita = it.id in marcadas) }
