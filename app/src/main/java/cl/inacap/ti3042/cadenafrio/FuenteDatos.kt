package cl.inacap.ti3042.cadenafrio

/** Función que recibe cada nuevo estado emitido por una fuente de datos. */
typealias ObservadorEstado = (EstadoSistema) -> Unit

/**
 * Contrato común para cualquier origen de datos del contenedor.
 * Hoy lo implementa SimuladorDatos; en la etapa siguiente lo implementará
 * BluetoothDatos (HC-06). Las pantallas nunca usan esta interfaz directamente:
 * siempre pasan por RepositorioEstado.
 *
 * Las implementaciones deben notificar a los observadores en el hilo principal.
 */
interface FuenteDatos {
    /** Comienza a producir lecturas (abrir conexión, arrancar temporizador, etc.). */
    fun iniciar()

    /** Deja de producir lecturas y libera recursos. */
    fun detener()

    /** Agrega un observador; recibe de inmediato el último estado conocido. */
    fun registrarObservador(observador: ObservadorEstado)

    /** Quita un observador previamente registrado. */
    fun quitarObservador(observador: ObservadorEstado)

    /** Envía un comando del protocolo (ST, LOCK, UNLOCK, SIL, DEMO ON/OFF, SET ...). */
    fun enviarComando(comando: String)

    /** true si la fuente está entregando datos. */
    fun estaConectada(): Boolean

    /** Nombre legible de la fuente, para mostrar en pantalla. */
    fun nombreFuente(): String
}
