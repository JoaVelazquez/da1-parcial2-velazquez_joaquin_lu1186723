package ar.jornadas

import android.app.Application

// Una sola base, un solo cliente HTTP y una sola Mesa por proceso
class App : Application() {
    val servicios by lazy { Servicios(this) }
}
