package ar.jornadas.ui

import ar.jornadas.data.Charla

const val TODOS = "Todos"
val DIAS = listOf("Ambos días", "Día 1", "Día 2")
val TEMAS = listOf(TODOS, "Android", "Datos", "Calidad")

// dia 0 = ambos días
fun filtrar(charlas: List<Charla>, dia: Int, tema: String): List<Charla> =
    charlas.filter { (dia == 0 || it.dia == dia) && (tema == TODOS || it.tema == tema) }
