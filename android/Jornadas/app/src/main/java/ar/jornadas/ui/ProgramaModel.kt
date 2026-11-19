package ar.jornadas.ui

import android.app.Application
import androidx.lifecycle.*
import ar.jornadas.App
import ar.jornadas.data.Cartelera
import ar.jornadas.data.Charla
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ProgramaModel(app: Application, private val estado: SavedStateHandle) : AndroidViewModel(app) {
    private val servicios = (app as App).servicios
    val mesa = servicios.mesa
    private val repo = servicios.agenda

    // null mientras la base todavía no respondió
    val cartelera: StateFlow<Cartelera?> = repo.cartelera.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // En SavedStateHandle: sobreviven a rotar y a que Android mate el proceso
    val dia = estado.getStateFlow("dia", 0)
    val tema = estado.getStateFlow("tema", TODOS)
    fun elegirDia(valor: Int) { estado["dia"] = valor }
    fun elegirTema(valor: String) { estado["tema"] = valor }

    // Programa con los filtros aplicados
    val visibles: StateFlow<List<Charla>> =
        combine(cartelera, dia, tema) { guardado, d, t -> filtrar(guardado?.charlas.orEmpty(), d, t) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _fallo = MutableStateFlow(false)
    val fallo = _fallo.asStateFlow()
    private val _cargando = MutableStateFlow(false)
    val cargando = _cargando.asStateFlow()

    private var descarga: Job? = null

    // Una sola carga automática por proceso: rotar o volver de Inicio no suma
    fun cargaAutomatica() {
        if (repo.reclamarCargaAutomatica()) recargar()
    }

    fun recargar() {
        if (descarga?.isActive == true) return // ya hay una petición en curso
        descarga = viewModelScope.launch {
            _cargando.value = true
            try {
                repo.sincronizar()
                _fallo.value = false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Sin red, error HTTP, JSON roto o error de base: se sigue con lo guardado
                _fallo.value = true
            } finally {
                _cargando.value = false
            }
        }
    }

    fun marcar(id: String) { viewModelScope.launch { repo.cambiarFavorita(id) } }
}
