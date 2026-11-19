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

    // Cada respuesta es el programa completo. Mi agenda está en otra tabla,
    // así que las marcas hechas durante la descarga no se tocan.
    suspend fun sincronizar() {
        val fichas = api.consultar().map { it.aFicha() }
        db.withTransaction {
            dao.borrarTodo()
            dao.insertar(fichas)
            dao.quitarMarcasRetiradas()
            dao.guardarEdicion(Edicion(actualizada = System.currentTimeMillis()))
        }
    }

    suspend fun cambiarFavorita(id: String) {
        db.withTransaction { if (dao.desmarcar(id) == 0) dao.marcar(id) }
    }
}

internal fun FichaRemota.aFicha() = FichaGuardada(id, title, track, day, time, room, speaker, summary)
