package ar.jornadas.ui

import ar.jornadas.data.Charla
import org.junit.Assert.assertEquals
import org.junit.Test

class FiltrosTest {
    private fun charla(id: String, tema: String, dia: Int) =
        Charla(id, "Charla $id", tema, dia, "09:00", "Aula", "Ponente", "Resumen")

    private val programa = listOf(
        charla("13", "Android", 2), charla("21", "Datos", 1),
        charla("23", "Datos", 2), charla("24", "Datos", 2), charla("33", "Calidad", 2)
    )

    @Test fun dia2YDatosDejaDosResultados() {
        assertEquals(listOf("23", "24"), filtrar(programa, 2, "Datos").map { it.id })
    }

    @Test fun sinFiltrosMuestraTodo() {
        assertEquals(programa, filtrar(programa, 0, TODOS))
    }

    @Test fun soloDia() {
        assertEquals(listOf("13", "23", "24", "33"), filtrar(programa, 2, TODOS).map { it.id })
    }
}
