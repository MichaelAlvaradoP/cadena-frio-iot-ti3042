package cl.inacap.ti3042.cadenafrio

import android.app.Application

/**
 * Clase Application: se crea una sola vez al abrir la app, antes que cualquier
 * Activity. Entrega el contexto de la aplicación al repositorio para que pueda
 * abrir la base de datos SQLite.
 */
class CadenaFrioApp : Application() {

    override fun onCreate() {
        super.onCreate()
        RepositorioEstado.inicializar(this)
    }
}
