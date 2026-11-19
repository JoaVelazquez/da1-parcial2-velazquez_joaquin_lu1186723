package ar.jornadas.data

import org.junit.Assert.*
import org.junit.Test

class AplicarMarcasTest {
    private fun ficha(id: String, titulo: String = "Charla $id", sala: String = "Auditorio") =
        FichaRemota(id, titulo, "Android", 1, "09:00", sala, "Paz Vega", "Resumen").aCharla()

    @Test fun v1aV2_la11ConservaLaMarcaConDatosNuevos() {
        val v2 = listOf(ficha("11", "Compose en producción avanzada", "Sala Puerto"), ficha("12"))
        val resultado = aplicarMarcas(v2, setOf("11")).associateBy { it.id }
        assertTrue(resultado.getValue("11").favorita)
        assertEquals("Compose en producción avanzada", resultado.getValue("11").titulo)
        assertEquals("Sala Puerto", resultado.getValue("11").sala)
        assertFalse(resultado.getValue("12").favorita)
    }

    @Test fun v3_retiraLa11YLa90EntraSinMarca() {
        val v3 = listOf(ficha("24"), ficha("90"))
        val resultado = aplicarMarcas(v3, setOf("11", "24")).associateBy { it.id }
        assertNull(resultado["11"])
        assertTrue(resultado.getValue("24").favorita)
        assertFalse(resultado.getValue("90").favorita)
    }

    @Test fun v4_programaVacioEsValido() {
        assertTrue(aplicarMarcas(emptyList(), setOf("24")).isEmpty())
    }

    @Test fun unaMarcaQuitadaDuranteLaPeticionSeRespeta() {
        // Las marcas se leen al llegar la respuesta: si ya no está, no vuelve
        assertFalse(aplicarMarcas(listOf(ficha("24")), emptySet()).single().favorita)
    }
}
